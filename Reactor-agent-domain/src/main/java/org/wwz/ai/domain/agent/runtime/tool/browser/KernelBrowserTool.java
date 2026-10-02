package org.wwz.ai.domain.agent.runtime.tool.browser;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.adapter.port.BrowserOperationKind;
import org.wwz.ai.domain.agent.adapter.port.BrowserOperationLockPort;
import org.wwz.ai.domain.agent.adapter.port.BrowserOperationLockTimeoutException;
import org.wwz.ai.domain.agent.adapter.port.KernelBrowserSession;
import org.wwz.ai.domain.agent.adapter.port.KernelBrowserSessionPort;
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
public class KernelBrowserTool implements BaseTool {

    public static final String TOOL_NAME = "kernel_browser";
    private static final String KERNEL_API_KEY = "KERNEL_API_KEY";
    private static final String REBUILT_NOTICE = "云端浏览器已重建，之前的页面状态已丢失。";

    private AgentContext agentContext;

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "远程操作当前用户的云端浏览器。";
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
        String ownerKey = StringUtils.trimToNull(agentContext.getVisitorId());
        if (ownerKey == null) {
            return ToolResultPayload.failure("缺少访客身份", "缺少访客身份", null, "missing visitorId");
        }

        ReactorRuntimeDependencies deps = agentContext.getRuntimeDependencies();
        KernelBrowserSessionPort sessionPort = deps.getOptionalKernelBrowserSessionPort();
        if (sessionPort == null || !sessionPort.isConfigured()) {
            return ToolResultPayload.failure("Kernel 云端浏览器未配置", "Kernel 云端浏览器未配置", null,
                    "kernel_browser_unavailable");
        }
        CliExecutionPort cli = deps.getOptionalCliExecutionPort();
        OpenCliProperties properties = deps.getOpenCliProperties();
        if (cli == null || properties == null || !properties.isEnabled()) {
            return ToolResultPayload.failure("opencli 未配置", "opencli 未配置", null, "opencli_unavailable");
        }
        BrowserOperationLockPort lock = deps.getOptionalBrowserOperationLockPort();
        if (lock == null) {
            return lockUnavailable();
        }

        long budgetMs = resolveTimeout(params.get("timeout_ms"), properties);
        long started = System.nanoTime();
        LockedExecution execution;
        try {
            execution = lock.execute(BrowserOperationKind.KERNEL_BROWSER, ownerKey, budgetMs, () ->
                    executeForOwner(args, ownerKey, sessionPort, cli, properties, budgetMs, started));
        } catch (BrowserOperationLockTimeoutException e) {
            return lockTimeout();
        }
        if (execution.failure != null) {
            return execution.failure;
        }
        KernelBrowserSession session = execution.session;
        CliResult result = execution.result;
        List<String> rewritten = execution.rewritten;
        Map<String, Object> fields = toFields(result, session.getCdpWsUrl());
        publishViewport(String.valueOf(fields.get("stdout")), fields);
        if (session.isReconstructed()) {
            fields.put("notice", REBUILT_NOTICE);
        }
        redactFields(fields, session.getCdpWsUrl());

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

    private LockedExecution executeForOwner(List<String> args,
                                            String ownerKey,
                                            KernelBrowserSessionPort sessionPort,
                                            CliExecutionPort cli,
                                            OpenCliProperties properties,
                                            long budgetMs,
                                            long startedNanos) {
        long remaining = remainingMillis(budgetMs, startedNanos);
        if (remaining <= 0) {
            return LockedExecution.failed(lockTimeout());
        }
        KernelBrowserSession session;
        try {
            session = sessionPort.resolveForOwner(ownerKey);
        } catch (RuntimeException e) {
            return LockedExecution.failed(ToolResultPayload.failure(
                    "获取 Kernel 云端浏览器会话失败", "获取 Kernel 云端浏览器会话失败", null,
                    "kernel_browser_session_failed"));
        }
        if (session == null || StringUtils.isBlank(session.getCdpWsUrl())) {
            return LockedExecution.failed(ToolResultPayload.failure(
                    "Kernel 云端浏览器会话不可用", "Kernel 云端浏览器会话不可用", null,
                    "kernel_browser_session_unavailable"));
        }

        List<String> rewritten = OpenCliArgv.rewrite(args, ownerKey, properties.getExtraArgs());
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
        env.put("OPENCLI_CDP_ENDPOINT", session.getCdpWsUrl());
        env.put("OPENCLI_CACHE_DIR", resolveCacheDir(properties, ownerKey));
        try {
            CliResult result = cli.exec(CliInvocation.builder()
                    .tool(properties.getCommand())
                    .args(processArgs)
                    .cwd(agentContext.getWorkspaceRoot())
                    .env(env)
                    .unsetEnv(List.of(KERNEL_API_KEY))
                    .timeoutMs(remaining)
                    .capture("both")
                    .maxOutputChars(properties.getMaxOutputChars())
                    .build());
            return LockedExecution.completed(session, result, rewritten);
        } catch (RuntimeException e) {
            return LockedExecution.failed(ToolResultPayload.failure(
                    "opencli 执行失败", "opencli 执行失败", null, "opencli_execution_failed"));
        }
    }

