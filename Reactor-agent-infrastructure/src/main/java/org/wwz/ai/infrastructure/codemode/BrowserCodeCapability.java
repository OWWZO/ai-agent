package org.wwz.ai.infrastructure.codemode;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.browser.BrowserTool;
import org.wwz.ai.domain.agent.runtime.tool.codemode.CodeModeErrorCode;
import org.wwz.ai.domain.agent.runtime.tool.codemode.CodeModeInvocationRecorder;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Code Mode 的 {@code browser} capability。
 * <p>
 * <b>不重新拼装 Browser CLI 环境</b>：直接委托现有 {@link BrowserTool}，
 * 因此用户身份校验、Relay 在线检查、OpenCLI 参数重写、Secret 注入、
 * 超时、输出上限、敏感参数脱敏、Viewport 与 artifact 处理全部与 {@code browser} 工具一致。
 * 每次执行持有独立的工具实例，不共享带可变 {@link AgentContext} 的对象。
 * </p>
 * <p>
 * {@code agentBrowser} 不在这里暴露，也不可通过任何参数切换。
 * </p>
 */
public final class BrowserCodeCapability {

    private final AgentContext agentContext;
    private final CodeModeExecutionBudget budget;
    private final int maxOutputChars;

    public BrowserCodeCapability(AgentContext agentContext, CodeModeExecutionBudget budget, int maxOutputChars) {
        this.agentContext = agentContext;
        this.budget = budget;
        this.maxOutputChars = maxOutputChars > 0 ? maxOutputChars : 1_048_576;
    }

    /**
     * JS: {@code await browser.command({ args: [...], timeoutMs: 120000 })}
     */
    public Map<String, Object> command(Map<String, Object> args) {
        budget.beginCall(agentContext);
        Map<String, Object> params = args == null ? new LinkedHashMap<>() : new LinkedHashMap<>(args);
        List<String> argv = toStringList(params.get("args"));
        if (argv.isEmpty()) {
            throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_INVALID_ARGUMENT,
                    "browser.command 需要非空 args 数组");
        }
        long requested = params.get("timeoutMs") instanceof Number number
                ? number.longValue()
                : (params.get("timeout_ms") instanceof Number fallback ? fallback.longValue() : 0L);
        long remaining = budget.remainingMs();
        long timeoutMs = requested > 0 ? Math.min(requested, remaining) : remaining;
        params.put("args", argv);
        params.put("timeout_ms", timeoutMs);

        ToolArtifactSource source = agentContext.getCurrentToolArtifactSource();
        String parentToolCallId = source == null ? null : source.getToolCallId();
        LocalDateTime startedAt = LocalDateTime.now();
        Long invocationId = CodeModeInvocationRecorder.begin(
                agentContext, parentToolCallId, CodeModeInvocationRecorder.TOOL_BROWSER,
                CodeModeInvocationRecorder.summarize(params), startedAt);
        try {
            BrowserTool tool = new BrowserTool();
            tool.setAgentContext(agentContext);
            Map<String, Object> fields = BrowserCodeModeGate.withUserLock(
                    agentContext.getUserId(),
                    () -> invoke(tool, params));
            CodeModeInvocationRecorder.end(agentContext, invocationId, CodeModeInvocationRecorder.TOOL_BROWSER,
                    fields.get("ok") == Boolean.TRUE
                            ? ExecutionLedgerConstants.STATUS_SUCCESS
                            : ExecutionLedgerConstants.STATUS_FAILED,
                    summarize(fields), null, LocalDateTime.now());
            return fields;
        } catch (CodeModeExecutionException e) {
            CodeModeInvocationRecorder.end(agentContext, invocationId, CodeModeInvocationRecorder.TOOL_BROWSER,
                    ExecutionLedgerConstants.STATUS_FAILED, null, e.getMessage(), LocalDateTime.now());
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invoke(BrowserTool tool, Map<String, Object> params) {
        Object raw = tool.execute(params);
        Map<String, Object> fields = new LinkedHashMap<>();
        if (raw instanceof ToolResultPayload payload) {
            if (payload.getLlmData() instanceof Map<?, ?> data) {
                fields.putAll((Map<String, Object>) data);
            } else if (payload.getLlmData() != null) {
                fields.put("output", String.valueOf(payload.getLlmData()));
            }
            fields.put("failed", Boolean.TRUE.equals(payload.getFailed()));
            if (StringUtils.isNotBlank(payload.getErrorMsg())) {
                fields.put("error", payload.getErrorMsg());
            }
        } else if (raw instanceof Map<?, ?> data) {
            fields.putAll((Map<String, Object>) data);
        } else {
            fields.put("output", String.valueOf(raw));
        }
        if (fields.get("ok") == null) {
            fields.put("ok", !Boolean.TRUE.equals(fields.get("failed")));
        }
        return truncate(fields);
    }

    private Map<String, Object> truncate(Map<String, Object> fields) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String text && text.length() > maxOutputChars) {
                result.put(entry.getKey(), text.substring(0, maxOutputChars) + "...(truncated)");
            } else {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }

    private static String summarize(Map<String, Object> fields) {
        Object stdout = fields.get("stdout");
        if (stdout == null) {
            return String.valueOf(fields.get("ok"));
        }
        String text = String.valueOf(stdout);
        return text.length() <= 500 ? text : text.substring(0, 500) + "...";
    }

    private static List<String> toStringList(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<String> values = new java.util.ArrayList<>(list.size());
        for (Object item : list) {
            if (!(item instanceof String text) || text.isBlank()) {
                throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_INVALID_ARGUMENT,
                        "browser.command 的 args 必须是非空字符串数组");
            }
            values.add(text);
        }
        return values;
    }
}
