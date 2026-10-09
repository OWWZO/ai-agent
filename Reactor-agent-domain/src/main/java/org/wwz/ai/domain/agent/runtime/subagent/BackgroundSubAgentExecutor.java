package org.wwz.ai.domain.agent.runtime.subagent;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationFinishRecord;
import org.wwz.ai.domain.agent.runtime.stream.ToolResultStreamPayload;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.cancel.ActiveAgentRunRegistry;
import org.wwz.ai.domain.agent.runtime.cancel.RunCancellation;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.tasklist.RuntimeBackgroundTask;
import org.wwz.ai.domain.agent.runtime.tasklist.RuntimeBackgroundTaskRegistry;
import org.wwz.ai.domain.agent.runtime.tasklist.SessionAgentMailboxHub;
import org.wwz.ai.domain.agent.runtime.tasklist.SessionBackgroundTaskHub;
import org.wwz.ai.domain.agent.runtime.tool.ToolObservationSerializer;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.common.AgentDispatchTool;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 后台子 Agent 提交与结算。创建（Agent）与续跑（SendMessage）共用。
 */
@Slf4j
public final class BackgroundSubAgentExecutor {

    private static final ExecutorService BACKGROUND_EXECUTOR = Executors.newCachedThreadPool(new ThreadFactory() {
        private final AtomicInteger seq = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "bg-subagent-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    });

    private BackgroundSubAgentExecutor() {
    }

    public static ToolResultPayload submit(AgentContext parent,
                                           SubAgentRunner subAgentRunner,
                                           ActiveAgentRunRegistry runRegistry,
                                           String description,
                                           String prompt,
                                           String subagentType,
                                           String resumeAgentId,
                                           String parentToolUseId,
                                           String parentToolName) {
        RuntimeBackgroundTaskRegistry registry = parent.requireBackgroundTasks();
        RuntimeBackgroundTask task = registry.registerLocalAgent(description, subagentType, prompt);
        RunCancellation taskCancel = task.getCancellation() != null
                ? task.getCancellation()
                : new RunCancellation();
        task.setCancellation(taskCancel);

        boolean resume = StringUtils.isNotBlank(resumeAgentId);
        final String preferredAgentId = resume
                ? resumeAgentId.trim()
                : SubAgentContextFactory.newAgentId();
        task.setAgentId(preferredAgentId);
        registry.bindAgentId(task.getId(), preferredAgentId);

        final String desc = description;
        final String pmt = prompt;
        final String type = subagentType;
        final String resumeId = resume ? resumeAgentId.trim() : null;
        final String taskId = task.getId();
        final String sessionKey = StringUtils.defaultIfBlank(parent.getSessionId(), parent.getRequestId());
        final String toolName = StringUtils.defaultIfBlank(parentToolName, AgentDispatchTool.NAME);
        SessionAgentMailboxHub.queue(sessionKey, preferredAgentId);

        Future<?> future = BACKGROUND_EXECUTOR.submit(() -> {
            try {
                SubAgentResult result = subAgentRunner.run(
                        parent, desc, pmt, type, resumeId, taskCancel, preferredAgentId, parentToolUseId);
                registry.bindAgentId(taskId, result.getAgentId());
                if (taskCancel.isCancelled()
                        || RuntimeBackgroundTask.STATUS_STOPPED.equals(
                        registry.get(taskId).map(RuntimeBackgroundTask::getStatus).orElse(null))) {
                    registry.get(taskId).ifPresent(t -> {
                        if (StringUtils.isBlank(t.getOutput()) && StringUtils.isNotBlank(result.getContent())) {
                            t.setOutput(result.getContent());
                        }
                        if (StringUtils.isBlank(t.getAgentId())) {
                            t.setAgentId(result.getAgentId());
                        }
                        t.setAgentType(result.getAgentType());
                        t.setTotalToolUseCount(result.getTotalToolUseCount());
                        t.setTotalDurationMs(result.getTotalDurationMs());
                    });
                    persistLedgerSettlement(parent, parentToolUseId, toolName,
                            registry.get(taskId).orElse(task));
                    emitBackgroundSettlement(parent, parentToolUseId, toolName, desc, pmt, type, result, false, runRegistry);
                    return;
                }
                if (result.isCompleted()) {
                    registry.complete(taskId, result);
                    persistLedgerSettlement(parent, parentToolUseId, toolName,
                            registry.get(taskId).orElse(task));
                    emitBackgroundSettlement(parent, parentToolUseId, toolName, desc, pmt, type, result, true, runRegistry);
                } else {
                    registry.fail(taskId, result);
                    persistLedgerSettlement(parent, parentToolUseId, toolName,
                            registry.get(taskId).orElse(task));
                    emitBackgroundSettlement(parent, parentToolUseId, toolName, desc, pmt, type, result, false, runRegistry);
                }
            } catch (Exception e) {
                log.error("{} background subagent failed taskId={}", parent.getRequestId(), taskId, e);
                if (taskCancel.isCancelled()) {
                    emitBackgroundSettlement(parent, parentToolUseId, toolName, desc, pmt, type, null, false, runRegistry);
                    return;
                }
                String err = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                registry.fail(taskId, err);
                SubAgentResult failed = SubAgentResult.builder()
                        .status(SubAgentResult.STATUS_FAILED)
                        .agentId(preferredAgentId)
                        .agentType(type)
                        .description(desc)
                        .prompt(pmt)
                        .errorMsg(err)
                        .build();
                persistLedgerSettlement(parent, parentToolUseId, toolName,
                        registry.get(taskId).orElse(task));
                emitBackgroundSettlement(parent, parentToolUseId, toolName, desc, pmt, type, failed, false, runRegistry);
            }
        });
        registry.bindFuture(taskId, future);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tool", toolName);
        data.put("ok", true);
        data.put("status", RuntimeBackgroundTask.STATUS_RUNNING);
        data.put("task_id", taskId);
        data.put("agentId", preferredAgentId);
        data.put("task_type", RuntimeBackgroundTask.TYPE_LOCAL_AGENT);
        data.put("description", description);
        data.put("agentType", StringUtils.defaultIfBlank(subagentType, SubAgentRegistry.TYPE_GENERAL_PURPOSE));
        data.put("run_in_background", true);
        if (resume) {
            data.put("resumed", true);
            data.put("message", "已在后台唤醒子 Agent。用 TaskOutput(task_id=\"" + taskId
                    + "\") 取结果；SendMessage(to=\"" + preferredAgentId
                    + "\") 中途指导；TaskStop 取消。");
        } else {
            data.put("message", "后台子 Agent 已启动。用 TaskOutput(task_id=\"" + taskId
                    + "\") 取结果；SendMessage(to=\"" + preferredAgentId
                    + "\" 或 task_id) 中途指导；TaskStop 取消。");
        }
        return ToolResultPayload.fromData(data);
    }

    public static void settleLedgerIfTerminal(AgentContext parent,
                                              String parentToolUseId,
                                              String runningObservation) {
        settleLedgerIfTerminal(parent, parentToolUseId, AgentDispatchTool.NAME, runningObservation);
    }

    public static void settleLedgerIfTerminal(AgentContext parent,
                                              String parentToolUseId,
                                              String parentToolName,
                                              String runningObservation) {
        if (parent == null || StringUtils.isBlank(parentToolUseId)
                || StringUtils.isBlank(runningObservation)) {
            return;
        }
        try {
            Map<String, Object> receipt = JSON.parseObject(runningObservation, Map.class);
            String taskId = receipt == null ? null : trimToString(receipt.get("task_id"));
            if (StringUtils.isBlank(taskId)) {
                return;
            }
            String toolName = StringUtils.defaultIfBlank(parentToolName, AgentDispatchTool.NAME);
            parent.requireBackgroundTasks().get(taskId)
                    .filter(task -> !RuntimeBackgroundTask.STATUS_RUNNING.equals(task.getStatus()))
                    .ifPresent(task -> persistLedgerSettlement(parent, parentToolUseId, toolName, task));
        } catch (Exception e) {
            log.debug("background ledger settlement check skipped: {}", e.getMessage());
        }
    }

    public static boolean shouldSettleParentStream(AgentContext parent) {
        if (parent == null || !parent.isTurnClosed()) {
            return false;
        }
        return !SessionBackgroundTaskHub.hasRunning(
                SessionBackgroundTaskHub.keyFor(parent.getSessionId(), parent.getRequestId()));
    }

    private static void persistLedgerSettlement(AgentContext parent,
                                                String parentToolUseId,
                                                String parentToolName,
                                                RuntimeBackgroundTask task) {
        if (parent == null || task == null || StringUtils.isBlank(parentToolUseId)
                || !parent.hasActiveLedgerRun()
                || parent.getAgentRunState() == null
                || parent.getExecutionRecorder() == null) {
            return;
        }
        Long toolInvocationId = parent.getAgentRunState().resolveToolInvocationId(parentToolUseId);
        if (toolInvocationId == null) {
            return;
        }

        boolean completed = RuntimeBackgroundTask.STATUS_COMPLETED.equals(task.getStatus());
        String toolName = StringUtils.defaultIfBlank(parentToolName, AgentDispatchTool.NAME);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tool", toolName);
        data.put("ok", completed);
        data.put("status", task.getStatus());
        data.put("agentId", task.getAgentId());
        data.put("agentType", task.getAgentType());
        data.put("description", task.getDescription());
        data.put("content", task.getOutput());
        data.put("totalToolUseCount", task.getTotalToolUseCount());
        data.put("totalDurationMs", task.getTotalDurationMs());
        data.put("run_in_background", true);
        if (StringUtils.isNotBlank(task.getErrorMsg())) {
            data.put("errorMsg", task.getErrorMsg());
        }

        int ledgerStatus = RuntimeBackgroundTask.STATUS_STOPPED.equals(task.getStatus())
                ? ExecutionLedgerConstants.STATUS_STOPPED
                : completed ? ExecutionLedgerConstants.STATUS_SUCCESS : ExecutionLedgerConstants.STATUS_FAILED;
        parent.getExecutionRecorder().finishToolInvocation(ToolInvocationFinishRecord.builder()
                .toolInvocationId(toolInvocationId)
                .runId(parent.getAgentRunState().getRunId())
                .requestId(parent.getRequestId())
                .sessionId(parent.getSessionId())
                .toolCallId(parentToolUseId)
                .toolName(toolName)
                .status(ledgerStatus)
                .llmObservation(ToolObservationSerializer.serializeSuccess(data))
                .errorMsg(task.getErrorMsg())
                .finishedAt(resolveFinishedAt(task))
                .build());
    }

    private static LocalDateTime resolveFinishedAt(RuntimeBackgroundTask task) {
        if (task != null && task.getEndedAtMs() != null) {
            return LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(task.getEndedAtMs()),
                    ZoneId.systemDefault());
        }
        return LocalDateTime.now();
    }

    private static void emitBackgroundSettlement(AgentContext parent,
                                                 String parentToolUseId,
                                                 String parentToolName,
                                                 String description,
                                                 String prompt,
                                                 String subagentType,
                                                 SubAgentResult result,
                                                 boolean completed,
                                                 ActiveAgentRunRegistry runRegistry) {
        if (parent == null) {
            return;
        }
        Printer printer = parent.getPrinter();
        if (printer == null) {
            return;
        }
        String toolName = StringUtils.defaultIfBlank(parentToolName, AgentDispatchTool.NAME);
        try {
            if (StringUtils.isNotBlank(parentToolUseId)) {
                Map<String, Object> data = result == null
                        ? new LinkedHashMap<>()
                        : buildObservationData(result, toolName);
                data.put("tool", toolName);
                data.put("ok", completed);
                data.put("run_in_background", true);
                if (result == null) {
                    data.put("status", completed
                            ? SubAgentResult.STATUS_COMPLETED
                            : SubAgentResult.STATUS_FAILED);
                    data.put("description", description);
                    data.put("agentType", subagentType);
                } else if (!completed && StringUtils.isBlank(String.valueOf(data.get("status")))) {
                    data.put("status", SubAgentResult.STATUS_FAILED);
                }

                Map<String, Object> toolParam = new LinkedHashMap<>();
                toolParam.put("description", description);
                toolParam.put("prompt", prompt);
                toolParam.put("subagent_type",
                        StringUtils.defaultIfBlank(subagentType, SubAgentRegistry.TYPE_GENERAL_PURPOSE));
                toolParam.put("run_in_background", true);

                String observation = ToolObservationSerializer.serializeSuccess(data);
                ToolResultStreamPayload toolResult = ToolResultStreamPayload.builder()
                        .toolName(toolName)
                        .toolCallId(parentToolUseId)
                        .toolParam(toolParam)
                        .toolResult(observation)
                        .build();
                Map<String, Object> extra = new LinkedHashMap<>();
                extra.put("run_in_background", true);
                extra.put("status", completed ? "success" : "failed");
                extra.put("isFinal", true);
                printer.send(parentToolUseId, "tool_result", toolResult, extra, null, true);
            }
            if (shouldSettleParentStream(parent)) {
                Map<String, Object> settle = new LinkedHashMap<>();
                settle.put("reason", "background_idle");
                printer.send("stream_settle", settle);
                if (runRegistry != null && StringUtils.isNotBlank(parent.getRequestId())) {
                    runRegistry.end(parent.getRequestId());
                }
            }
        } catch (Exception e) {
            log.debug("background settlement emit skipped: {}", e.getMessage());
        }
    }

    private static Map<String, Object> buildObservationData(SubAgentResult result, String toolName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tool", toolName);
        body.put("ok", result.isCompleted());
        body.put("status", result.getStatus());
        body.put("agentId", result.getAgentId());
        body.put("agentType", result.getAgentType());
        body.put("description", result.getDescription());
        body.put("content", result.getContent());
        body.put("totalToolUseCount", result.getTotalToolUseCount());
        body.put("totalDurationMs", result.getTotalDurationMs());
        body.put("run_in_background", true);
        if (result.getMemoryPersisted() != null) {
            body.put("memoryPersisted", result.getMemoryPersisted());
        }
        if (StringUtils.isNotBlank(result.getErrorMsg())) {
            body.put("errorMsg", result.getErrorMsg());
        }
        return body;
    }

    private static String trimToString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
