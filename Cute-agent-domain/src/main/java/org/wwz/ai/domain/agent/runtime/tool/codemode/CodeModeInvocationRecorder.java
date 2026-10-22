package org.wwz.ai.domain.agent.runtime.tool.codemode;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.ledger.AgentExecutionRecorder;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationBatchStartRecord;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationFinishRecord;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Code Mode 内部 capability 调用的账本记录器。
 * <p>
 * 复用现有 Execution Ledger：内部调用以 {@code code_mode.browser} 等虚拟工具名登记为
 * 独立的 tool invocation，并通过 {@code parentToolCallId} 指向外层 {@code code_mode} 调用。
 * 不新建消息账本，不写源码全文，参数只登记摘要。
 * </p>
 */
public final class CodeModeInvocationRecorder {

    /** 内部 browser capability 的虚拟工具名。 */
    public static final String TOOL_BROWSER = "code_mode.browser";
    /** 内部 host capability 的虚拟工具名。 */
    public static final String TOOL_HOST = "code_mode.host";
    /** 内部 workspace 读的虚拟工具名。 */
    public static final String TOOL_WORKSPACE_READ = "code_mode.workspace.read";
    /** 内部 workspace 写的虚拟工具名。 */
    public static final String TOOL_WORKSPACE_WRITE = "code_mode.workspace.write";

    private CodeModeInvocationRecorder() {
    }

    /**
     * 预登记一次内部调用。账本不可用时返回 null，调用方无需特殊处理。
     *
     * @param parentToolCallId 外层 code_mode 的 toolCallId
     * @param toolName         虚拟工具名
     * @param argSummary       参数摘要（已脱敏，不含完整敏感参数）
     * @return toolInvocationId，可能为 null
     */
    public static Long begin(AgentContext agentContext,
                             String parentToolCallId,
                             String toolName,
                             String argSummary,
                             LocalDateTime startedAt) {
        if (agentContext == null || !agentContext.hasActiveLedgerRun()) {
            return null;
        }
        AgentExecutionRecorder recorder = agentContext.getExecutionRecorder();
        if (recorder == null || agentContext.getAgentRunState() == null) {
            return null;
        }
        Long llmInvocationId = agentContext.getAgentRunState().getCurrentLlmInvocationId();
        if (llmInvocationId == null) {
            return null;
        }
        String callId = "cm-" + toolName + "-" + System.nanoTime();
        try {
            Map<String, Long> created = recorder.createToolInvocations(ToolInvocationBatchStartRecord.builder()
                    .runId(agentContext.getAgentRunState().getRunId())
                    .requestId(agentContext.getRequestId())
                    .llmInvocationId(llmInvocationId)
                    .items(List.of(ToolInvocationBatchStartRecord.Item.builder()
                            .toolCallId(callId)
                            .parentToolCallId(parentToolCallId)
                            .subAgentId(agentContext.getSubAgentId())
                            .subAgentType(agentContext.getSubAgentType())
                            .dispatchIndex(1)
                            .toolName(toolName)
                            .toolProvider(ExecutionLedgerConstants.TOOL_PROVIDER_LOCAL)
                            .inputJson(truncate(argSummary, 2000))
                            .startedAt(startedAt == null ? LocalDateTime.now() : startedAt)
                            .build()))
                    .build());
            if (created == null) {
                return null;
            }
            return created.get(callId);
        } catch (Exception e) {
            // 账本故障不能影响主链路
            return null;
        }
    }

    /**
     * 结束一次内部调用。
     *
     * @param status 见 {@link ExecutionLedgerConstants} 的 STATUS_*
     */
    public static void end(AgentContext agentContext,
                           Long toolInvocationId,
                           String toolName,
                           int status,
                           String observation,
                           String errorMsg,
                           LocalDateTime finishedAt) {
        if (agentContext == null || toolInvocationId == null) {
            return;
        }
        AgentExecutionRecorder recorder = agentContext.getExecutionRecorder();
        if (recorder == null || agentContext.getAgentRunState() == null) {
            return;
        }
        try {
            recorder.finishToolInvocation(ToolInvocationFinishRecord.builder()
                    .toolInvocationId(toolInvocationId)
                    .runId(agentContext.getAgentRunState().getRunId())
                    .requestId(agentContext.getRequestId())
                    .sessionId(agentContext.getSessionId())
                    .toolName(toolName)
                    .status(status)
                    .llmObservation(truncate(observation, 4000))
                    .errorMsg(truncate(errorMsg, 500))
                    .finishedAt(finishedAt == null ? LocalDateTime.now() : finishedAt)
                    .build());
        } catch (Exception ignore) {
            // 同上：账本故障不影响主链路
        }
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max) + "...(truncated)";
    }

    /**
     * 参数摘要：只保留键名和标量前若干字符，避免把整段 stdout 或 secret 写进账本。
     */
    public static String summarize(Map<String, Object> args) {
        if (args == null || args.isEmpty()) {
            return "{}";
        }
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            if (!first) {
                builder.append(", ");
            }
            first = false;
            builder.append(entry.getKey()).append("=");
            Object value = entry.getValue();
            if (value instanceof List<?> list) {
                builder.append("[").append(Math.min(list.size(), 8)).append(" items]");
            } else {
                builder.append(truncate(StringUtils.defaultString(String.valueOf(value)), 120));
            }
        }
        return builder.append("}").toString();
    }
}
