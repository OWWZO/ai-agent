package org.wwz.ai.domain.agent.runtime.tool.common.shell;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bash / PowerShell 公共逻辑（对标 cc-haha shell 工具的同步执行路径）。
 */
@Slf4j
@Data
public abstract class AbstractShellTool implements BaseTool {

    protected static final long DEFAULT_TIMEOUT_MS = 120_000L;
    protected static final long MAX_TIMEOUT_MS = 600_000L;
    protected static final int MAX_OBSERVATION_CHARS = 30_000;

    protected AgentContext agentContext;

    protected abstract List<String> buildProcessCommand(String command);

    protected abstract String shellLabel();

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> command = new LinkedHashMap<>();
        command.put("type", "string");
        command.put("description", shellLabel() + " command to execute");

        Map<String, Object> timeout = new LinkedHashMap<>();
        timeout.put("type", "integer");
        timeout.put("description", "Optional timeout in milliseconds (default 120000, max 600000)");

        Map<String, Object> description = new LinkedHashMap<>();
        description.put("type", "string");
        description.put("description", "Clear, concise description of what this command does");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("command", command);
        properties.put("timeout", timeout);
        properties.put("description", description);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("command"));
        return parameters;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        try {
            Map<String, Object> params = coerceMap(input);
            String command = StringUtils.trimToEmpty(valueAsString(params.get("command")));
            if (StringUtils.isBlank(command)) {
                return ToolResultPayload.failure(
                        shellLabel() + " 失败：command 不能为空",
                        shellLabel() + " 失败：command 不能为空",
                        null,
                        "missing command");
            }

            long timeoutMs = resolveTimeoutMs(params.get("timeout"));
            Path cwd = resolveWorkingDirectory();
            List<String> processCommand = buildProcessCommand(command);
            log.info("{} {} execute cwd={}, timeoutMs={}, command={}",
                    requestId(), getName(), cwd, timeoutMs, abbreviate(command, 200));

            ShellExecResult result = ShellProcessExecutor.execute(processCommand, cwd, timeoutMs);
            return buildPayload(command, result);
        } catch (Exception e) {
            log.error("{} {} execute error, input={}", requestId(), getName(), input, e);
            String msg = shellLabel() + " 执行失败：" + StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName());
            return ToolResultPayload.failure(msg, msg, null, e.getMessage());
        }
    }

    protected ToolResultPayload buildPayload(String command, ShellExecResult result) {
        StringBuilder body = new StringBuilder();
        body.append("Command: ").append(command).append('\n');
        body.append("Exit code: ").append(result.getExitCode()).append('\n');
        body.append("Duration: ").append(result.getDurationMs()).append("ms\n");
        if (result.isTimedOut()) {
            body.append("Status: timed out\n");
        }
        String stdout = StringUtils.defaultString(result.getStdout()).trim();
        String stderr = StringUtils.defaultString(result.getStderr()).trim();
        if (StringUtils.isNotBlank(stdout)) {
            body.append("\nstdout:\n").append(stdout);
        }
        if (StringUtils.isNotBlank(stderr)) {
            body.append("\nstderr:\n").append(stderr);
        }
        if (StringUtils.isBlank(stdout) && StringUtils.isBlank(stderr)) {
            body.append("\n(No output)");
        }

        String observation = truncate(body.toString(), MAX_OBSERVATION_CHARS);
        boolean failed = result.isTimedOut() || result.getExitCode() != 0;
        if (failed) {
            return ToolResultPayload.failure(observation, observation, null,
                    result.isTimedOut() ? "timeout" : "exit_code_" + result.getExitCode());
        }
        return ToolResultPayload.text(observation);
    }

    protected Path resolveWorkingDirectory() {
        if (agentContext != null && StringUtils.isNotBlank(agentContext.getWorkspaceRoot())) {
            Path root = Path.of(agentContext.getWorkspaceRoot()).toAbsolutePath().normalize();
            if (Files.isDirectory(root)) {
                return root;
            }
        }
        return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

    protected long resolveTimeoutMs(Object raw) {
        Long value = valueAsLong(raw);
        if (value == null || value <= 0) {
            return DEFAULT_TIMEOUT_MS;
        }
        return Math.min(value, MAX_TIMEOUT_MS);
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> coerceMap(Object input) {
        if (input instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    protected String valueAsString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    protected Long valueAsLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    protected String requestId() {
        return agentContext == null ? "unknown" : StringUtils.defaultString(agentContext.getRequestId(), "unknown");
    }

    protected static String truncate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "\n...[truncated]";
    }

    protected static String abbreviate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "...";
    }
}
