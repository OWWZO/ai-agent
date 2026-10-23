package org.wwz.ai.domain.agent.runtime.agent;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.executor.AgentExecutorSupport;
import org.wwz.ai.domain.agent.runtime.planmode.PlanModeToolPolicy;
import org.wwz.ai.domain.agent.runtime.tool.ToolObservationBudget;
import org.wwz.ai.domain.agent.runtime.tool.ToolObservationFormatter;
import org.wwz.ai.domain.agent.runtime.tool.ToolObservationSerializer;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.canvas.CanvasPublishArgSalvage;
import org.wwz.ai.domain.agent.runtime.tool.canvas.EmitUiTreeArgSalvage;
import org.wwz.ai.domain.agent.runtime.askuser.UserInputRequiredException;
import org.wwz.ai.domain.agent.runtime.desktopcontrol.DesktopControlRequiredException;
import org.wwz.ai.domain.agent.runtime.planmode.PlanApprovalRequiredException;
import org.wwz.ai.domain.agent.runtime.tool.common.mcp.McpToolNames;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.AskUserQuestionTool;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.RequestDesktopControlTool;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.TaskToolNames;
import org.wwz.ai.domain.agent.runtime.tool.deferred.DeferredToolCatalog;
import org.wwz.ai.domain.agent.runtime.tool.deferred.DeferredToolCall;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 工具执行管线：预登记 → 执行 → observation 收口 → 账本/产物/SSE 终态。
 * <p>
 * 协作方：{@link ToolLedgerRecorder} 写账本，{@link ToolBatchRunner} 管并发与取消，
 * {@link ToolCallEventEmitter} 装配 SSE 协议，{@link ToolTimingTracker} 记墙钟。
 * 本类只保留编排顺序与单工具执行语义。
 */
@Slf4j
final class ToolExecutionPipeline {

    private final BaseAgent agent;
    private final ToolTimingTracker timings = new ToolTimingTracker();
    private final ToolLedgerRecorder ledger;
    private final ToolBatchRunner batchRunner;
    private final ToolCallEventEmitter events;

    ToolExecutionPipeline(BaseAgent agent) {
        this.agent = agent;
        this.ledger = new ToolLedgerRecorder(agent, timings);
        this.batchRunner = new ToolBatchRunner(agent);
        this.events = new ToolCallEventEmitter(agent, timings, ledger);
    }

    ToolExecutionOutcome executeOne(ToolCall command) {
        List<ToolCall> commands = command == null ? List.of() : List.of(command);
        timings.start(commands);
        Map<String, ToolDispatch> dispatches = resolveAll(commands);
        Map<String, Long> toolInvocationIds = ensureToolInvocationIds(dispatches, commands);
        AgentContext context = agent.getContext();
        if (context != null && context.getAgentRunState() != null && !toolInvocationIds.isEmpty()) {
            context.getAgentRunState().bindToolInvocationIds(toolInvocationIds);
        }
        Map<String, Integer> dispatchIndexMapping = buildDispatchIndexMapping(commands);
        events.emitRunning(commands, dispatches, dispatchIndexMapping);
        ToolDispatch dispatch = dispatches.get(command == null ? null : command.getId());
        ToolExecutionOutcome outcome = finalizeOutcome(command, executeInternal(dispatch, command));
        settle(command, dispatch, dispatchIndexMapping.get(command == null ? null : command.getId()), outcome);
        return outcome;
    }