    private static ToolResultPayload lockUnavailable() {
        return ToolResultPayload.failure("浏览器操作锁未装配", "浏览器操作锁未装配", null, "browser_lock_unavailable");
    }

    private static ToolResultPayload lockTimeout() {
        return ToolResultPayload.failure("云端浏览器正被占用，等待超时", "云端浏览器正被占用，等待超时", null,
                "browser_lock_timeout");
    }

    private static long remainingMillis(long budgetMs, long startedNanos) {
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
        return budgetMs - Math.max(0L, elapsed);
    }

    private static final class LockedExecution {
        private final ToolResultPayload failure;
        private final KernelBrowserSession session;
        private final CliResult result;
        private final List<String> rewritten;

        private LockedExecution(ToolResultPayload failure,
                                KernelBrowserSession session,
                                CliResult result,
                                List<String> rewritten) {
            this.failure = failure;
            this.session = session;
            this.result = result;
            this.rewritten = rewritten;
        }

        private static LockedExecution failed(ToolResultPayload failure) {
            return new LockedExecution(failure, null, null, List.of());
        }

        private static LockedExecution completed(KernelBrowserSession session,
                                                 CliResult result,
                                                 List<String> rewritten) {
            return new LockedExecution(null, session, result, rewritten);
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

    private static Map<String, Object> toFields(CliResult result, String cdpWsUrl) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("ok", result.isOk());
        fields.put("tool", result.getTool());
        fields.put("exit_code", result.getExitCode());
        fields.put("stdout", redactText(result.getStdout(), cdpWsUrl));
        fields.put("stderr", redactText(result.getStderr(), cdpWsUrl));
        fields.put("duration_ms", result.getDurationMs());
        fields.put("timed_out", result.isTimedOut());
        fields.put("truncated", result.isTruncated());
        return fields;
    }

    private static void redactFields(Map<String, Object> fields, String cdpWsUrl) {
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            entry.setValue(redactValue(entry.getValue(), cdpWsUrl));
        }
    }

    private static Object redactValue(Object value, String cdpWsUrl) {
        if (value instanceof String text) {
            return redactText(text, cdpWsUrl);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> redacted = new LinkedHashMap<>();
            map.forEach((key, nested) -> redacted.put(redactText(String.valueOf(key), cdpWsUrl),
                    redactValue(nested, cdpWsUrl)));
            return redacted;
        }
        if (value instanceof List<?> list) {
            List<Object> redacted = new ArrayList<>(list.size());
            for (Object item : list) {
                redacted.add(redactValue(item, cdpWsUrl));
            }
            return redacted;
        }
        return value;
    }

    private static String redactText(String value, String cdpWsUrl) {
        if (StringUtils.isBlank(value) || StringUtils.isBlank(cdpWsUrl)) {
            return value;
        }
        return value.replace(cdpWsUrl, "[redacted]");
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

    private static String resolveCacheDir(OpenCliProperties properties, String ownerKey) {
        String root = StringUtils.trimToNull(properties.getCacheDir());
        if (root == null) {
            root = Path.of(System.getProperty("java.io.tmpdir"), "reactor-opencli").toString();
        }
        return Path.of(root, ownerKey).toString();
    }

    private static List<String> stringList(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
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
}
