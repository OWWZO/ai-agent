package org.wwz.ai.domain.agent.runtime.tool.codemode;

import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.types.agent.config.CodeModeProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code code_mode}：让 Agent 写一段 JavaScript，在当前 JVM 内编排 browser / host / workspace。
 * <p>
 * 本类只做参数解析、校验和结果转换，<b>不 import 任何 GraalJS 类型</b>；
 * 真正的执行委托给 {@link CodeModeExecutorPort}（实现在 infrastructure）。
 * </p>
 */
@Setter
public class CodeModeTool implements BaseTool {

    public static final String TOOL_NAME = "code_mode";

    private final CodeModeProperties properties;

    private AgentContext agentContext;

    private CodeModeExecutorPort executorPort;

    public CodeModeTool(CodeModeProperties properties) {
        this.properties = properties;
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "在当前 JVM 内执行一段 JavaScript，用于把 browser、host、workspace 的多步调用编排成一次执行。"
                + "入口固定为 async function main({ browser, host, workspace, input })。"
                + "source 与 workspaceFile 二选一：source 直接传源码，workspaceFile 执行会话 workspace 内已存在的 .js 脚本（只允许 scripts/ 与 jobs/）。"
                + "脚本内不能 require / import，不能访问 Java 类、文件系统和进程；"
                + "浏览器和 CLI 只能经由 browser.command / host.exec 调用，host 只接受白名单 CLI 的 argv。"
                + "已有的 Bash 工具是用来操作云端沙箱的，这个工具是用 JavaScript 来替代 本地Bash 的执行能力，所以本地Bash怎么调用这些CLI，JavaScript就怎么写。"
                ;
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("source", Map.of(
                "type", "string",
                "description", "JavaScript 源码，必须包含 async function main(context)。与 workspaceFile 二选一。"
        ));
        properties.put("workspaceFile", Map.of(
                "type", "string",
                "description", "会话 workspace 内的 .js 脚本相对路径，只允许 scripts/ 或 jobs/ 下。与 source 二选一。"
        ));
        properties.put("inputs", Map.of(
                "type", "object",
                "description", "传入脚本的 JSON 对象，在 main(context) 中通过 context.input 读取。",
                "properties", Map.of(),
                "required", List.of()
        ));
        properties.put("timeoutMs", Map.of(
                "type", "integer",
                "description", "超时毫秒，默认 " + defaultTimeoutMs()
        ));
        // source / workspaceFile 二选一，故 required 为空；显式声明避免下游自动补齐。
        return Map.of(
                "type", "object",
                "properties", properties,
                "required", List.of()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        if (executorPort == null) {
            return fail(CodeModeErrorCode.CODE_MODE_CAPABILITY_UNAVAILABLE, "Code Mode 执行器未装配");
        }
        if (agentContext == null) {
            return fail(CodeModeErrorCode.CODE_MODE_INVALID_ARGUMENT, "缺少 AgentContext");
        }
        if (agentContext.isRunCancelled()) {
            return fail(CodeModeErrorCode.CODE_MODE_CANCELLED, "工具未执行：用户已停止本轮对话");
        }
        Map<String, Object> params = input instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : new LinkedHashMap<>();
        if (input != null && !(input instanceof Map<?, ?>)) {
            return fail(CodeModeErrorCode.CODE_MODE_INVALID_ARGUMENT, "参数必须是 JSON 对象");
        }

        String source = params.get("source") == null ? null : String.valueOf(params.get("source"));
        String workspaceFile = params.get("workspaceFile") == null ? null : String.valueOf(params.get("workspaceFile"));
        CodeModeRequestValidator.Validation validation = CodeModeRequestValidator.validateSourceTarget(
                source,
                workspaceFile,
                maxSourceChars(),
                scriptPrefixes());
        if (!validation.isValid()) {
            return fail(validation.errorCode(), validation.message());
        }
        Map<String, Object> inputs = CodeModeRequestValidator.normalizeInputs(params.get("inputs"));
        if (inputs == null) {
            return fail(CodeModeErrorCode.CODE_MODE_INVALID_ARGUMENT, "inputs 必须是 JSON 对象");
        }
        long timeoutMs = CodeModeRequestValidator.resolveTimeoutMs(
                params.get("timeoutMs"), defaultTimeoutMs(), maxTimeoutMs());

        CodeModeRequest request = CodeModeRequest.builder()
                .source(StringUtils.isNotBlank(source) ? source : null)
                .workspaceFile(StringUtils.isNotBlank(workspaceFile) ? workspaceFile.trim() : null)
                .inputs(inputs)
                .timeoutMs(timeoutMs)
                .build();

        CodeModeResult result;
        try {
            result = executorPort.execute(request, agentContext);
        } catch (RuntimeException e) {
            return fail(CodeModeErrorCode.CODE_MODE_RUNTIME_ERROR,
                    "Code Mode 执行失败：" + StringUtils.defaultString(e.getMessage(), e.getClass().getSimpleName()));
        }
        if (result == null) {
            return fail(CodeModeErrorCode.CODE_MODE_RUNTIME_ERROR, "Code Mode 执行器未返回结果");
        }
        return toPayload(result);
    }

    private Object toPayload(CodeModeResult result) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("ok", result.isSuccess());
        fields.put("duration_ms", result.getDurationMs());
        fields.put("tool_calls", result.getToolCallCount());
        if (!result.getProducedFiles().isEmpty()) {
            fields.put("produced_files", result.getProducedFiles());
        }
        if (StringUtils.isNotBlank(result.getSourceHash())) {
            fields.put("source_hash", result.getSourceHash());
        }
        if (!result.getLogs().isEmpty()) {
            fields.put("logs", result.getLogs());
        }
        if (result.isSuccess()) {
            fields.put("result", result.getResult());
            return ToolResultPayload.okData(TOOL_NAME, fields);
        }
        fields.put("error_code", result.getErrorCode());
        fields.put("message", result.getErrorMessage());
        return ToolResultPayload.failureFrom(
                "Code Mode 执行失败：" + StringUtils.defaultString(result.getErrorMessage(), result.getErrorCode()),
                envelope(fields));
    }

    private static Map<String, Object> envelope(Map<String, Object> fields) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("type", "tool_error");
        detail.put("tool", TOOL_NAME);
        detail.putAll(fields);
        return detail;
    }

    private static ToolResultPayload fail(CodeModeErrorCode code, String message) {
        return fail(code.code(), message);
    }

    private static ToolResultPayload fail(String errorCode, String message) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("type", "tool_error");
        detail.put("tool", TOOL_NAME);
        detail.put("error_code", errorCode);
        detail.put("message", message);
        return ToolResultPayload.failureFrom(message, detail);
    }

    private long defaultTimeoutMs() {
        return properties == null || properties.getDefaultTimeoutMs() <= 0
                ? 180_000L
                : properties.getDefaultTimeoutMs();
    }

    private long maxTimeoutMs() {
        return properties == null || properties.getMaxTimeoutMs() <= 0
                ? 540_000L
                : properties.getMaxTimeoutMs();
    }

    private int maxSourceChars() {
        return properties == null || properties.getMaxSourceChars() <= 0
                ? 262_144
                : properties.getMaxSourceChars();
    }

    private List<String> scriptPrefixes() {
        if (properties == null || properties.getWorkspace() == null
                || properties.getWorkspace().getAllowedScriptPrefixes() == null
                || properties.getWorkspace().getAllowedScriptPrefixes().isEmpty()) {
            return List.of("scripts/", "jobs/");
        }
        return properties.getWorkspace().getAllowedScriptPrefixes();
    }
}
