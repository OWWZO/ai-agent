package org.wwz.ai.domain.agent.runtime.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.ledger.model.ArtifactRecordCommand;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationBatchStartRecord;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationFinishRecord;
import org.wwz.ai.domain.agent.ledger.model.replay.ReplayTiming;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.executor.AgentExecutorSupport;
import org.wwz.ai.domain.agent.runtime.planmode.PlanModeToolPolicy;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.ToolObservationSerializer;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.canvas.CanvasPublishArgSalvage;
import org.wwz.ai.domain.agent.runtime.tool.canvas.EmitUiTreeArgSalvage;
import org.wwz.ai.domain.agent.runtime.askuser.UserInputRequiredException;
import org.wwz.ai.domain.agent.runtime.desktopcontrol.DesktopControlRequiredException;
import org.wwz.ai.domain.agent.runtime.planmode.PlanApprovalRequiredException;
import org.wwz.ai.domain.agent.runtime.subagent.BackgroundSubAgentExecutor;
import org.wwz.ai.domain.agent.runtime.tool.common.AgentDispatchTool;
import org.wwz.ai.domain.agent.runtime.tool.common.mcp.McpToolNames;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.AskUserQuestionTool;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.RequestDesktopControlTool;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.TaskToolNames;
import org.wwz.ai.domain.agent.runtime.tool.deferred.DeferredToolCatalog;
import org.wwz.ai.domain.agent.runtime.tool.deferred.DeferredToolSource;
import org.wwz.ai.domain.agent.runtime.tool.mcp.runtime.DeferredToolCall;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * 工具执行管线：预登记 → 执行 → observation 收口 → 账本/产物/SSE 终态。
 */
@Slf4j
final class ToolExecutionPipeline {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final BaseAgent agent;
    private final Map<String, ReplayTiming> runtimeTimings = new ConcurrentHashMap<>();

    ToolExecutionPipeline(BaseAgent agent) {
        this.agent = agent;
    }

    ToolExecutionOutcome executeOne(ToolCall command) {
        List<ToolCall> commands = command == null ? List.of() : List.of(command);
        ensureRuntimeTimings(commands);
        Map<String, Dispatch> dispatches = resolveAll(commands);
        Map<String, Long> toolInvocationIds = ensureToolInvocationIds(dispatches, commands);
        AgentContext context = agent.getContext();
        if (context != null && context.getAgentRunState() != null && !toolInvocationIds.isEmpty()) {
            context.getAgentRunState().bindToolInvocationIds(toolInvocationIds);
        }
        Map<String, Integer> dispatchIndexMapping = buildDispatchIndexMapping(commands);
        emitToolCallRunningEvents(commands, dispatches, dispatchIndexMapping);
        Dispatch dispatch = dispatches.get(command == null ? null : command.getId());
        ToolExecutionOutcome outcome = finalizeOutcome(command, executeInternal(dispatch, command));
        attachFinishedTiming(command, outcome);
        finishToolInvocation(command, dispatch, outcome);
        recordToolArtifacts(command);
        emitToolCallFinishedEvent(command, dispatch, dispatchIndexMapping.get(command == null ? null : command.getId()), outcome);
        clearRuntimeTiming(command);
        return outcome;
    }

