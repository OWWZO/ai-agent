package org.wwz.ai.infrastructure.codemode;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.adapter.port.cli.CliExecutionPort;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.cli.HostCliPolicy;
import org.wwz.ai.domain.agent.runtime.tool.cli.HostCliTool;
import org.wwz.ai.domain.agent.runtime.tool.codemode.CodeModeErrorCode;
import org.wwz.ai.domain.agent.runtime.tool.codemode.CodeModeInvocationRecorder;
import org.wwz.ai.types.agent.config.HostCliProperties;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Code Mode 的 {@code host} capability。
 * <p>
 * 与 {@code host_cli} 工具共用 {@link HostCliPolicy}：白名单、argv、无 shell、
 * cwd 固定会话 workspace、社交 CLI 只读约束全部一致，不存在第二套实现。
 * </p>
 * <p>
 * JS 只暴露 {@code host.exec({ tool, args })}；<b>不接受任何 shell 字符串</b>，
 * args 传字符串会被直接拒绝而不是按 shell 解析。
 * </p>
 */
public final class HostCodeCapability {

    private final AgentContext agentContext;
    private final CodeModeExecutionBudget budget;
    private final int maxOutputChars;
    private final List<String> availableTools;

    public HostCodeCapability(AgentContext agentContext,
                              CodeModeExecutionBudget budget,
                              int maxOutputChars,
                              List<String> availableTools) {
        this.agentContext = agentContext;
        this.budget = budget;
        this.maxOutputChars = maxOutputChars > 0 ? maxOutputChars : 1_048_576;
        this.availableTools = availableTools == null ? List.of() : List.copyOf(availableTools);
    }

    /** 按当前装配解析可用 CLI 名单。 */
    public static List<String> resolveAvailableTools(ReactorRuntimeDependencies deps) {
        if (deps == null) {
            return List.of();
        }
        HostCliProperties properties = deps.getHostCliProperties();
        CliExecutionPort cli = deps.getOptionalCliExecutionPort();
        if (properties == null || !properties.isEnabled() || cli == null
                || properties.getAllow() == null || properties.getAllow().isEmpty()) {
            return List.of();
        }
        List<String> available = new ArrayList<>();
        for (String tool : properties.getAllow()) {
            if (StringUtils.isNotBlank(tool) && cli.isResolvable(tool.trim())) {
                available.add(tool.trim());
            }
        }
        return available;
    }

    /**
     * JS: {@code await host.exec({ tool: "jq", args: ["-r", "."], timeoutMs: 60000 })}
     */
    public Map<String, Object> exec(Map<String, Object> args) {
        budget.beginCall(agentContext);
        Map<String, Object> params = args == null ? new LinkedHashMap<>() : new LinkedHashMap<>(args);
        String tool = params.get("tool") == null ? null : String.valueOf(params.get("tool")).trim();
        if (StringUtils.isBlank(tool)) {
            throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_INVALID_ARGUMENT,
                    "host.exec 需要 tool");
        }
        if (params.get("args") instanceof String) {
            throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_CAPABILITY_DENIED,
                    "host.exec 只接受 argv 数组，不接受 shell 字符串");
        }
        List<String> argv = HostCliPolicy.stringList(params.get("args"));
        if (!HostCliPolicy.isAllowed(tool, availableTools)) {
            throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_CAPABILITY_DENIED,
                    "不允许的 CLI: " + tool + "，可用：" + String.join(", ", availableTools));
        }
        String deniedCommand = HostCliPolicy.deniedSocialCommand(tool, argv);
        if (deniedCommand != null) {
            throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_CAPABILITY_DENIED,
                    "不允许的只读 CLI 操作: " + tool + " " + deniedCommand);
        }
        if (availableTools.isEmpty()) {
            throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_CAPABILITY_UNAVAILABLE,
                    "host_cli 未装配可用 CLI");
        }

        long requested = params.get("timeoutMs") instanceof Number number
                ? number.longValue()
                : (params.get("timeout_ms") instanceof Number fallback ? fallback.longValue() : 0L);
        long remaining = budget.remainingMs();
        long timeoutMs = requested > 0 ? Math.min(requested, remaining) : remaining;
        Map<String, Object> toolParams = new LinkedHashMap<>();
        toolParams.put("tool", tool);
        toolParams.put("args", argv);
        toolParams.put("timeout_ms", timeoutMs);

        ToolArtifactSource source = agentContext.getCurrentToolArtifactSource();
        String parentToolCallId = source == null ? null : source.getToolCallId();
        LocalDateTime startedAt = LocalDateTime.now();
        Long invocationId = CodeModeInvocationRecorder.begin(
                agentContext, parentToolCallId, CodeModeInvocationRecorder.TOOL_HOST,
                CodeModeInvocationRecorder.summarize(toolParams), startedAt);
        try {
            HostCliTool hostCliTool = new HostCliTool(availableTools);
            hostCliTool.setAgentContext(agentContext);
            Map<String, Object> fields = invoke(hostCliTool, toolParams);
            CodeModeInvocationRecorder.end(agentContext, invocationId, CodeModeInvocationRecorder.TOOL_HOST,
                    fields.get("ok") == Boolean.TRUE
                            ? ExecutionLedgerConstants.STATUS_SUCCESS
                            : ExecutionLedgerConstants.STATUS_FAILED,
                    summarize(fields), null, LocalDateTime.now());
            return fields;
        } catch (CodeModeExecutionException e) {
            CodeModeInvocationRecorder.end(agentContext, invocationId, CodeModeInvocationRecorder.TOOL_HOST,
                    ExecutionLedgerConstants.STATUS_FAILED, null, e.getMessage(), LocalDateTime.now());
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invoke(HostCliTool tool, Map<String, Object> params) {
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
}
