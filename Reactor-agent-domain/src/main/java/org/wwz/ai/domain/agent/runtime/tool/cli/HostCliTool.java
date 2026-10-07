package org.wwz.ai.domain.agent.runtime.tool.cli;

import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.adapter.port.FileArtifactPort;
import org.wwz.ai.domain.agent.adapter.port.cli.CliArtifact;
import org.wwz.ai.domain.agent.adapter.port.cli.CliExecutionPort;
import org.wwz.ai.domain.agent.adapter.port.cli.CliInvocation;
import org.wwz.ai.domain.agent.adapter.port.cli.CliResult;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.dto.FileRequest;
import org.wwz.ai.domain.agent.runtime.dto.FileResponse;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.types.agent.config.HostCliProperties;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@Setter
public class HostCliTool implements BaseTool {

    public static final String TOOL_NAME = "host_cli";
    private static final Set<String> TWITTER_WRITE_COMMANDS = Set.of(
            "bookmark", "delete", "favorite", "follow", "like", "post", "quote",
            "reply", "retweet", "unbookmark", "unfavorite", "unfollow", "unlike", "unretweet");
    private static final Set<String> REDDIT_WRITE_COMMANDS = Set.of(
            "comment", "login", "logout", "save", "subscribe", "upvote");
    private static final List<String> PROXY_ENVIRONMENT_VARIABLES = List.of(
            "HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "http_proxy", "https_proxy", "all_proxy");

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
        List<String> args = stringList(params.get("args"));
        if (StringUtils.isBlank(tool)) {
            return ToolResultPayload.failure("tool 不能为空", "tool 不能为空", null, "missing tool");
        }
        if (!availableTools.contains(tool)) {
            return ToolResultPayload.failure("不允许的 CLI: " + tool, "不允许的 CLI: " + tool, null, "host_cli_denied");
        }
        String deniedCommand = deniedSocialCommand(tool, args);
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
        Set<String> before = snapshot(cwd);
        long timeoutMs = resolveTimeout(params.get("timeout_ms"), properties);
        String proxy = StringUtils.trimToNull(deps.requireReactorConfig().getWebFetchProxy());
        CliResult result = cli.exec(CliInvocation.builder()
                .tool(tool)
                .args(args)
                .cwd(cwd)
                .env(proxyEnvironment(proxy))
                .unsetEnv(proxy == null ? PROXY_ENVIRONMENT_VARIABLES : List.of())
                .timeoutMs(timeoutMs)
                .capture("both")
                .maxOutputChars(properties.getMaxOutputChars())
                .build());
        List<CliArtifact> artifacts = registerNewFiles(cwd, before);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("ok", result.isOk());
        fields.put("tool", tool);
        fields.put("exit_code", result.getExitCode());
        fields.put("stdout", result.getStdout());
        fields.put("stderr", result.getStderr());
        fields.put("duration_ms", result.getDurationMs());
        fields.put("timed_out", result.isTimedOut());
        fields.put("truncated", result.isTruncated());
        if (!artifacts.isEmpty()) {
            fields.put("artifacts", artifacts);
        }
        return result.isOk()
                ? ToolResultPayload.okData(TOOL_NAME, fields)
                : ToolResultPayload.fromData(fields);
    }

    private static Map<String, String> proxyEnvironment(String proxy) {
        if (proxy == null) {
            return Map.of();
        }
        return Map.of(
                "HTTP_PROXY", proxy,
                "HTTPS_PROXY", proxy,
                "ALL_PROXY", proxy,
                "http_proxy", proxy,
                "https_proxy", proxy,
                "all_proxy", proxy);
    }

