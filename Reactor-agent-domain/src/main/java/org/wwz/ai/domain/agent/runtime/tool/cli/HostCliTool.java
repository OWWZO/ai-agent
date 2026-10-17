package org.wwz.ai.domain.agent.runtime.tool.cli;

import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.adapter.port.cli.CliExecutionPort;
import org.wwz.ai.domain.agent.adapter.port.cli.CliInvocation;
import org.wwz.ai.domain.agent.adapter.port.cli.CliResult;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.types.agent.config.HostCliProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Setter
public class HostCliTool implements BaseTool {

    public static final String TOOL_NAME = "host_cli";

    private final List<String> availableTools;
    private AgentContext agentContext;

    public HostCliTool(List<String> availableTools) {
        this.availableTools = availableTools == null ? List.of() : List.copyOf(availableTools);
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "在会话 workspace 里调用宿主机已装 CLI，不经 shell。当前可用：" + String.join(", ", availableTools)
                + "。cwd 固定为会话 workspace；twitter 和 rdt 仅允许只读命令。";
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("tool", Map.of(
                "type", "string",
                "description", "二进制名，必须是：" + String.join(", ", availableTools),
                "enum", availableTools
        ));
        properties.put("args", Map.of(
                "type", "array",
                "items", Map.of("type", "string"),
                "description", "argv 数组，禁止整段 shell"
        ));
        properties.put("timeout_ms", Map.of(
                "type", "integer",
                "description", "超时毫秒"
        ));
        return Map.of(
                "type", "object",
                "properties", properties,
                "required", List.of("tool", "args")
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        Map<String, Object> params = input instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : new LinkedHashMap<>();
        String tool = params.get("tool") == null ? null : String.valueOf(params.get("tool")).trim();
        List<String> args = HostCliPolicy.stringList(params.get("args"));
        if (StringUtils.isBlank(tool)) {
            return ToolResultPayload.failure("tool 不能为空", "tool 不能为空", null, "missing tool");
        }
        if (!HostCliPolicy.isAllowed(tool, availableTools)) {
            return ToolResultPayload.failure("不允许的 CLI: " + tool, "不允许的 CLI: " + tool, null, "host_cli_denied");
        }
        String deniedCommand = HostCliPolicy.deniedSocialCommand(tool, args);
        if (deniedCommand != null) {
            return ToolResultPayload.failure(
                    "不允许的只读 CLI 操作: " + tool + " " + deniedCommand,
                    "不允许的只读 CLI 操作: " + tool + " " + deniedCommand,
                    null,
                    "host_cli_denied");
        }
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            return ToolResultPayload.failure("运行时未装配", "运行时未装配", null, "runtime_unavailable");
        }
        ReactorRuntimeDependencies deps = agentContext.getRuntimeDependencies();
        CliExecutionPort cli = deps.getOptionalCliExecutionPort();
        HostCliProperties properties = deps.getHostCliProperties();
        if (cli == null || properties == null || !properties.isEnabled() || !cli.isResolvable(tool)) {
            return ToolResultPayload.failure("CLI 不可用: " + tool, "CLI 不可用: " + tool, null, "host_cli_unavailable");
        }
        String cwd = agentContext.getWorkspaceRoot();
        long timeoutMs = HostCliPolicy.resolveTimeout(
                params.get("timeout_ms"), properties.getDefaultTimeoutMs(), properties.getMaxTimeoutMs());
        String proxy = StringUtils.trimToNull(deps.requireReactorConfig().getWebFetchProxy());
        String captureId = StringUtils.defaultIfBlank(agentContext.getRequestId(), "host-cli");
        try (CliArtifactCaptureLease ignored =
                     CliArtifactCaptureCoordinator.acquireWorkspaceDiffLease(cwd, captureId)) {
            // 前后快照只是 host_cli 的兼容兜底；workspace 级 lease 防止并发调用互相捕获文件。
            Set<String> before = CliArtifactMaterializer.snapshotWorkspace(cwd);
            CliResult result;
            try {
                result = cli.exec(CliInvocation.builder()
                        .tool(tool)
                        .args(args)
                        .cwd(cwd)
                        .env(HostCliPolicy.proxyEnvironment(proxy))
                        .unsetEnv(HostCliPolicy.proxyUnsetVariables(proxy))
                        .timeoutMs(timeoutMs)
                        .capture("both")
                        .maxOutputChars(properties.getMaxOutputChars())
                        .build());
            } catch (RuntimeException e) {
                CliArtifactMaterialization partial = CliArtifactMaterializer.materialize(
                        CliArtifactMaterializeCommand.builder()
                                .agentContext(agentContext)
                                .workspaceRoot(cwd)
                                .commandLabel("host_cli 执行产物")
                                .baselineFiles(before)
                                .build());
                return cliFailure(partial);
            }
            if (result == null) {
                return ToolResultPayload.failure("CLI 未返回结果", "CLI 未返回结果", null, "host_cli_execution_failed");
            }
            CliArtifactMaterialization materialization = CliArtifactMaterializer.materialize(
                    CliArtifactMaterializeCommand.builder()
                            .agentContext(agentContext)
                            .workspaceRoot(cwd)
                            .reportedArtifacts(result.getArtifacts())
                            .commandLabel("host_cli 执行产物")
                            .baselineFiles(before)
                            .build());
            return buildResult(tool, result, materialization);
        } catch (CliArtifactCaptureCoordinator.CliArtifactCaptureException e) {
            return ToolResultPayload.failure("artifact capture 失败", "artifact capture 失败", null, e.getCode());
        }
    }

    private static ToolResultPayload buildResult(String tool,
                                                 CliResult result,
                                                 CliArtifactMaterialization materialization) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("ok", result.isOk());
        fields.put("tool", tool);
        fields.put("exit_code", result.getExitCode());
        fields.put("stdout", result.getStdout());
        fields.put("stderr", result.getStderr());
        fields.put("duration_ms", result.getDurationMs());
        fields.put("timed_out", result.isTimedOut());
        fields.put("truncated", result.isTruncated());
        if (!materialization.getArtifacts().isEmpty()) {
            fields.put("artifacts", materialization.artifactMaps());
        }
        if (!materialization.getFailures().isEmpty()) {
            fields.put("artifact_failures", materialization.failureMaps());
        }
        return result.isOk()
                ? ToolResultPayload.okData(TOOL_NAME, fields)
                : ToolResultPayload.fromData(fields);
    }

    private static ToolResultPayload cliFailure(CliArtifactMaterialization materialization) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("error", "opencli 执行失败");
        if (materialization != null && !materialization.getArtifacts().isEmpty()) {
            detail.put("artifacts", materialization.artifactMaps());
        }
        if (materialization != null && !materialization.getFailures().isEmpty()) {
            detail.put("artifact_failures", materialization.failureMaps());
        }
        return ToolResultPayload.builder()
                .toolResult("opencli 执行失败")
                .llmData(detail)
                .failed(Boolean.TRUE)
                .errorMsg("opencli_execution_failed")
                .build();
    }

}
