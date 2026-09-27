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
                "description", "超时毫秒，默认 60000"
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
        String visitorId = StringUtils.trimToNull(agentContext.getVisitorId());
        if (visitorId == null) {
            return ToolResultPayload.failure("缺少访客身份", "缺少访客身份", null, "missing visitorId");
        }
        BrowserRelayPort relay = deps.getOptionalBrowserRelayPort();
        if (relay == null || !relay.isOnline(visitorId)) {
            return ToolResultPayload.failure("浏览器未连接", "浏览器未连接", null, "browser_offline");
        }
        CliExecutionPort cli = deps.getOptionalCliExecutionPort();
        OpenCliProperties properties = deps.getOpenCliProperties();
        if (cli == null || properties == null || !properties.isEnabled()) {
            return ToolResultPayload.failure("opencli 未配置", "opencli 未配置", null, "opencli_unavailable");
        }
        List<String> rewritten = OpenCliArgv.rewrite(args, visitorId, properties.getExtraArgs());
        List<String> processArgs = new ArrayList<>();
        if (properties.getPrefixArgs() != null) {
            for (String prefix : properties.getPrefixArgs()) {
                if (StringUtils.isNotBlank(prefix)) {
                    processArgs.add(prefix);
                }
            }
        }
        processArgs.addAll(rewritten);
        long timeoutMs = resolveTimeout(params.get("timeout_ms"), properties);
        Map<String, String> env = new LinkedHashMap<>();
        env.put("OPENCLI_RELAY_URL", resolveRelayUrl(properties, deps));
        env.put("OPENCLI_RELAY_VISITOR_ID", visitorId);
        String secret = resolveSecret(deps);
        if (StringUtils.isNotBlank(secret)) {
            env.put("OPENCLI_RELAY_SECRET", secret);
        }
        env.put("OPENCLI_CACHE_DIR", resolveCacheDir(properties, visitorId));
        env.put("OPENCLI_SITE_SESSION", "persistent");
        CliResult result = cli.exec(CliInvocation.builder()
                .tool(properties.getCommand())
                .args(processArgs)
                .cwd(agentContext.getWorkspaceRoot())
                .env(env)
                .timeoutMs(timeoutMs)
                .capture("both")
                .maxOutputChars(properties.getMaxOutputChars())
                .build());
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

    private static String resolveCacheDir(OpenCliProperties properties, String visitorId) {
        String root = StringUtils.trimToNull(properties.getCacheDir());
        if (root == null) {
            root = Path.of(System.getProperty("java.io.tmpdir"), "reactor-opencli").toString();
        }
        return Path.of(root, visitorId).toString();
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