    private List<CliArtifact> registerNewFiles(String cwd, Set<String> before) {
        if (StringUtils.isBlank(cwd) || agentContext == null || agentContext.getRuntimeDependencies() == null) {
            return List.of();
        }
        FileArtifactPort fileArtifactPort;
        try {
            fileArtifactPort = agentContext.getRuntimeDependencies().requireFileArtifactPort();
        } catch (RuntimeException ignore) {
            return List.of();
        }
        List<CliArtifact> artifacts = new ArrayList<>();
        List<Map<String, Object>> eventFileInfo = new ArrayList<>();
        for (String path : snapshot(cwd)) {
            if (before.contains(path)) {
                continue;
            }
            Path file = Path.of(path);
            if (!Files.isRegularFile(file)) {
                continue;
            }
            try {
                String serviceUrl = agentContext.getRuntimeDependencies().requireReactorConfig().getCodeInterpreterUrl();
                FileRequest request = FileRequest.builder()
                        .requestId(StringUtils.defaultIfBlank(agentContext.getSessionId(), agentContext.getRequestId()))
                        .fileName(file.getFileName().toString())
                        .description(file.getFileName().toString())
                        .localPath(file.toAbsolutePath().toString())
                        .build();
                FileResponse response = fileArtifactPort.register(serviceUrl, request);
                if (response == null) {
                    continue;
                }
                ToolArtifactSource source = agentContext.getCurrentToolArtifactSource();
                if (source != null) {
                    agentContext.registerGeneratedArtifact(source, File.builder()
                            .fileName(file.getFileName().toString())
                            .ossUrl(response.getOssUrl())
                            .domainUrl(response.getDomainUrl())
                            .fileSize(response.getFileSize())
                            .description(file.getFileName().toString())
                            .isInternalFile(false)
                            .build());
                }
                artifacts.add(CliArtifact.builder()
                        .path(file.toAbsolutePath().toString())
                        .size(response.getFileSize() == null ? Files.size(file) : response.getFileSize().longValue())
                        .mime(Files.probeContentType(file))
                        .build());
                Map<String, Object> eventFile = new LinkedHashMap<>();
                eventFile.put("fileName", file.getFileName().toString());
                eventFile.put("ossUrl", response.getOssUrl());
                eventFile.put("domainUrl", response.getDomainUrl());
                eventFileInfo.add(eventFile);
            } catch (Exception ignore) {
            }
        }
        if (!eventFileInfo.isEmpty() && agentContext.getPrinter() != null) {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("command", "host_cli 执行产物");
            event.put("fileInfo", eventFileInfo);
            agentContext.getPrinter().send("file", event, null);
        }
        return artifacts;
    }

    private static Set<String> snapshot(String cwd) {
        if (StringUtils.isBlank(cwd)) {
            return Set.of();
        }
        Path root = Path.of(cwd);
        if (!Files.isDirectory(root)) {
            return Set.of();
        }
        Set<String> files = new LinkedHashSet<>();
        try (Stream<Path> walk = Files.walk(root, 4)) {
            walk.filter(Files::isRegularFile).forEach(path -> files.add(path.toAbsolutePath().normalize().toString()));
        } catch (Exception ignore) {
        }
        return files;
    }

    private static long resolveTimeout(Object raw, HostCliProperties properties) {
        long fallback = properties.getDefaultTimeoutMs() <= 0 ? 120_000L : properties.getDefaultTimeoutMs();
        long max = properties.getMaxTimeoutMs() <= 0 ? 300_000L : properties.getMaxTimeoutMs();
        if (raw instanceof Number number && number.longValue() > 0) {
            return Math.min(number.longValue(), max);
        }
        return Math.min(fallback, max);
    }

    private static List<String> stringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> values = new ArrayList<>(list.size());
        for (Object item : list) {
            if (!(item instanceof String text)) {
                return List.of();
            }
            values.add(text);
        }
        return values;
    }

    private static String deniedSocialCommand(String tool, List<String> args) {
        if (!("twitter".equals(tool) || "rdt".equals(tool))) {
            return null;
        }
        for (String arg : args) {
            if (arg == null || arg.isBlank()) {
                continue;
            }
            String normalized = arg.toLowerCase(Locale.ROOT);
            if (normalized.startsWith("-")) {
                continue;
            }
            if ("twitter".equals(tool) && TWITTER_WRITE_COMMANDS.contains(normalized)) {
                return normalized;
            }
            if ("rdt".equals(tool) && REDDIT_WRITE_COMMANDS.contains(normalized)) {
                return normalized;
            }
            return null;
        }
        return null;
    }
}
