package org.wwz.ai.domain.agent.runtime.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.ledger.model.ArtifactRecordCommand;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationBatchStartRecord;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationFinishRecord;
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
import org.wwz.ai.domain.agent.runtime.planmode.PlanApprovalRequiredException;
import org.wwz.ai.domain.agent.runtime.tool.common.AgentDispatchTool;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.AskUserQuestionTool;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.TaskToolNames;

import java.time.LocalDateTime;
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

    ToolExecutionPipeline(BaseAgent agent) {
        this.agent = agent;
    }

    ToolExecutionOutcome executeOne(ToolCall command) {
        List<ToolCall> commands = command == null ? List.of() : List.of(command);
        Map<String, Long> toolInvocationIds = ensureToolInvocationIds(commands);
        AgentContext context = agent.getContext();
        if (context != null && context.getAgentRunState() != null && !toolInvocationIds.isEmpty()) {
            context.getAgentRunState().bindToolInvocationIds(toolInvocationIds);
        }
        Map<String, Integer> dispatchIndexMapping = buildDispatchIndexMapping(commands);
        emitToolCallRunningEvents(commands, dispatchIndexMapping);
        ToolExecutionOutcome outcome = finalizeOutcome(command, executeInternal(command));
        finishToolInvocation(command, outcome);
        recordToolArtifacts(command);
        emitToolCallFinishedEvent(command, dispatchIndexMapping.get(command == null ? null : command.getId()), outcome);
        return outcome;
    }

    Map<String, ToolExecutionOutcome> executeBatch(List<ToolCall> commands) {
        Map<String, ToolExecutionOutcome> result = new ConcurrentHashMap<>();
        if (commands == null || commands.isEmpty()) {
            return result;
        }

        String soleYieldViolation = detectSoleYieldToolViolation(commands);
        if (soleYieldViolation != null) {
            Map<String, Integer> dispatchIndexMapping = buildDispatchIndexMapping(commands);
            String code = "EXIT_PLAN_MODE".equals(soleYieldViolation)
                    ? "EXIT_PLAN_MODE_MUST_BE_SOLE"
                    : "ASK_USER_QUESTION_MUST_BE_SOLE";
            String msg = "EXIT_PLAN_MODE".equals(soleYieldViolation)
                    ? "ExitPlanMode 必须是本轮唯一 tool call，不能与其他工具并行"
                    : "AskUserQuestion 必须是本轮唯一 tool call，不能与其他工具并行";
            for (ToolCall command : commands) {
                if (command == null || StringUtils.isBlank(command.getId())) {
                    continue;
                }
                completeToolOutcome(
                        result,
                        command,
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
        Map<String, Long> toolInvocationIds = ensureToolInvocationIds(commands);
        AgentContext context = agent.getContext();
        if (context != null && context.getAgentRunState() != null) {
            context.getAgentRunState().bindToolInvocationIds(toolInvocationIds);
        }
        emitToolCallRunningEvents(commands, dispatchIndexMapping);

        AtomicReference<RuntimeException> yieldSignal = new AtomicReference<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>(commands.size());
        List<CompletableFuture<?>> executionFutures = new ArrayList<>(commands.size());
        for (ToolCall toolCall : commands) {
            Executor executor = resolveExecutorForTool(toolCall);
            String scene = isAgentDispatchTool(toolCall) ? "subAgentBatch" : "toolBatch";
            CompletableFuture<ToolExecutionOutcome> executionFuture = AgentExecutorSupport
                    .supplyAsync(executor, scene, context,
                            () -> finalizeOutcome(toolCall, executeInternal(toolCall)));
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
                    if (root instanceof PlanApprovalRequiredException planApprovalRequired) {
                        yieldSignal.compareAndSet(null, planApprovalRequired);
                        return null;
                    }
                    String msg = root.getMessage() == null
                            ? root.getClass().getSimpleName()
                            : root.getMessage();
                    completeToolOutcome(result, toolCall,
                            toolFailureOutcome("Tool execution error: " + msg, msg),
                            dispatchIndexMapping, true);
                    return null;
                }
                completeToolOutcome(result, toolCall, outcome, dispatchIndexMapping, true);
                return null;
            }));
        }

        awaitToolBatch(futures, executionFutures);
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
        Map<String, Long> created = preRegisterToolInvocations(missingCommands);
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
            items.add(ToolInvocationBatchStartRecord.Item.builder()
                    .toolCallId(command.getId())
                    .parentToolCallId(context.getParentToolUseId())
                    .subAgentId(context.getSubAgentId())
                    .subAgentType(context.getSubAgentType())
                    .subAgentDescription(context.getSubAgentDescription())
                    .dispatchIndex(dispatchIndex++)
                    .toolName(command.getFunction().getName())
                    .toolProvider(resolveToolProvider(command.getFunction().getName()))
                    .inputJson(normalizeToolPayload(command.getFunction().getArguments()))
                    .startedAt(LocalDateTime.now())
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

    private ToolExecutionOutcome executeInternal(ToolCall command) {
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

        String toolName = command.getFunction().getName();
        if (context != null && context.isRunCancelled()) {
            return ToolExecutionOutcome.failure(
                    "工具未执行：用户已停止本轮对话",
                    "工具未执行：用户已停止本轮对话",
                    null,
                    "USER_STOP"
            );
        }
        try {
            Object args = parseToolArguments(toolName, command.getFunction().getArguments());
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
                resultObject = agent.getAvailableTools().execute(toolName, args);
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
                    payload.getBase64Image(), payload.getImageMimeType());
        } catch (UserInputRequiredException yield) {
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
            if (TaskToolNames.EXIT_PLAN_MODE.equals(name)) {
                hasExit = true;
            }
        }
        if ((hasAsk || hasExit) && total > 1) {
            return hasExit ? "EXIT_PLAN_MODE" : "ASK_USER";
        }
        return null;
    }

    private void awaitToolBatch(List<CompletableFuture<Void>> futures,
                                List<CompletableFuture<?>> executionFutures) {
        if (futures == null || futures.isEmpty()) {
            return;
        }
        CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        long timeoutSeconds = resolveToolBatchTimeoutSeconds();
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
        finishToolInvocation(toolCall, outcome);
        if (recordArtifacts) {
            recordToolArtifacts(toolCall);
        }
        emitToolCallFinishedEvent(toolCall, dispatchIndexMapping.get(toolCall.getId()), outcome);
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

    private void emitToolCallRunningEvents(List<ToolCall> commands, Map<String, Integer> dispatchIndexMapping) {
        if (commands == null || commands.isEmpty()) {
            return;
        }
        for (ToolCall command : commands) {
            emitToolCallEvent(command, dispatchIndexMapping.get(command == null ? null : command.getId()),
                    "running", false, null);
        }
    }

    private void emitToolCallFinishedEvent(ToolCall command,
                                           Integer dispatchIndex,
                                           ToolExecutionOutcome outcome) {
        String status = outcome != null && outcome.isSuccess() ? "success" : "failed";
        emitToolCallEvent(command, dispatchIndex, status, true, outcome);
    }

    private void emitToolCallEvent(ToolCall command,
                                   Integer dispatchIndex,
                                   String status,
                                   boolean isFinal,
                                   ToolExecutionOutcome outcome) {
        Printer printer = agent.getPrinter();
        if (printer == null || command == null || command.getFunction() == null) {
            return;
        }
        String toolCallId = command.getId();
        String toolName = command.getFunction().getName();
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

        // 保留原始入参字符串，供前端在 running 阶段继续展示。
        String rawArguments = command.getFunction().getArguments();
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

    private void finishToolInvocation(ToolCall command, ToolExecutionOutcome outcome) {
        AgentContext context = agent.getContext();
        if (context == null || !context.hasActiveLedgerRun() || context.getAgentRunState() == null || command == null) {
            return;
        }
        Long toolInvocationId = context.getAgentRunState().resolveToolInvocationId(command.getId());
        if (toolInvocationId == null) {
            return;
        }
        context.getExecutionRecorder().finishToolInvocation(ToolInvocationFinishRecord.builder()
                .toolInvocationId(toolInvocationId)
                .runId(context.getAgentRunState().getRunId())
                .requestId(context.getRequestId())
                .sessionId(context.getSessionId())
                .toolCallId(command.getId())
                .toolName(command.getFunction().getName())
                .status(outcome != null && outcome.isSuccess()
                        ? ExecutionLedgerConstants.STATUS_SUCCESS
                        : ExecutionLedgerConstants.STATUS_FAILED)
                .llmObservation(outcome == null ? null : outcome.getLlmObservation())
                .structuredOutput(outcome == null ? null : outcome.getStructuredOutput())
                .errorMsg(outcome == null ? null : outcome.getErrorMsg())
                .finishedAt(LocalDateTime.now())
                .build());
        if (AgentDispatchTool.NAME.equals(command.getFunction().getName())) {
            AgentDispatchTool.settleLedgerIfTerminal(
                    context,
                    command.getId(),
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
}
