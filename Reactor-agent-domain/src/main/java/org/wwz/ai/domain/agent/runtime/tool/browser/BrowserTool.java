package org.wwz.ai.domain.agent.runtime.tool.browser;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.adapter.port.BrowserRelayPort;
import org.wwz.ai.domain.agent.adapter.port.cli.CliExecutionPort;
import org.wwz.ai.domain.agent.adapter.port.cli.CliInvocation;
import org.wwz.ai.domain.agent.adapter.port.cli.CliResult;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.cli.OpenCliArgv;
import org.wwz.ai.types.agent.config.OpenCliProperties;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Setter
public class BrowserTool implements BaseTool {

    public static final String TOOL_NAME = "browser";

    private AgentContext agentContext;

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "远程操作用户的浏览器。";
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("args", Map.of(
                "type", "array",
                "items", Map.of("type", "string"),
                "description", "CLI argv 参数数组。先用 list 查看可用命令，再用 <site> --help 或 <site> <command> --help 查询参数；每项命令行参数分别传入。"
        ));
        properties.put("timeout_ms", Map.of(
                "type", "integer",
                "description", "超时毫秒，默认 180000"
        ));
        return Map.of(
                "type", "object",
                "properties", properties,
                "required", List.of("args")
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        Map<String, Object> params = input instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : new LinkedHashMap<>();
        List<String> args = stringList(params.get("args"));
        if (args.isEmpty()) {
            return ToolResultPayload.failure("args 不能为空", "args 不能为空", null, "missing args");
        }
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            return ToolResultPayload.failure("运行时未装配", "运行时未装配", null, "runtime_unavailable");
        }
        ReactorRuntimeDependencies deps = agentContext.getRuntimeDependencies();
        String userId = StringUtils.trimToNull(agentContext.getUserId());
        if (userId == null) {
            return ToolResultPayload.failure("缺少用户身份", "缺少用户身份", null, "missing userId");
        }
        CliExecutionPort cli = deps.getOptionalCliExecutionPort();
        OpenCliProperties properties = deps.getOpenCliProperties();
        if (cli == null || properties == null || !properties.isEnabled()) {
            return ToolResultPayload.failure("opencli 未配置", "opencli 未配置", null, "opencli_unavailable");
        }
        long budgetMs = resolveTimeout(params.get("timeout_ms"), properties);
        long started = System.nanoTime();
        Execution execution = executeCli(deps, args, userId, cli, properties, budgetMs, started);
        if (execution.failure != null) {
            return execution.failure;
        }
        CliResult result = execution.result;
        List<String> rewritten = execution.rewritten;
        Map<String, Object> fields = toFields(result);
        publishViewport(result.getStdout(), fields);
        boolean sensitive = OpenCliArgv.isSensitive(rewritten);
        if (sensitive) {
            Map<String, Object> redacted = new LinkedHashMap<>();
            redacted.put("redacted", OpenCliArgv.sensitiveKind(rewritten));
            redacted.put("exit_code", result.getExitCode());
            redacted.put("stdout_chars", result.getStdout() == null ? 0 : result.getStdout().length());
            redacted.put("timed_out", result.isTimedOut());
            return ToolResultPayload.builder()
                    .toolResult(JSON.toJSONString(redacted))
                    .llmObservation(JSON.toJSONString(fields))
                    .llmData(fields)
                    .ledgerObservation(JSON.toJSONString(redacted))
                    .failed(Boolean.FALSE)
                    .build();
        }
        if (!result.isOk()) {
            return ToolResultPayload.fromData(fields);
        }
        return ToolResultPayload.okData(TOOL_NAME, fields);
    }

    private Execution executeCli(ReactorRuntimeDependencies deps,
                                       List<String> args,
                                       String userId,
                                       CliExecutionPort cli,
                                       OpenCliProperties properties,
                                       long budgetMs,
                                       long started) {
        long remaining = remainingMillis(budgetMs, started);
        if (remaining <= 0) {
            return Execution.failed(browserTimeout());
        }
        BrowserRelayPort relay = deps.getOptionalBrowserRelayPort();
        if (relay == null || !relay.isOnline(userId)) {
            return Execution.failed(ToolResultPayload.failure(
                    "浏览器未连接", "浏览器未连接", null, "browser_offline"));
        }
        List<String> rewritten = OpenCliArgv.rewrite(args, properties.getExtraArgs());
        List<String> processArgs = new ArrayList<>();
        if (properties.getPrefixArgs() != null) {
            for (String prefix : properties.getPrefixArgs()) {
                if (StringUtils.isNotBlank(prefix)) {
                    processArgs.add(prefix);
                }
            }
        }
        processArgs.addAll(rewritten);
        Map<String, String> env = new LinkedHashMap<>();
        env.put("OPENCLI_RELAY_URL", resolveRelayUrl(properties, deps));
        env.put("OPENCLI_RELAY_VISITOR_ID", userId);
        String secret = resolveSecret(deps);
        if (StringUtils.isNotBlank(secret)) {
            env.put("OPENCLI_RELAY_SECRET", secret);
        }
        env.put("OPENCLI_CACHE_DIR", resolveCacheDir(properties, userId));
        CliResult result = cli.exec(CliInvocation.builder()
                .tool(properties.getCommand())
                .args(processArgs)
                .cwd(agentContext.getWorkspaceRoot())
                .env(env)
                .timeoutMs(remaining)
                .capture("both")
                .maxOutputChars(properties.getMaxOutputChars())
                .build());
        return Execution.completed(result, rewritten);
    }

    private static ToolResultPayload browserTimeout() {
        return ToolResultPayload.failure("浏览器操作超时", "浏览器操作超时", null, "browser_timeout");
    }

    private static long remainingMillis(long budgetMs, long startedNanos) {
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
        return budgetMs - Math.max(0L, elapsed);
    }

    private static final class Execution {
        private final ToolResultPayload failure;
        private final CliResult result;
        private final List<String> rewritten;

        private Execution(ToolResultPayload failure, CliResult result, List<String> rewritten) {
            this.failure = failure;
            this.result = result;
            this.rewritten = rewritten;
        }

        private static Execution failed(ToolResultPayload failure) {
            return new Execution(failure, null, List.of());
        }

        private static Execution completed(CliResult result, List<String> rewritten) {
            return new Execution(null, result, rewritten);
        }
    }

    private void publishViewport(String stdout, Map<String, Object> fields) {
        Map<String, Object> payload = extractViewport(stdout);
        if (payload.isEmpty()) {
            return;
        }
        if (payload.get("tree") != null || payload.get("url") != null) {
            BrowserViewportPublisher.emitTree(agentContext, payload);
            if (payload.get("tree") != null) {
                fields.put("tree", payload.get("tree"));
            }
        }
        Object image = payload.get("imageBase64");
        if (image != null) {
            String artifactRef = BrowserViewportPublisher.uploadPng(agentContext, String.valueOf(image));
            if (artifactRef != null) {
                fields.put("artifactRef", artifactRef);
            }
            fields.remove("imageBase64");
            fields.put("message", "Viewport attached as image media; inspect the screenshot, do not fetch artifactRef.");
        }
    }

    private static Map<String, Object> extractViewport(String stdout) {
        if (StringUtils.isBlank(stdout)) {
            return Map.of();
        }
        String trimmed = stdout.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return Map.of();
        }
        try {
            JSONObject json = JSON.parseObject(trimmed.substring(start, end + 1));
            if (json == null) {
                return Map.of();
            }
            Map<String, Object> fields = new LinkedHashMap<>(json);
            if (json.get("data") instanceof Map<?, ?> nested) {
                for (Map.Entry<?, ?> entry : nested.entrySet()) {
                    if (entry.getKey() != null && !fields.containsKey(String.valueOf(entry.getKey()))) {
                        fields.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
            }
            return fields;
        } catch (RuntimeException ignore) {
            return Map.of();
        }
    }

    private static Map<String, Object> toFields(CliResult result) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("ok", result.isOk());
        fields.put("tool", result.getTool());
        fields.put("exit_code", result.getExitCode());
        fields.put("stdout", result.getStdout());
        fields.put("stderr", result.getStderr());
        fields.put("duration_ms", result.getDurationMs());
        fields.put("timed_out", result.isTimedOut());
        fields.put("truncated", result.isTruncated());
        return fields;
    }

    private static long resolveTimeout(Object raw, OpenCliProperties properties) {
        long fallback = properties.getDefaultTimeoutMs() <= 0 ? 60_000L : properties.getDefaultTimeoutMs();
        long max = properties.getMaxTimeoutMs() <= 0 ? 180_000L : properties.getMaxTimeoutMs();
        if (raw instanceof Number number) {
            long value = number.longValue();
            if (value > 0) {
                return Math.min(value, max);
            }
        }
        if (raw instanceof String text && StringUtils.isNumeric(text.trim())) {
            long value = Long.parseLong(text.trim());
            if (value > 0) {
                return Math.min(value, max);
            }
        }
        return Math.min(fallback, max);
    }

    private static String resolveRelayUrl(OpenCliProperties properties, ReactorRuntimeDependencies deps) {
        if (StringUtils.isNotBlank(properties.getRelayUrl())) {
            return properties.getRelayUrl().trim();
        }
        String port = "8100";
        if (deps.getEnvironment() != null) {
            port = deps.getEnvironment().getProperty("local.server.port",
                    deps.getEnvironment().getProperty("server.port", "8100"));
        }
        return "http://127.0.0.1:" + port + "/internal/browser/rpc";
    }

    private static String resolveSecret(ReactorRuntimeDependencies deps) {
        if (deps.getEnvironment() != null) {
            return StringUtils.defaultString(deps.getEnvironment().getProperty("reactor.browser-relay.internal-rpc-secret"));
        }
        return "";
    }

    private static String resolveCacheDir(OpenCliProperties properties, String userId) {
        String root = StringUtils.trimToNull(properties.getCacheDir());
        if (root == null) {
            root = Path.of(System.getProperty("java.io.tmpdir"), "reactor-opencli").toString();
        }
        return Path.of(root, userId).toString();
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<String> values = new ArrayList<>(list.size());
        for (Object item : list) {
            if (!(item instanceof String text) || text == null) {
                return List.of();
            }
            values.add(text);
        }
        return values;
    }
}