    Map<String, ToolExecutionOutcome> executeBatch(List<ToolCall> commands) {
        Map<String, ToolExecutionOutcome> result = new ConcurrentHashMap<>();
        if (commands == null || commands.isEmpty()) {
            return result;
        }

        timings.start(commands);

        AgentContext context = agent.getContext();
        if (context != null && context.isRunCancelled()) {
            return finishCancelledBeforeStart(commands);
        }

        String soleYieldViolation = detectSoleYieldToolViolation(commands);
        if (soleYieldViolation != null) {
            return rejectMixedYieldBatch(commands, soleYieldViolation);
        }

        Map<String, Integer> dispatchIndexMapping = buildDispatchIndexMapping(commands);
        Map<String, ToolDispatch> dispatches = resolveAll(commands);
        Map<String, Long> toolInvocationIds = ensureToolInvocationIds(dispatches, commands);
        if (context != null && context.getAgentRunState() != null) {
            context.getAgentRunState().bindToolInvocationIds(toolInvocationIds);
        }
        events.emitRunning(commands, dispatches, dispatchIndexMapping);

        AtomicReference<RuntimeException> yieldSignal = new AtomicReference<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>(commands.size());
        List<CompletableFuture<?>> executionFutures = new ArrayList<>(commands.size());
        for (ToolCall toolCall : commands) {
            Executor executor = batchRunner.executorFor(toolCall);
            String scene = batchRunner.isAgentDispatchTool(toolCall) ? "subAgentBatch" : "toolBatch";
            CompletableFuture<ToolExecutionOutcome> executionFuture = AgentExecutorSupport
                    .supplyAsync(executor, scene, context,
                            () -> finalizeOutcome(toolCall, executeInternal(dispatches.get(toolCall.getId()), toolCall)));
            executionFutures.add(executionFuture);
            futures.add(executionFuture.handle((outcome, error) -> {
                if (ToolBatchRunner.isCancellation(error)) {
                    return null;
                }
                if (error != null) {
                    Throwable root = ToolBatchRunner.unwrap(error);
                    // 让出类工具的异常不是失败，而是本轮执行要中断的信号，交给上层收口。
                    if (root instanceof UserInputRequiredException
                            || root instanceof DesktopControlRequiredException
                            || root instanceof PlanApprovalRequiredException) {
                        yieldSignal.compareAndSet(null, (RuntimeException) root);
                        return null;
                    }
                    String msg = root.getMessage() == null
                            ? root.getClass().getSimpleName()
                            : root.getMessage();
                    completeToolOutcome(result, toolCall, dispatches,
                            toolFailureOutcome("Tool execution error: " + msg, msg),
                            dispatchIndexMapping, true);
                    return null;
                }
                completeToolOutcome(result, toolCall, dispatches, outcome, dispatchIndexMapping, true);
                return null;
            }));
        }

        batchRunner.await(futures, executionFutures, commands);
        if (yieldSignal.get() != null) {
            throw yieldSignal.get();
        }
        for (ToolCall command : commands) {
            if (command == null || StringUtils.isBlank(command.getId()) || result.containsKey(command.getId())) {
                continue;
            }
            boolean cancelled = context != null && context.isRunCancelled();
            completeToolOutcome(
                    result,
                    command,
                    dispatches,
                    cancelled
                            ? userStopOutcome("工具执行已取消：用户停止本轮对话")
                            : toolFailureOutcome("工具执行超时，已终止等待", "TOOL_BATCH_TIMEOUT"),
                    dispatchIndexMapping,
                    false);
        }
        return orderedOutcomes(commands, result);
    }

