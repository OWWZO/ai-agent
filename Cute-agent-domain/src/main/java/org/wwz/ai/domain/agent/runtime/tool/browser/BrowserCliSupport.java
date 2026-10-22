package org.wwz.ai.domain.agent.runtime.tool.browser;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.wwz.ai.domain.agent.adapter.port.cli.CliResult;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.cli.CliArtifactCaptureCoordinator;
import org.wwz.ai.domain.agent.runtime.tool.cli.CliArtifactCapturePlan;
import org.wwz.ai.domain.agent.runtime.tool.cli.CliArtifactFailure;
import org.wwz.ai.domain.agent.runtime.tool.cli.CliArtifactMaterialization;
import org.wwz.ai.domain.agent.runtime.tool.cli.CliArtifactMaterializeCommand;
import org.wwz.ai.domain.agent.runtime.tool.cli.CliArtifactMaterializer;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;

import org.wwz.ai.types.agent.config.OpenCliProperties;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 两个浏览器工具共用的 CLI 编排辅助。
 * <p>
 * {@code AgentBrowserTool}（走 AgentBrowserSessionPort 的 CDP 会话）与
 * {@code BrowserTool}（走 BrowserRelayPort 的 relay）后端协议完全不同，
 * 但 argv 解析、产物捕获、viewport 发布、字段构建与缓存目录规则逐字相同。
 * 这些共有部分收敛到此处，两端各自只保留后端接入。
 */
public final class BrowserCliSupport {

    private static final long DEFAULT_TIMEOUT_FALLBACK_MS = 60_000L;
    private static final long MAX_TIMEOUT_FALLBACK_MS = 180_000L;
    private static final String CLI_FAILED_TEXT = "opencli 执行失败";
    private static final String CLI_FAILED_CODE = "opencli_execution_failed";

    private BrowserCliSupport() {
    }

    /** 超时结果：errorCode 两端一致，文案按工具区分。 */
    public static ToolResultPayload browserTimeout(String message) {
        return ToolResultPayload.failure(message, message, null, "browser_timeout");
    }

    public static long remainingMillis(long budgetMs, long startedNanos) {
        long elapsed = (System.nanoTime() - startedNanos) / 1_000_000L;
        return Math.max(0L, budgetMs - elapsed);
    }

    /** 默认参数表：两个浏览器工具在 opencli 命令面下的 schema 完全一致。 */
    public static Map<String, Object> toParams() {
        return toParams("CLI argv 参数数组。先用 list 查看可用命令，再用 <site> --help 或 <site> <command> --help 查询参数；下载命令必须传入当前任务专属的 --output 目录，并行下载使用不同目录；每项命令行参数分别传入。");
    }