    Map<String, ToolExecutionOutcome> executeBatch(List<ToolCall> commands) {
        Map<String, ToolExecutionOutcome> result = new ConcurrentHashMap<>();
        if (commands == null || commands.isEmpty()) {
            return result;
        }

        ensureRuntimeTimings(commands);

        String soleYieldViolation = detectSoleYieldToolViolation(commands);
        if (soleYieldViolation != null) {
            Map<String, Integer> dispatchIndexMapping = buildDispatchIndexMapping(commands);
            String code;
            String msg;
            if ("EXIT_PLAN_MODE".equals(soleYieldViolation)) {
                code = "EXIT_PLAN_MODE_MUST_BE_SOLE";
                msg = "ExitPlanMode 必须是本轮唯一 tool call，不能与其他工具并行";
            } else if ("DESKTOP_CONTROL".equals(soleYieldViolation)) {
                code = "DESKTOP_CONTROL_MUST_BE_SOLE";
                msg = "RequestDesktopControl 必须是本轮唯一 tool call，不能与其他工具并行";
            } else {
                code = "ASK_USER_QUESTION_MUST_BE_SOLE";
                msg = "AskUserQuestion 必须是本轮唯一 tool call，不能与其他工具并行";
            }
            for (ToolCall command : commands) {
                if (command == null || StringUtils.isBlank(command.getId())) {
                    continue;
                }
                completeToolOutcome(
                        result,
                        command,
                        Map.of(),
                        toolFailureOutcome(msg, code),
                        dispatchIndexMapping,
                        true);
            }
            Map<String, ToolExecutionOutcome> ordered = new LinkedHashMap<>(commands.size());
            for (ToolCall command : commands) {
                if (command == null || StringUtils.isBlank(command.getId())) {
                    continue;
                }
                ordered.put(command.getId(), result.get(command.getId()));
            }
            return ordered;
        }

        Map<String, Integer> dispatchIndexMapping = buildDispatchIndexMapping(commands);
        Map<String, Dispatch> dispatches = resolveAll(commands);
        Map<String, Long> toolInvocationIds = ensureToolInvocationIds(dispatches, commands);
        AgentContext context = agent.getContext();
        if (context != null && context.getAgentRunState() != null) {
            context.getAgentRunState().bindToolInvocationIds(toolInvocationIds);
        }
        emitToolCallRunningEvents(commands, dispatches, dispatchIndexMapping);

        AtomicReference<RuntimeException> yieldSignal = new AtomicReference<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>(commands.size());
        List<CompletableFuture<?>> executionFutures = new ArrayList<>(commands.size());
        for (ToolCall toolCall : commands) {
            Executor executor = resolveExecutorForTool(toolCall);
            String scene = isAgentDispatchTool(toolCall) ? "subAgentBatch" : "toolBatch";
            CompletableFuture<ToolExecutionOutcome> executionFuture = AgentExecutorSupport
                    .supplyAsync(executor, scene, context,
                            () -> finalizeOutcome(toolCall, executeInternal(dispatches.get(toolCall.getId()), toolCall)));
            executionFutures.add(executionFuture);
            futures.add(executionFuture.handle((outcome, error) -> {
                if (error != null && unwrapExecutionError(error) instanceof CancellationException) {
                    return null;
                }
                if (error != null) {
                    Throwable root = unwrapExecutionError(error);
                    if (root instanceof UserInputRequiredException userInputRequired) {
                        yieldSignal.compareAndSet(null, userInputRequired);
                        return null;
                    }
                    if (root instanceof DesktopControlRequiredException desktopControlRequired) {
                        yieldSignal.compareAndSet(null, desktopControlRequired);
                        return null;
                    }
                    if (root instanceof PlanApprovalRequiredException planApprovalRequired) {
                        yieldSignal.compareAndSet(null, planApprovalRequired);
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

        awaitToolBatch(futures, executionFutures, commands);
        if (yieldSignal.get() != null) {
            throw yieldSignal.get();
        }
        for (ToolCall command : commands) {
            if (command == null || StringUtils.isBlank(command.getId()) || result.containsKey(command.getId())) {
                continue;
            }
            completeToolOutcome(
                    result,
                    command,
                    dispatches,
                    toolFailureOutcome("工具执行超时，已终止等待", "TOOL_BATCH_TIMEOUT"),
                    dispatchIndexMapping,
                    false);
        }

        Map<String, ToolExecutionOutcome> ordered = new LinkedHashMap<>(commands.size());
        for (ToolCall command : commands) {
            if (command != null && StringUtils.isNotBlank(command.getId())) {
                ordered.put(command.getId(), result.get(command.getId()));
            }
        }
        return ordered;
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

    private Map<String, Long> ensureToolInvocationIds(Map<String, Dispatch> dispatches, List<ToolCall> commands) {
        AgentContext context = agent.getContext();
        if (context == null || context.getAgentRunState() == null || commands == null || commands.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> existing = new LinkedHashMap<>();
        List<ToolCall> missingCommands = new ArrayList<>();
        for (ToolCall command : commands) {
            if (command == null || StringUtils.isBlank(command.getId())) {
                continue;
            }
            Long existingInvocationId = context.getAgentRunState().resolveToolInvocationId(command.getId());
            if (existingInvocationId != null) {
                existing.put(command.getId(), existingInvocationId);
            } else {
                missingCommands.add(command);
            }
        }
        if (missingCommands.isEmpty()) {
            return existing;
        }
        Map<String, Long> created = preRegisterToolInvocations(missingCommands, dispatches);
        if (existing.isEmpty()) {
            return created;
        }
        if (created.isEmpty()) {
            return existing;
        }
        existing.putAll(created);
        return existing;
    }

    Map<String, Long> preRegisterToolInvocations(List<ToolCall> commands) {
        return preRegisterToolInvocations(commands, resolveAll(commands));
    }

    private Map<String, Long> preRegisterToolInvocations(List<ToolCall> commands, Map<String, Dispatch> dispatches) {
        ensureRuntimeTimings(commands);
        AgentContext context = agent.getContext();
        if (context == null || !context.hasActiveLedgerRun() || context.getAgentRunState() == null) {
            return Map.of();
        }
        Long llmInvocationId = context.getAgentRunState().getCurrentLlmInvocationId();
        if (llmInvocationId == null) {
            return Map.of();
        }
        List<ToolInvocationBatchStartRecord.Item> items = new ArrayList<>(commands.size());
        int dispatchIndex = 1;
        for (ToolCall command : commands) {
            if (command == null || command.getFunction() == null || StringUtils.isBlank(command.getFunction().getName())) {
                continue;
            }
            Dispatch dispatch = dispatches == null ? null : dispatches.get(command.getId());
            String toolName = dispatch == null ? command.getFunction().getName() : dispatch.toolName;
            items.add(ToolInvocationBatchStartRecord.Item.builder()
                    .toolCallId(command.getId())
                    .parentToolCallId(context.getParentToolUseId())
                    .subAgentId(context.getSubAgentId())
                    .subAgentType(context.getSubAgentType())
                    .subAgentDescription(context.getSubAgentDescription())
                    .dispatchIndex(dispatchIndex++)
                    .toolName(toolName)
                    .toolProvider(resolveToolProvider(toolName))
                    .inputJson(dispatch == null ? normalizeToolPayload(command.getFunction().getArguments())
                            : dispatch.inputJson)
                    .startedAt(resolveRuntimeTiming(command).getStartedAt())
                    .build());
        }
        if (items.isEmpty()) {
            return Map.of();
        }
        return context.getExecutionRecorder().createToolInvocations(ToolInvocationBatchStartRecord.builder()
                .runId(context.getAgentRunState().getRunId())
                .requestId(context.getRequestId())
                .llmInvocationId(llmInvocationId)
                .agentName(agent.getName())
                .stepNo(agent.getCurrentStep())
                .items(items)
                .build());
    }

    private ToolExecutionOutcome executeInternal(Dispatch dispatch, ToolCall command) {
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
            return ToolExecutionOutcome.failure(
                    "工具未执行：用户已停止本轮对话",
                    "工具未执行：用户已停止本轮对话",
                    null,
                    "USER_STOP"
            );
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

            ToolResultPayload payload = normalizeToolResultPayload(resultObject);
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
                    payload.getBase64Image(), payload.getImageMimeType())
                    .setLedgerObservation(payload.getLedgerObservation());
        } catch (UserInputRequiredException yield) {
            throw yield;
        } catch (DesktopControlRequiredException yield) {
            throw yield;
        } catch (PlanApprovalRequiredException yield) {
            throw yield;
        } catch (Exception e) {
            log.error("{} execute tool {} failed ", context.getRequestId(), toolName, e);
            return ToolExecutionOutcome.failure(
                    "Tool " + toolName + " Error.",
                    "Tool " + toolName + " Error.",
                    null,
                    e.getMessage()
            );
        }
    }

    /**
     * @return "ASK_USER" / "EXIT_PLAN_MODE" when a yield tool is mixed with others; otherwise null
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

    private void awaitToolBatch(List<CompletableFuture<Void>> futures,
                                List<CompletableFuture<?>> executionFutures,
                                List<ToolCall> commands) {
        if (futures == null || futures.isEmpty()) {
            return;
        }
        CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        long timeoutSeconds = containsTaskOutput(commands) ? 0L : resolveToolBatchTimeoutSeconds();
        AgentContext context = agent.getContext();
        try {
            if (timeoutSeconds > 0L) {
                all.get(timeoutSeconds, TimeUnit.SECONDS);
            } else {
                all.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("{} tool batch interrupted, cancelling unfinished tools",
                    context == null ? "-" : context.getRequestId());
            cancelToolExecutions(executionFutures);
        } catch (TimeoutException e) {
            log.error("{} tool batch timed out after {}s",
                    context == null ? "-" : context.getRequestId(), timeoutSeconds);
            cancelToolExecutions(executionFutures);
        } catch (ExecutionException e) {
            Throwable root = unwrapExecutionError(e.getCause());
            if (root instanceof TimeoutException) {
                log.error("{} tool batch timed out after {}s",
                        context == null ? "-" : context.getRequestId(), timeoutSeconds);
                cancelToolExecutions(executionFutures);
            } else {
                log.error("{} tool batch join failed",
                        context == null ? "-" : context.getRequestId(), root);
            }
        }
    }

    private void cancelToolExecutions(List<CompletableFuture<?>> executionFutures) {
        if (executionFutures == null) {
            return;
        }
        for (CompletableFuture<?> executionFuture : executionFutures) {
            if (executionFuture != null && !executionFuture.isDone()) {
                executionFuture.cancel(true);
            }
        }
    }

    private void completeToolOutcome(Map<String, ToolExecutionOutcome> result,
                                     ToolCall toolCall,
                                     Map<String, Dispatch> dispatches,
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
        Dispatch dispatch = dispatches == null ? null : dispatches.get(toolCall.getId());
        attachFinishedTiming(toolCall, outcome);
        finishToolInvocation(toolCall, dispatch, outcome);
        if (recordArtifacts) {
            recordToolArtifacts(toolCall);
        }
        emitToolCallFinishedEvent(toolCall, dispatch, dispatchIndexMapping.get(toolCall.getId()), outcome);
        clearRuntimeTiming(toolCall);
    }

    private static ToolExecutionOutcome toolFailureOutcome(String message, String errorMsg) {
        return ToolExecutionOutcome.failure(message, message, null, errorMsg);
    }

    private static Throwable unwrapExecutionError(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current == null ? error : current;
    }

    private boolean isAgentDispatchTool(ToolCall toolCall) {
        return toolCall != null
                && toolCall.getFunction() != null
                && AgentDispatchTool.NAME.equals(toolCall.getFunction().getName());
    }

    private Executor resolveExecutorForTool(ToolCall toolCall) {
        if (isAgentDispatchTool(toolCall)) {
            return resolveExecutor(ReactorRuntimeDependencies::requireTaskExecutor);
        }
        return resolveExecutor(ReactorRuntimeDependencies::requireToolExecutor);
    }

    private static boolean containsTaskOutput(List<ToolCall> commands) {
        if (commands == null) {
            return false;
        }
        for (ToolCall command : commands) {
            if (command == null || command.getFunction() == null) {
                continue;
            }
            if (TaskToolNames.TASK_OUTPUT.equals(command.getFunction().getName())) {
                return true;
            }
        }
        return false;
    }

    private long resolveToolBatchTimeoutSeconds() {
        AgentContext context = agent.getContext();
        if (context == null || context.getRuntimeDependencies() == null) {
            return 600L;
        }
        return context.getRuntimeDependencies().resolveToolBatchTimeoutSeconds();
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

    private void emitToolCallRunningEvents(List<ToolCall> commands,
                                           Map<String, Dispatch> dispatches,
                                           Map<String, Integer> dispatchIndexMapping) {
        if (commands == null || commands.isEmpty()) {
            return;
        }
        for (ToolCall command : commands) {
            Dispatch dispatch = dispatches == null ? null : dispatches.get(command == null ? null : command.getId());
            emitToolCallEvent(command, dispatch, dispatchIndexMapping.get(command == null ? null : command.getId()),
                    "running", false, null);
        }
    }

    private void emitToolCallFinishedEvent(ToolCall command,
                                           Dispatch dispatch,
                                           Integer dispatchIndex,
                                           ToolExecutionOutcome outcome) {
        String status = outcome != null && outcome.isSuccess() ? "success" : "failed";
        emitToolCallEvent(command, dispatch, dispatchIndex, status, true, outcome);
    }

    private void emitToolCallEvent(ToolCall command,
                                   Dispatch dispatch,
                                   Integer dispatchIndex,
                                   String status,
                                   boolean isFinal,
                                   ToolExecutionOutcome outcome) {
        Printer printer = agent.getPrinter();
        if (printer == null || command == null || command.getFunction() == null) {
            return;
        }
        String toolCallId = command.getId();
        String toolName = dispatch == null ? command.getFunction().getName() : dispatch.toolName;
        if (StringUtils.isBlank(toolCallId) || StringUtils.isBlank(toolName)) {
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageType", "tool_call");
        payload.put("status", status);
        payload.put("toolName", toolName);
        payload.put("toolCallId", toolCallId);
        payload.put("streamToolKey", toolCallId);
        payload.put("toolProvider", resolveToolProvider(toolName));
        if (dispatchIndex != null) {
            payload.put("dispatchIndex", dispatchIndex);
        }

        AgentContext context = agent.getContext();
        Long toolInvocationId = context == null || context.getAgentRunState() == null
                ? null
                : context.getAgentRunState().resolveToolInvocationId(toolCallId);
        if (toolInvocationId != null) {
            payload.put("toolInvocationId", String.valueOf(toolInvocationId));
        }

        // 解包后的入参给前端卡片；Memory 仍保留模型发出的 ToolCall。
        String rawArguments = dispatch == null ? command.getFunction().getArguments() : dispatch.inputJson;
        if (StringUtils.isNotBlank(rawArguments)) {
            payload.put("argumentsText", rawArguments);
            payload.put("argumentsRaw", rawArguments);
        }

        Object input = parseToolCallInput(rawArguments);
        if (input != null) {
            payload.put("input", input);
        }

        payload.put("summary", buildToolCallSummary(toolName, status));
        payload.put("isFinal", isFinal);

        ReplayTiming timing = outcome == null
                ? resolveRuntimeTiming(command)
                : outcome.getTiming();
        if (timing != null) {
            payload.put("timing", snapshotTiming(timing));
        }

        if (outcome != null && StringUtils.isNotBlank(outcome.getErrorMsg())) {
            payload.put("errorMsg", outcome.getErrorMsg());
        }

        printer.send(toolCallId, "tool_call", payload, isFinal);
    }

    private Object parseToolCallInput(String arguments) {
        String normalizedPayload = normalizeToolPayload(arguments);
        try {
            return JSON.readValue(normalizedPayload, Object.class);
        } catch (Exception ignore) {
            return null;
        }
    }

    private Object parseToolArguments(String toolName, String arguments) throws Exception {
        String normalizedPayload = normalizeToolPayload(arguments);
        try {
            return JSON.readValue(normalizedPayload, Object.class);
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

    private String buildToolCallSummary(String toolName, String status) {
        if ("success".equals(status)) {
            return toolName + " 调用完成";
        }
        if ("failed".equals(status)) {
            return toolName + " 调用失败";
        }
        return "正在调用 " + toolName;
    }

    private void finishToolInvocation(ToolCall command, Dispatch dispatch, ToolExecutionOutcome outcome) {
        AgentContext context = agent.getContext();
        if (context == null || !context.hasActiveLedgerRun() || context.getAgentRunState() == null || command == null) {
            return;
        }
        Long toolInvocationId = context.getAgentRunState().resolveToolInvocationId(command.getId());
        if (toolInvocationId == null) {
            return;
        }
        String toolName = dispatch == null || command.getFunction() == null
                ? (command.getFunction() == null ? null : command.getFunction().getName())
                : dispatch.toolName;
        ReplayTiming timing = outcome == null ? resolveRuntimeTiming(command) : outcome.getTiming();
        LocalDateTime finishedAt = timing == null || timing.getFinishedAt() == null
                ? LocalDateTime.now()
                : timing.getFinishedAt();
        context.getExecutionRecorder().finishToolInvocation(ToolInvocationFinishRecord.builder()
                .toolInvocationId(toolInvocationId)
                .runId(context.getAgentRunState().getRunId())
                .requestId(context.getRequestId())
                .sessionId(context.getSessionId())
                .toolCallId(command.getId())
                .toolName(toolName)
                .status(outcome != null && outcome.isSuccess()
                        ? ExecutionLedgerConstants.STATUS_SUCCESS
                        : ExecutionLedgerConstants.STATUS_FAILED)
                    .llmObservation(outcome == null ? null : StringUtils.defaultIfBlank(
                            outcome.getLedgerObservation(), outcome.getLlmObservation()))
                .structuredOutput(outcome == null ? null : outcome.getStructuredOutput())
                .errorMsg(outcome == null ? null : outcome.getErrorMsg())
                .finishedAt(finishedAt)
                .build());
        if (command.getFunction() != null
                && (AgentDispatchTool.NAME.equals(command.getFunction().getName())
                || TaskToolNames.SEND_MESSAGE.equals(command.getFunction().getName()))) {
            BackgroundSubAgentExecutor.settleLedgerIfTerminal(
                    context,
                    command.getId(),
                    command.getFunction().getName(),
                    outcome == null ? null : outcome.getLlmObservation());
        }
    }

    private void recordToolArtifacts(ToolCall command) {
        AgentContext context = agent.getContext();
        if (context == null || !context.hasActiveLedgerRun() || context.getAgentRunState() == null || command == null) {
            return;
        }
        Long toolInvocationId = context.getAgentRunState().resolveToolInvocationId(command.getId());
        if (toolInvocationId == null) {
            return;
        }
        List<ArtifactRecordCommand> artifactCommands = new ArrayList<>();
        for (var binding : context.getArtifactBindingsByToolCallId(command.getId())) {
            if (binding == null || binding.getSource() == null || binding.getFile() == null) {
                continue;
            }
            File file = binding.getFile();
            artifactCommands.add(ArtifactRecordCommand.builder()
                    .runId(context.getAgentRunState().getRunId())
                    .requestId(context.getRequestId())
                    .toolInvocationId(toolInvocationId)
                    .toolCallId(command.getId())
                    .artifactRole(ExecutionLedgerConstants.ARTIFACT_ROLE_OUTPUT)
                    .visibility(binding.isInternalFile()
                            ? ExecutionLedgerConstants.VISIBILITY_INTERNAL
                            : ExecutionLedgerConstants.VISIBILITY_VISIBLE)
                    .sourceType(ExecutionLedgerConstants.SOURCE_TYPE_TOOL_OUTPUT)
                    .sourceName(binding.getSource().getToolName())
                    .fileName(file.getFileName())
                    .storageKey(resolveStorageKey(file))
                    .downloadUrl(file.getOssUrl())
                    .previewUrl(file.getDomainUrl())
                    .fileSize(file.getFileSize() == null ? null : file.getFileSize().longValue())
                    .metadataJson(buildArtifactMetadata(file))
                    .build());
        }
        if (!artifactCommands.isEmpty()) {
            context.getExecutionRecorder().recordArtifacts(artifactCommands);
        }
    }

    private String normalizeToolPayload(String payload) {
        if (StringUtils.isBlank(payload)) {
            return "{}";
        }
        try {
            return JSON.readTree(payload).toString();
        } catch (Exception ignore) {
            return "{}";
        }
    }

    private ToolResultPayload normalizeToolResultPayload(Object rawResult) {
        if (rawResult instanceof ToolResultPayload payload) {
            boolean failed = Boolean.TRUE.equals(payload.getFailed());
            String toolResult = StringUtils.defaultString(payload.getToolResult());
            String llmObservation = payload.getLlmObservation();
            if (StringUtils.isBlank(llmObservation)) {
                if (payload.getLlmData() != null || failed) {
                    llmObservation = ToolObservationSerializer.serializePayload(payload);
                } else {
                    llmObservation = toolResult;
                }
            }
            if (StringUtils.isBlank(toolResult)) {
                toolResult = llmObservation;
            }
            return ToolResultPayload.builder()
                    .toolResult(toolResult)
                    .llmObservation(llmObservation)
                    .llmData(payload.getLlmData())
                    .structuredOutput(payload.getStructuredOutput())
                    .ledgerObservation(payload.getLedgerObservation())
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

    private String resolveToolProvider(String toolName) {
        ToolCollection availableTools = agent.getAvailableTools();
        if (availableTools == null || StringUtils.isBlank(toolName)) {
            return ExecutionLedgerConstants.TOOL_PROVIDER_LOCAL;
        }
        if (availableTools.getMcpToolMap() != null && availableTools.getMcpToolMap().containsKey(toolName)) {
            return ExecutionLedgerConstants.TOOL_PROVIDER_MCP;
        }
        DeferredToolCatalog catalog = availableTools.getDeferredToolCatalog();
        if (catalog != null) {
            var entry = catalog.get(toolName);
            if (entry != null && entry.getSource() == DeferredToolSource.MCP) {
                return ExecutionLedgerConstants.TOOL_PROVIDER_MCP;
            }
        }
        return ExecutionLedgerConstants.TOOL_PROVIDER_LOCAL;
    }

    private Executor resolveExecutor(Function<ReactorRuntimeDependencies, Executor> picker) {
        AgentContext context = agent.getContext();
        if (context == null || context.getRuntimeDependencies() == null) {
            return Runnable::run;
        }
        return picker.apply(context.getRuntimeDependencies());
    }

    private String resolveStorageKey(File file) {
        if (file == null) {
            return "";
        }
        if (StringUtils.isNotBlank(file.getOriginOssUrl())) {
            return file.getOriginOssUrl();
        }
        if (StringUtils.isNotBlank(file.getOssUrl())) {
            return file.getOssUrl();
        }
        if (StringUtils.isNotBlank(file.getOriginDomainUrl())) {
            return file.getOriginDomainUrl();
        }
        if (StringUtils.isNotBlank(file.getDomainUrl())) {
            return file.getDomainUrl();
        }
        return StringUtils.defaultString(file.getFileName());
    }

    private String buildArtifactMetadata(File file) {
        if (file == null) {
            return null;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (StringUtils.isNotBlank(file.getDescription())) {
            metadata.put("description", file.getDescription());
        }
        if (StringUtils.isNotBlank(file.getOriginFileName())) {
            metadata.put("originFileName", file.getOriginFileName());
        }
        if (StringUtils.isNotBlank(file.getRelativePath())) {
            metadata.put("relativePath", file.getRelativePath());
        }
        if (StringUtils.isNotBlank(file.getOriginDomainUrl())) {
            metadata.put("originDomainUrl", file.getOriginDomainUrl());
        }
        if (metadata.isEmpty()) {
            return null;
        }
        try {
            return JSON.writeValueAsString(metadata);
        } catch (Exception ignore) {
            return null;
        }
    }

    private ToolExecutionOutcome finalizeOutcome(ToolCall command, ToolExecutionOutcome outcome) {
        if (outcome == null) {
            return null;
        }
        String toolCallId = command == null ? null : command.getId();
        return outcome.setLlmObservation(agent.buildFinalLlmObservation(outcome.getLlmObservation(), toolCallId));
    }

    private void ensureRuntimeTimings(List<ToolCall> commands) {
        if (commands == null) {
            return;
        }
        for (ToolCall command : commands) {
            if (command == null || StringUtils.isBlank(command.getId())) {
                continue;
            }
            runtimeTimings.computeIfAbsent(command.getId(), ignored -> ReplayTiming.builder()
                    .startedAt(LocalDateTime.now())
                    .source(ReplayTiming.SOURCE_RUNTIME)
                    .build());
        }
    }

    private ReplayTiming resolveRuntimeTiming(ToolCall command) {
        if (command == null || StringUtils.isBlank(command.getId())) {
            return null;
        }
        ensureRuntimeTimings(List.of(command));
        return runtimeTimings.get(command.getId());
    }

    private void attachFinishedTiming(ToolCall command, ToolExecutionOutcome outcome) {
        if (outcome == null) {
            return;
        }
        ReplayTiming timing = resolveRuntimeTiming(command);
        if (timing == null) {
            return;
        }
        LocalDateTime finishedAt = LocalDateTime.now();
        timing.setFinishedAt(finishedAt);
        timing.setDurationMs(Math.max(0L, Duration.between(timing.getStartedAt(), finishedAt).toMillis()));
        timing.setSource(ReplayTiming.SOURCE_RUNTIME);
        outcome.setTiming(timing);
    }

    private void clearRuntimeTiming(ToolCall command) {
        if (command != null && StringUtils.isNotBlank(command.getId())) {
            runtimeTimings.remove(command.getId());
        }
    }

    private ReplayTiming snapshotTiming(ReplayTiming timing) {
        if (timing == null) {
            return null;
        }
        return ReplayTiming.builder()
                .startedAt(timing.getStartedAt())
                .finishedAt(timing.getFinishedAt())
                .durationMs(timing.getDurationMs())
                .source(timing.getSource())
                .build();
    }

    private Map<String, Dispatch> resolveAll(List<ToolCall> commands) {
        Map<String, Dispatch> resolved = new LinkedHashMap<>();
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

    private Dispatch resolveDispatch(ToolCall command) {
        if (command == null || command.getFunction() == null
                || StringUtils.isBlank(command.getFunction().getName())) {
            return new Dispatch(null, null, false, "{}", null);
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
                return new Dispatch(originalName, null, true, normalizeToolPayload(originalArgs),
                        ToolExecutionOutcome.failure(message, message, null, "DEFERRED_TOOL_DIRECT_CALL"));
            }
            return new Dispatch(originalName, null, false, normalizeToolPayload(originalArgs), null);
        }
        Object parsed;
        try {
            parsed = parseToolArguments(originalName, originalArgs);
        } catch (Exception e) {
            return new Dispatch(originalName, Map.of(), true, normalizeToolPayload(originalArgs),
                    ToolExecutionOutcome.failure(
                            "Error: Invalid function call format",
                            "Error: Invalid function call format",
                            null,
                            "Invalid function call format"));
        }
        DeferredToolCall.Result result = DeferredToolCall.resolve(agent.getAvailableTools(), parsed);
        if (!result.ok()) {
            ToolResultPayload payload = normalizeToolResultPayload(result.errorPayload());
            String toolResult = StringUtils.defaultString(payload.getToolResult());
            String observation = StringUtils.defaultIfBlank(payload.getLlmObservation(), toolResult);
            ToolExecutionOutcome early = Boolean.TRUE.equals(payload.getFailed())
                    ? ToolExecutionOutcome.failure(toolResult, observation, payload.getStructuredOutput(),
                    StringUtils.defaultIfBlank(payload.getErrorMsg(), toolResult))
                    : ToolExecutionOutcome.success(toolResult, observation, payload.getStructuredOutput(),
                    payload.getBase64Image(), payload.getImageMimeType())
                    .setLedgerObservation(payload.getLedgerObservation());
            return new Dispatch(originalName, parsed, true, normalizeToolPayload(originalArgs), early);
        }
        return new Dispatch(result.name(), result.arguments(), true, toJson(result.arguments()), null);
    }

    private String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private static final class Dispatch {
        private final String toolName;
        private final Object args;
        private final boolean argsReady;
        private final String inputJson;
        private final ToolExecutionOutcome earlyOutcome;

        private Dispatch(String toolName,
                         Object args,
                         boolean argsReady,
                         String inputJson,
                         ToolExecutionOutcome earlyOutcome) {
            this.toolName = toolName;
            this.args = args;
            this.argsReady = argsReady;
            this.inputJson = inputJson;
            this.earlyOutcome = earlyOutcome;
        }
    }
}