    Map<String, Object> parseToolParam(ToolCall command) {
        if (command == null || command.getFunction() == null) {
            return Map.of();
        }
        String toolName = command.getFunction().getName();
        try {
            Object parsed = parseToolArguments(toolName, command.getFunction().getArguments());
            if (parsed instanceof Map<?, ?> parsedMap) {
                Map<String, Object> map = new LinkedHashMap<>(parsedMap.size());
                for (Map.Entry<?, ?> entry : parsedMap.entrySet()) {
                    map.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                return map;
            }
        } catch (Exception e) {
            AgentContext context = agent.getContext();
            log.warn("{} invalid tool arguments, fallback empty map. tool={}, args={}",
                    context == null ? "-" : context.getRequestId(), toolName,
                    command.getFunction().getArguments());
        }
        return Map.of();
    }

    Map<String, Long> ensureToolInvocationIds(List<ToolCall> commands) {
        return ensureToolInvocationIds(resolveAll(commands), commands);
    }

    private Map<String, Long> ensureToolInvocationIds(Map<String, ToolDispatch> dispatches, List<ToolCall> commands) {
        return ledger.ensureIds(dispatches, commands);
    }

    Map<String, Long> preRegisterToolInvocations(List<ToolCall> commands) {
        return ledger.preRegister(commands, resolveAll(commands));
    }

    void awaitToolBatch(List<CompletableFuture<Void>> futures,
                        List<CompletableFuture<?>> executionFutures,
                        List<ToolCall> commands) {
        batchRunner.await(futures, executionFutures, commands);
    }

    private ToolExecutionOutcome executeInternal(ToolDispatch dispatch, ToolCall command) {
        AgentContext context = agent.getContext();
        if (command == null || command.getFunction() == null
                || StringUtils.isBlank(command.getFunction().getName())) {
            return ToolExecutionOutcome.failure(
                    "Error: Invalid function call format",
                    "Error: Invalid function call format",
                    null,
                    "Invalid function call format"
            );
        }
        if (dispatch != null && dispatch.earlyOutcome != null) {
            return dispatch.earlyOutcome;
        }

        String toolName = dispatch == null ? command.getFunction().getName() : dispatch.toolName;
        if (context != null && context.isRunCancelled()) {
            return userStopOutcome("工具未执行：用户已停止本轮对话");
        }
        try {
            Object args = dispatch != null && dispatch.argsReady
                    ? dispatch.args
                    : parseToolArguments(toolName, command.getFunction().getArguments());
            String planDeny = PlanModeToolPolicy.denyReason(context, toolName, args);
            if (planDeny != null) {
                return ToolExecutionOutcome.failure(planDeny, planDeny, null, "PLAN_MODE_DENY");
            }

            ToolArtifactSource artifactSource = ToolArtifactSource.builder()
                    .sessionId(context.getSessionId())
                    .requestId(context.getRequestId())
                    .toolCallId(command.getId())
                    .toolName(toolName)
                    .build();

            Object resultObject;
            context.bindCurrentToolArtifactSource(artifactSource);
            try {
                resultObject = agent.getAvailableTools().executeResolved(toolName, args);
            } finally {
                context.clearCurrentToolArtifactSource();
            }

            if ("deep_search".equals(toolName)) {
                log.debug("{} execute tool: {} {} result {}", context.getRequestId(), toolName, args, resultObject);
            } else {
                log.info("{} execute tool: {} {} result {}", context.getRequestId(), toolName, args, resultObject);
            }

            if (resultObject == null) {
                return ToolExecutionOutcome.failure(
                        "Tool " + toolName + " Error.",
                        "Tool " + toolName + " Error.",
                        null,
                        "Tool returned null"
                );
            }

            ToolResultPayload payload = normalizeToolResultPayload(toolName, resultObject);
            String toolResult = StringUtils.defaultString(payload.getToolResult());
            String llmObservation = StringUtils.defaultIfBlank(payload.getLlmObservation(), toolResult);
            if (Boolean.TRUE.equals(payload.getFailed())) {
                return ToolExecutionOutcome.failure(
                        toolResult,
                        llmObservation,
                        payload.getStructuredOutput(),
                        StringUtils.defaultIfBlank(payload.getErrorMsg(), toolResult)
                );
            }
            return ToolExecutionOutcome.success(toolResult, llmObservation, payload.getStructuredOutput(),
                    payload.getBase64Image(), payload.getImageMimeType());
        } catch (UserInputRequiredException yield) {
            throw yield;
        } catch (DesktopControlRequiredException yield) {
            throw yield;
        } catch (PlanApprovalRequiredException yield) {
            throw yield;
        } catch (Exception e) {
            if (context != null && context.isRunCancelled()) {
                Thread.interrupted();
                return userStopOutcome("工具执行已取消：用户停止本轮对话");
            }
            log.error("{} execute tool {} failed ", context == null ? "-" : context.getRequestId(), toolName, e);
            return ToolExecutionOutcome.failure(
                    "Tool " + toolName + " Error.",
                    "Tool " + toolName + " Error.",
                    null,
                    e.getMessage()
            );
        }
    }

    /**
     * 让出类工具必须独占一轮，否则模型会把用户答复和其他工具的结果混在一起。
     *
     * @return "ASK_USER" / "EXIT_PLAN_MODE" / "DESKTOP_CONTROL"；未违规返回 null
     */
    private String detectSoleYieldToolViolation(List<ToolCall> commands) {
        boolean hasAsk = false;
        boolean hasExit = false;
        boolean hasDesktop = false;
        int total = 0;
        for (ToolCall command : commands) {
            if (command == null || command.getFunction() == null) {
                continue;
            }
            total++;
            String name = command.getFunction().getName();
            if (AskUserQuestionTool.NAME.equals(name)) {
                hasAsk = true;
            }
            if (RequestDesktopControlTool.NAME.equals(name)) {
                hasDesktop = true;
            }
            if (TaskToolNames.EXIT_PLAN_MODE.equals(name)) {
                hasExit = true;
            }
        }
        if ((hasAsk || hasExit || hasDesktop) && total > 1) {
            if (hasExit) {
                return "EXIT_PLAN_MODE";
            }
            if (hasDesktop) {
                return "DESKTOP_CONTROL";
            }
            return "ASK_USER";
        }
        return null;
    }

    private Map<String, ToolExecutionOutcome> rejectMixedYieldBatch(List<ToolCall> commands, String violation) {
        String code;
        String msg;
        if ("EXIT_PLAN_MODE".equals(violation)) {
            code = "EXIT_PLAN_MODE_MUST_BE_SOLE";
            msg = "ExitPlanMode 必须是本轮唯一 tool call，不能与其他工具并行";
        } else if ("DESKTOP_CONTROL".equals(violation)) {
            code = "DESKTOP_CONTROL_MUST_BE_SOLE";
            msg = "RequestDesktopControl 必须是本轮唯一 tool call，不能与其他工具并行";
        } else {
            code = "ASK_USER_QUESTION_MUST_BE_SOLE";
            msg = "AskUserQuestion 必须是本轮唯一 tool call，不能与其他工具并行";
        }
        Map<String, Integer> dispatchIndexMapping = buildDispatchIndexMapping(commands);
        Map<String, ToolExecutionOutcome> collected = new ConcurrentHashMap<>();
        for (ToolCall command : commands) {
            if (command == null || StringUtils.isBlank(command.getId())) {
                continue;
            }
            completeToolOutcome(collected, command, Map.of(),
                    toolFailureOutcome(msg, code), dispatchIndexMapping, true);
        }
        return orderedOutcomes(commands, collected);
    }

    private void completeToolOutcome(Map<String, ToolExecutionOutcome> result,
                                     ToolCall toolCall,
                                     Map<String, ToolDispatch> dispatches,
                                     ToolExecutionOutcome outcome,
                                     Map<String, Integer> dispatchIndexMapping,
                                     boolean recordArtifacts) {
        if (toolCall == null || StringUtils.isBlank(toolCall.getId()) || outcome == null) {
            return;
        }
        if (result.containsKey(toolCall.getId())) {
            return;
        }
        result.put(toolCall.getId(), outcome);
        ToolDispatch dispatch = dispatches == null ? null : dispatches.get(toolCall.getId());
        settle(toolCall, dispatch, dispatchIndexMapping.get(toolCall.getId()), outcome, recordArtifacts);
    }

    /** 单次工具调用的收口顺序：计时 → 账本终态 → 产物 → SSE 终态 → 释放计时。 */
    private void settle(ToolCall command, ToolDispatch dispatch, Integer dispatchIndex, ToolExecutionOutcome outcome) {
        settle(command, dispatch, dispatchIndex, outcome, true);
    }

    private void settle(ToolCall command,
                        ToolDispatch dispatch,
                        Integer dispatchIndex,
                        ToolExecutionOutcome outcome,
                        boolean recordArtifacts) {
        timings.attachFinished(command, outcome);
        ledger.finish(command, dispatch, outcome);
        if (recordArtifacts) {
            ledger.recordArtifacts(command);
        }
        events.emitFinished(command, dispatch, dispatchIndex, outcome);
        timings.clear(command);
    }

    private static Map<String, ToolExecutionOutcome> orderedOutcomes(List<ToolCall> commands,
                                                                     Map<String, ToolExecutionOutcome> result) {
        Map<String, ToolExecutionOutcome> ordered = new LinkedHashMap<>(commands.size());
        for (ToolCall command : commands) {
            if (command != null && StringUtils.isNotBlank(command.getId())) {
                ordered.put(command.getId(), result.get(command.getId()));
            }
        }
        return ordered;
    }

    private static ToolExecutionOutcome toolFailureOutcome(String message, String errorMsg) {
        return ToolExecutionOutcome.failure(message, message, null, errorMsg);
    }

    private static ToolExecutionOutcome userStopOutcome(String message) {
        return toolFailureOutcome(message, "USER_STOP");
    }

    static boolean isUserStop(ToolExecutionOutcome outcome) {
        return outcome != null && "USER_STOP".equals(outcome.getErrorMsg());
    }

    static int ledgerStatus(ToolExecutionOutcome outcome) {
        if (outcome != null && outcome.isSuccess()) {
            return ExecutionLedgerConstants.STATUS_SUCCESS;
        }
        if (isUserStop(outcome)) {
            return ExecutionLedgerConstants.STATUS_STOPPED;
        }
        return ExecutionLedgerConstants.STATUS_FAILED;
    }

    private Map<String, ToolExecutionOutcome> finishCancelledBeforeStart(List<ToolCall> commands) {
        timings.start(commands);
        Map<String, ToolDispatch> dispatches = resolveAll(commands);
        Map<String, Long> toolInvocationIds = ensureToolInvocationIds(dispatches, commands);
        AgentContext context = agent.getContext();
        if (context != null && context.getAgentRunState() != null) {
            context.getAgentRunState().bindToolInvocationIds(toolInvocationIds);
        }
        Map<String, Integer> dispatchIndexMapping = buildDispatchIndexMapping(commands);
        Map<String, ToolExecutionOutcome> result = new LinkedHashMap<>();
        ToolExecutionOutcome cancelled = userStopOutcome("工具未执行：用户已停止本轮对话");
        for (ToolCall command : commands) {
            completeToolOutcome(result, command, dispatches, cancelled, dispatchIndexMapping, false);
        }
        return orderedOutcomes(commands, result);
    }

    private Map<String, Integer> buildDispatchIndexMapping(List<ToolCall> commands) {
        Map<String, Integer> dispatchIndexMapping = new LinkedHashMap<>();
        if (commands == null || commands.isEmpty()) {
            return dispatchIndexMapping;
        }
        int dispatchIndex = 1;
        for (ToolCall command : commands) {
            if (command == null || StringUtils.isBlank(command.getId())) {
                continue;
            }
            dispatchIndexMapping.put(command.getId(), dispatchIndex++);
        }
        return dispatchIndexMapping;
    }

    private Object parseToolArguments(String toolName, String arguments) throws Exception {
        String normalizedPayload = ToolCallJson.normalize(arguments);
        try {
            return ToolCallJson.MAPPER.readValue(normalizedPayload, Object.class);
        } catch (Exception parseError) {
            Map<String, Object> salvaged = trySalvageToolArguments(toolName, normalizedPayload, parseError);
            if (salvaged != null) {
                return salvaged;
            }
            throw parseError;
        }
    }

    private Map<String, Object> trySalvageToolArguments(String toolName,
                                                        String normalizedPayload,
                                                        Exception parseError) {
        AgentContext context = agent.getContext();
        if (CanvasPublishArgSalvage.isCanvasPublish(toolName)) {
            Map<String, Object> salvaged = CanvasPublishArgSalvage.parseOrSalvage(normalizedPayload);
            if (salvaged != null && !salvaged.isEmpty()) {
                log.warn("{} canvas_publish args salvaged after parse failure: {}",
                        context == null ? "-" : context.getRequestId(),
                        parseError.getMessage());
                return salvaged;
            }
        }
        if (EmitUiTreeArgSalvage.isEmitUiTree(toolName)) {
            Map<String, Object> salvaged = EmitUiTreeArgSalvage.parseOrSalvage(normalizedPayload);
            if (salvaged != null && !salvaged.isEmpty()) {
                log.warn("{} emit_ui_tree args salvaged after parse failure: {}",
                        context == null ? "-" : context.getRequestId(),
                        parseError.getMessage());
                return salvaged;
            }
        }
        return null;
    }

    private ToolResultPayload normalizeToolResultPayload(String toolName, Object rawResult) {
        if (rawResult instanceof ToolResultPayload payload) {
            boolean failed = Boolean.TRUE.equals(payload.getFailed());
            String toolResult = StringUtils.defaultString(payload.getToolResult());
            String llmObservation = payload.getLlmObservation();
            if (StringUtils.isBlank(llmObservation)) {
                String compact = failed ? null : ToolObservationFormatter.format(toolName, payload.getLlmData());
                if (compact != null) {
                    // 投影后仍可能超大（大仓库 grep / 长文件 read）：完整内容落工作区，模型拿到可续读的信封。
                    llmObservation = ToolObservationBudget.apply(agent.getContext(), toolName, compact);
                } else if (payload.getLlmData() != null || failed) {
                    llmObservation = ToolObservationSerializer.serializePayload(payload);
                } else {
                    llmObservation = toolResult;
                }
            }
            if (StringUtils.isBlank(toolResult)) {
                // 完整结果落 tool_result 列：优先结构化 JSON，绝不落到紧凑投影文本。
                toolResult = payload.getLlmData() != null || failed
                        ? ToolObservationSerializer.serializePayload(payload)
                        : llmObservation;
            }
            return ToolResultPayload.builder()
                    .toolResult(toolResult)
                    .llmObservation(llmObservation)
                    .llmData(payload.getLlmData())
                    .structuredOutput(payload.getStructuredOutput())
                    .base64Image(payload.getBase64Image())
                    .imageMimeType(payload.getImageMimeType())
                    .failed(failed)
                    .errorMsg(payload.getErrorMsg())
                    .build();
        }
        if (rawResult instanceof String textResult) {
            return ToolResultPayload.builder()
                    .toolResult(textResult)
                    .llmObservation(ToolObservationSerializer.serializeSuccess(textResult))
                    .llmData(textResult)
                    .failed(Boolean.FALSE)
                    .build();
        }
        String serialized = ToolObservationSerializer.serializeSuccess(rawResult);
        return ToolResultPayload.builder()
                .toolResult(serialized)
                .llmObservation(serialized)
                .llmData(rawResult)
                .failed(Boolean.FALSE)
                .build();
    }

    private ToolExecutionOutcome finalizeOutcome(ToolCall command, ToolExecutionOutcome outcome) {
        if (outcome == null) {
            return null;
        }
        String toolCallId = command == null ? null : command.getId();
        return outcome.setLlmObservation(agent.buildFinalLlmObservation(outcome.getLlmObservation(), toolCallId));
    }

    private Map<String, ToolDispatch> resolveAll(List<ToolCall> commands) {
        Map<String, ToolDispatch> resolved = new LinkedHashMap<>();
        if (commands == null) {
            return resolved;
        }
        for (ToolCall command : commands) {
            if (command == null || StringUtils.isBlank(command.getId())) {
                continue;
            }
            resolved.put(command.getId(), resolveDispatch(command));
        }
        return resolved;
    }

    private ToolDispatch resolveDispatch(ToolCall command) {
        if (command == null || command.getFunction() == null
                || StringUtils.isBlank(command.getFunction().getName())) {
            return new ToolDispatch(null, null, false, "{}", null);
        }
        String originalName = command.getFunction().getName();
        String originalArgs = command.getFunction().getArguments();
        if (!McpToolNames.TOOL_CALL.equals(originalName)) {
            DeferredToolCatalog catalog = agent.getAvailableTools() == null
                    ? null
                    : agent.getAvailableTools().getDeferredToolCatalog();
            if (catalog != null && catalog.contains(originalName)) {
                String message = "Tool " + originalName
                        + " is deferred. Invoke it via ToolCall.";
                return new ToolDispatch(originalName, null, true, ToolCallJson.normalize(originalArgs),
                        ToolExecutionOutcome.failure(message, message, null, "DEFERRED_TOOL_DIRECT_CALL"));
            }
            return new ToolDispatch(originalName, null, false, ToolCallJson.normalize(originalArgs), null);
        }
        Object parsed;
        try {
            parsed = parseToolArguments(originalName, originalArgs);
        } catch (Exception e) {
            return new ToolDispatch(originalName, Map.of(), true, ToolCallJson.normalize(originalArgs),
                    ToolExecutionOutcome.failure(
                            "Error: Invalid function call format",
                            "Error: Invalid function call format",
                            null,
                            "Invalid function call format"));
        }
        DeferredToolCall.Result result = DeferredToolCall.resolve(agent.getAvailableTools(), parsed);
        if (!result.ok()) {
            ToolResultPayload payload = normalizeToolResultPayload(originalName, result.errorPayload());
            String toolResult = StringUtils.defaultString(payload.getToolResult());
            String observation = StringUtils.defaultIfBlank(payload.getLlmObservation(), toolResult);
            ToolExecutionOutcome early = Boolean.TRUE.equals(payload.getFailed())
                    ? ToolExecutionOutcome.failure(toolResult, observation, payload.getStructuredOutput(),
                    StringUtils.defaultIfBlank(payload.getErrorMsg(), toolResult))
                    : ToolExecutionOutcome.success(toolResult, observation, payload.getStructuredOutput(),
                    payload.getBase64Image(), payload.getImageMimeType());
            return new ToolDispatch(originalName, parsed, true, ToolCallJson.normalize(originalArgs), early);
        }
        return new ToolDispatch(result.name(), result.arguments(), true, ToolCallJson.write(result.arguments()), null);
    }
}