    /** 参数表骨架一致，args 的说明按命令面传入。 */
    public static Map<String, Object> toParams(String argsDescription) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("args", Map.of(
                "type", "array",
                "items", Map.of("type", "string"),
                "description", argsDescription
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

    public static void appendArtifacts(Map<String, Object> fields, CliArtifactMaterialization materialization) {
        if (materialization == null) {
            return;
        }
        if (!materialization.getArtifacts().isEmpty()) {
            fields.put("artifacts", materialization.artifactMaps());
        }
        if (!materialization.getFailures().isEmpty()) {
            fields.put("artifact_failures", materialization.failureMaps());
        }
    }

    public static ToolResultPayload captureFailure(CliArtifactCapturePlan plan) {
        CliArtifactCaptureCoordinator.ToolCaptureFailure failure =
                CliArtifactCaptureCoordinator.failure(plan);
        return ToolResultPayload.failureFrom(failure.code(), Map.of("message", failure.message()));
    }

    /** CLI 非零退出：产物与产物失败仍要带到观察里，否则下载结果会被静默丢弃。 */
    public static ToolResultPayload cliFailure(CliArtifactMaterialization materialization) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("error", CLI_FAILED_TEXT);
        if (materialization != null && !materialization.getArtifacts().isEmpty()) {
            detail.put("artifacts", materialization.artifactMaps());
        }
        if (materialization != null && !materialization.getFailures().isEmpty()) {
            detail.put("artifact_failures", materialization.failureMaps());
        }
        return ToolResultPayload.builder()
                .toolResult(CLI_FAILED_TEXT)
                .llmObservation(JSON.toJSONString(detail))
                .llmData(detail)
                .failed(Boolean.TRUE)
                .errorMsg(CLI_FAILED_CODE)
                .build();
    }

    /**
     * 把 capture root 交给 CliArtifactMaterializer；扫描失败降级为 capture 失败而不是抛出。
     *
     * @param commandLabel 产物归属文案，按工具区分（「browser 执行产物」/「agent_browser 执行产物」）
     */
    public static CliArtifactMaterialization materialize(AgentContext agentContext,
                                                         CliArtifactCapturePlan plan,
                                                         Set<String> baseline,
                                                         List<org.wwz.ai.domain.agent.adapter.port.cli.CliArtifact> reported,
                                                         String commandLabel) {
        try {
            return CliArtifactMaterializer.materialize(CliArtifactMaterializeCommand.builder()
                    .agentContext(agentContext)
                    .workspaceRoot(agentContext.getWorkspaceRoot())
                    .reportedArtifacts(reported)
                    .commandLabel(commandLabel)
                    .baselineFiles(baseline)
                    .outputRoot(plan.getCaptureRoot())
                    .captureRoot(plan.getCaptureRoot())
                    .captureRootAbsolute(plan.getCaptureRootAbsolute())
                    .build());
        } catch (RuntimeException e) {
            return CliArtifactMaterialization.builder()
                    .artifacts(List.of())
                    .failures(List.of(CliArtifactFailure.builder()
                            .reason(CliArtifactFailure.CAPTURE_SCAN_FAILED)
                            .message("capture root 扫描失败")
                            .build()))
                    .build();
        }
    }

    /** 发布 viewport：树/URL 走 SSE，截图转 artifact 引用后从观察里移除。 */
    public static void publishViewport(AgentContext agentContext, String stdout, Map<String, Object> fields) {
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

    /**
     * 从 stdout 里裁出 viewport JSON。
     * CLI 可能在 JSON 前后打印日志，因此取第一个 '{' 到最后一个 '}'；
     * data 内的键只补充顶层，不覆盖。
     */
    public static Map<String, Object> extractViewport(String stdout) {
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

    public static long resolveTimeout(Object raw, OpenCliProperties properties) {
        long fallback = properties.getDefaultTimeoutMs() <= 0 ? DEFAULT_TIMEOUT_FALLBACK_MS : properties.getDefaultTimeoutMs();
        long max = properties.getMaxTimeoutMs() <= 0 ? MAX_TIMEOUT_FALLBACK_MS : properties.getMaxTimeoutMs();
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

    public static String resolveCacheDir(OpenCliProperties properties, String userId) {
        String root = StringUtils.trimToNull(properties.getCacheDir());
        if (root == null) {
            root = Path.of(System.getProperty("java.io.tmpdir"), "reactor-opencli").toString();
        }
        return Path.of(root, userId).toString();
    }

    public static List<String> stringList(Object raw) {
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

    /** CLI 结果转观察字段；cdpWsUrl 非空时对全文脱敏。 */
    public static Map<String, Object> toFields(CliResult result, String cdpWsUrl) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("ok", result.isOk());
        fields.put("tool", result.getTool());
        fields.put("exit_code", result.getExitCode());
        fields.put("stdout", redactText(result.getStdout(), cdpWsUrl));
        fields.put("stderr", redactText(result.getStderr(), cdpWsUrl));
        fields.put("duration_ms", result.getDurationMs());
        fields.put("timed_out", result.isTimedOut());
        fields.put("truncated", result.isTruncated());
        // bsk CLI 失败时在 stderr 追加 `Hint:` / `Retry:` 两行，提升成结构化字段。
        if (!result.isOk()) {
            String hint = stderrLine(result.getStderr(), "Hint: ");
            if (hint != null) {
                fields.put("error_hint", hint);
            }
            String retry = stderrLine(result.getStderr(), "Retry: ");
            if (retry != null) {
                fields.put("retry", retry);
            }
        }
        return fields;
    }

    /** 取 stderr 中以 {@code prefix} 开头的那一行（去掉前缀），没有则返回 null。 */
    public static String stderrLine(String stderr, String prefix) {
        if (StringUtils.isBlank(stderr)) {
            return null;
        }
        for (String line : stderr.split("\\R")) {
            if (line.startsWith(prefix)) {
                String value = line.substring(prefix.length()).trim();
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }

    public static void redactFields(Map<String, Object> fields, String cdpWsUrl) {
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            entry.setValue(redactValue(entry.getValue(), cdpWsUrl));
        }
    }

    public static Object redactValue(Object value, String cdpWsUrl) {
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

    public static String redactText(String value, String cdpWsUrl) {
        if (StringUtils.isBlank(value) || StringUtils.isBlank(cdpWsUrl)) {
            return value;
        }
        return value.replace(cdpWsUrl, "[redacted]");
    }
}
