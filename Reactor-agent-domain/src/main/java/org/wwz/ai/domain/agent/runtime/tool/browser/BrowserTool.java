package org.wwz.ai.domain.agent.runtime.tool.browser;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.adapter.port.BrowserRelayPort;
import org.wwz.ai.domain.agent.adapter.port.BrowserBskConnectionPort;
import org.wwz.ai.domain.agent.adapter.port.cli.CliExecutionPort;
import org.wwz.ai.domain.agent.adapter.port.cli.CliInvocation;
import org.wwz.ai.domain.agent.adapter.port.cli.CliResult;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.tool.cli.CliArtifactCaptureCoordinator;
import org.wwz.ai.domain.agent.runtime.tool.cli.CliArtifactCaptureLease;
import org.wwz.ai.domain.agent.runtime.tool.cli.CliArtifactCapturePlan;
import org.wwz.ai.domain.agent.runtime.tool.cli.CliArtifactFailure;
import org.wwz.ai.domain.agent.runtime.tool.cli.CliArtifactMaterialization;
import org.wwz.ai.domain.agent.runtime.tool.cli.CliArtifactMaterializeCommand;
import org.wwz.ai.domain.agent.runtime.tool.cli.CliArtifactMaterializer;
import org.wwz.ai.domain.agent.runtime.tool.cli.OpenCliArgv;
import org.wwz.ai.types.agent.config.OpenCliProperties;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Setter
public class BrowserTool implements BaseTool {

    public static final String TOOL_NAME = "browser";

    /** opencli 命令面的参数说明（改造前原样保留）。 */
    private static final String OPENCLI_ARGS_DESCRIPTION =
            "CLI argv 参数数组。先用 list 查看可用命令，再用 <site> --help 或 <site> <command> --help 查询参数；下载命令必须传入当前任务专属的 --output 目录，并行下载使用不同目录；每项命令行参数分别传入。";

    /**
     * bsk 命令面的参数说明。
     *
     * <p>只列 bsk 的原生命令，并显式否掉 opencli 的站点适配器词汇——
     * 模型在两套命令之间串台的根源就是工具描述里同时出现两套说法。</p>
     */
    private static final String BSK_ARGS_DESCRIPTION =
            "bsk CLI 的参数数组（不要加 bsk 前缀）。可用命令：navigate <url>、navigate-back、navigate-forward、reload、"
            + "click [target]、hover [target]、focus [target]、blur [target]、fill [target] --value <text>、press <key>、"
            + "select [target]、scroll-to [target]、observe、snapshot、get-html、evaluate <expression>、screenshot、"
            + "wait-for-navigation、wait-ms <duration>、tab list|create|close|select、session start|list|stop。"
            + "target 用 observe/snapshot 输出的元素引用（如 @e3），也可以用 --selector <css> 定位。"
            + "每一条浏览器命令都必须带 --session <名字>：这个名字就是你要操作的那个浏览器上下文的身份，"
            + "同一批操作要始终传同一个名字，@eN 引用才连得上。名字由你自己取，建议用有意义的词"
            + "（如 --session xiaohongshu）；名字不存在时会自动创建一个新会话，不用先调 session start。"
            + "用不同的名字就能开多个互不干扰的会话；用之前的名字就能切回那个会话继续操作。"
            + "任务完成后不要关闭会话或浏览器窗口：保留窗口，用户才能回看这次操作打开了哪些页面。"
            + "只有用户明确要求关闭浏览器时，才调用 session stop。"
            + "只使用上面这些命令，不要使用列表之外的命令。";

    private AgentContext agentContext;

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return isBskSurface()
                ? "远程操作用户的浏览器（bsk 命令面）。"
                : "远程操作用户的浏览器。";
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("args", Map.of(
                "type", "array",
                "items", Map.of("type", "string"),
                "description", isBskSurface() ? BSK_ARGS_DESCRIPTION : OPENCLI_ARGS_DESCRIPTION
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
        appendArtifacts(fields, execution.materialization);
        publishViewport(result.getStdout(), fields);
        boolean sensitive = OpenCliArgv.isSensitive(rewritten);
        if (sensitive) {
            Map<String, Object> redacted = new LinkedHashMap<>();
            redacted.put("redacted", OpenCliArgv.sensitiveKind(rewritten));
            redacted.put("exit_code", result.getExitCode());
            redacted.put("stdout_chars", result.getStdout() == null ? 0 : result.getStdout().length());
            redacted.put("timed_out", result.isTimedOut());
            // 敏感命令也保留文件产物引用：URL 由文件服务生成，不含 Cookie 或 relay 信息。
            Object artifacts = fields.get("artifacts");
            if (artifacts != null) {
                redacted.put("artifacts", artifacts);
            }
            Object failures = fields.get("artifact_failures");
            if (failures != null) {
                redacted.put("artifact_failures", failures);
            }
            return ToolResultPayload.builder()
                    .toolResult(JSON.toJSONString(redacted))
                    .llmObservation(JSON.toJSONString(fields))
                    .llmData(fields)
                    .ledgerObservation(JSON.toJSONString(redacted))
                    .failed(Boolean.FALSE)
                    .build();
        }
        if (!result.isOk()) {
            // `fromData` 把 failed 写死成 FALSE，CLI 失败会被当成成功。
            // 这里显式标记，agent 才能从 failed 标志上判断这次调用没成功。
            return ToolResultPayload.builder()
                    .llmData(fields)
                    .llmObservation(JSON.toJSONString(fields))
                    .failed(Boolean.TRUE)
                    .errorMsg(executionErrorCode(fields))
                    .build();
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
        // 021：backend=bsk 时扩展连的是 bsk WS 网关而非老 Reactor relay，
        // relay.isOnline(userId) 必然为 false——不能用老守卫拦截 bsk 调用。
        // bsk 链路有自己的在线状态端口（BrowserBskConnectionPort）。
        //
        // surface=bsk（browser 工具只暴露 bsk 命令）时底层必然是 bsk 网关，
        // 与 backend 无关：opencli 进程根本不参与。
        boolean bskSurface = isBskSurface(properties);
        boolean bskBackend = bskSurface || "bsk".equalsIgnoreCase(properties.getBackend());
        if (bskBackend) {
            BrowserBskConnectionPort bsk = deps.getOptionalBrowserBskConnectionPort();
            if (bsk != null && !bsk.isOnline(userId)) {
                // 与老链路同一个口径：离线是运行期事实，必须给出可执行的下一步，
                // 否则模型只会重试同一条命令。
                String offline = "浏览器未连接：用户的本机浏览器 relay 当前不在线。"
                        + "请提示用户打开并登录浏览器连接后重试；在用户确认已连接前不要重复调用本工具。";
                return Execution.failed(ToolResultPayload.failure(
                        offline, offline, null, "browser_offline"));
            }
        } else if (relay == null || !relay.isOnline(userId)) {
            // browser 常驻挂载：relay 离线是运行期事实，不能靠 tools[] 消失来表达。
            // 这里必须给出可执行的下一步，否则模型只会重试同一条命令。
            String offline = "浏览器未连接：用户的本机浏览器 relay 当前不在线。"
                    + "请提示用户打开并登录浏览器连接后重试；在用户确认已连接前不要重复调用本工具。";
            return Execution.failed(ToolResultPayload.failure(
                    offline, offline, null, "browser_offline"));
        }
        List<String> rewritten;
        List<String> processArgs = new ArrayList<>();
        if (bskSurface) {
            // bsk 命令面：参数原样交给 bsk CLI，**不**追加 opencli 的 "-f json"
            // （那是 opencli 的输出格式开关，bsk CLI 不认）。
            // "--json" 必须放在子命令之前：commander 只在子命令前识别全局选项。
            if (properties.getBskPrefixArgs() == null || properties.getBskPrefixArgs().isEmpty()) {
                return Execution.failed(ToolResultPayload.failure(
                        "bsk 命令面未配置 bsk-prefix-args", "bsk 命令面未配置 bsk-prefix-args",
                        null, "bsk_surface_unavailable"));
            }
            rewritten = new ArrayList<>(args);
            for (String prefix : properties.getBskPrefixArgs()) {
                if (StringUtils.isNotBlank(prefix)) {
                    processArgs.add(prefix);
                }
            }
            // "--json" 跟在脚本路径之后、子命令之前：commander 只在子命令前识别全局选项。
            processArgs.add("--json");
            processArgs.addAll(rewritten);
        } else {
            rewritten = OpenCliArgv.rewrite(args, properties.getExtraArgs());
            if (properties.getPrefixArgs() != null) {
                for (String prefix : properties.getPrefixArgs()) {
                    if (StringUtils.isNotBlank(prefix)) {
                        processArgs.add(prefix);
                    }
                }
            }
            processArgs.addAll(rewritten);
        }
        ToolArtifactSource source = agentContext.getCurrentToolArtifactSource();
        CliArtifactCapturePlan capturePlan = CliArtifactCaptureCoordinator.plan(
                TOOL_NAME,
                args,
                processArgs,
                agentContext.getWorkspaceRoot(),
                source == null ? null : source.getToolCallId(),
                agentContext.getRequestId());
        if (!capturePlan.isValid()) {
            return Execution.failed(captureFailure(capturePlan), rewritten, CliArtifactMaterialization.empty());
        }
        Map<String, String> env = new LinkedHashMap<>();
        // 021 双后端开关：backend=bsk 时整条解析执行链路由 CLI 内部切换到
        // ReactorPage（bsk 后端）。
        //
        // 注意 bsk 模式**不注入** OPENCLI_RELAY_URL：那个变量在 opencli 侧是
        // relay 的 HTTP 语义（POST /internal/browser/rpc），在 bsk 侧却是
        // WS 网关语义——同一变量两套语义会互相打断（specs/021 KNOWN-FIXES P0-3）。
        // bsk 用新变量 OPENCLI_BSK_RELAY_URL，user_id 用 OPENCLI_BSK_VISITOR_ID。
        if (bskBackend) {
            env.put("OPENCLI_BSK_RELAY_URL", resolveBskRelayUrl(properties, deps));
            env.put("OPENCLI_BSK_VISITOR_ID", userId);
            if (bskSurface) {
                // bsk 命令面**不注入 session id**：会话由 agent 用 --session <名字> 自己指定，
                // 对齐 OpenCLI 的交互模型（名字即身份，不存在就自动建）。Java 不替 agent
                // 决定用哪个会话，否则 agent 永远只能挤在注入的那一个里。
            } else {
                // opencli 命令面 + bsk 后端：解析执行链路由 CLI 内部切到 ReactorPage。
                env.put("BROWSER_BACKEND", "bsk");
            }
        } else {
            env.put("OPENCLI_RELAY_URL", resolveRelayUrl(properties, deps));
            env.put("OPENCLI_RELAY_VISITOR_ID", userId);
        }
        String secret = resolveSecret(deps);
        if (StringUtils.isNotBlank(secret)) {
            env.put("OPENCLI_RELAY_SECRET", secret);
        }
        env.put("OPENCLI_CACHE_DIR", resolveCacheDir(properties, userId));
        try (CliArtifactCaptureLease lease = CliArtifactCaptureCoordinator.acquire(capturePlan)) {
            Set<String> baseline = capturePlan.requiresCaptureLease()
                    ? CliArtifactMaterializer.snapshotWorkspace(lease.getCaptureRootAbsolute())
                    : null;
            CliResult result;
            try {
                result = cli.exec(CliInvocation.builder()
                        .tool(resolveToolCommand(properties, bskSurface))
                        .args(capturePlan.getRewrittenArgs())
                        .cwd(agentContext.getWorkspaceRoot())
                        .env(env)
                        .timeoutMs(remaining)
                        .capture("both")
                        .maxOutputChars(properties.getMaxOutputChars())
                        .build());
            } catch (RuntimeException e) {
                CliArtifactMaterialization materialization = materialize(capturePlan, baseline, List.of());
                return Execution.failed(cliFailure(materialization), rewritten, materialization);
            }
            if (result == null) {
                CliArtifactMaterialization materialization = materialize(capturePlan, baseline, List.of());
                return Execution.failed(cliFailure(materialization), rewritten, materialization);
            }
            CliArtifactMaterialization materialization = materialize(
                    capturePlan, baseline, result.getArtifacts());
            return Execution.completed(result, rewritten, materialization);
        } catch (CliArtifactCaptureCoordinator.CliArtifactCaptureException e) {
            return Execution.failed(ToolResultPayload.failure(
                    "artifact capture 失败", "artifact capture 失败", null, e.getCode()),
                    rewritten, CliArtifactMaterialization.empty());
        }
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
        private final CliArtifactMaterialization materialization;

        private Execution(ToolResultPayload failure,
                          CliResult result,
                          List<String> rewritten,
                          CliArtifactMaterialization materialization) {
            this.failure = failure;
            this.result = result;
            this.rewritten = rewritten;
            this.materialization = materialization;
        }

        private static Execution failed(ToolResultPayload failure) {
            return failed(failure, List.of(), CliArtifactMaterialization.empty());
        }

        private static Execution failed(ToolResultPayload failure,
                                        List<String> rewritten,
                                        CliArtifactMaterialization materialization) {
            return new Execution(failure, null, rewritten, materialization);
        }

        private static Execution completed(CliResult result,
                                           List<String> rewritten,
                                           CliArtifactMaterialization materialization) {
            return new Execution(null, result, rewritten, materialization);
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

    private static void appendArtifacts(Map<String, Object> fields, CliArtifactMaterialization materialization) {
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

    private CliArtifactMaterialization materialize(CliArtifactCapturePlan plan,
                                                   Set<String> baseline,
                                                   List<org.wwz.ai.domain.agent.adapter.port.cli.CliArtifact> reported) {
        try {
            return CliArtifactMaterializer.materialize(CliArtifactMaterializeCommand.builder()
                    .agentContext(agentContext)
                    .workspaceRoot(agentContext.getWorkspaceRoot())
                    .reportedArtifacts(reported)
                    .commandLabel("browser 执行产物")
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

    private static ToolResultPayload captureFailure(CliArtifactCapturePlan plan) {
        CliArtifactCaptureCoordinator.ToolCaptureFailure failure =
                CliArtifactCaptureCoordinator.failure(plan);
        return ToolResultPayload.failureFrom(failure.code(), Map.of("message", failure.message()));
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
                .llmObservation(JSON.toJSONString(detail))
                .llmData(detail)
                .failed(Boolean.TRUE)
                .errorMsg("opencli_execution_failed")
                .build();
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
        // bsk CLI 失败时在 stderr 追加 `Hint:` / `Retry:` 两行。提升成结构化字段，
        // agent 不必再从自由文本里解析「这次失败能不能重试」。
        if (!result.isOk()) {
            String stderr = result.getStderr();
            String hint = stderrLine(stderr, "Hint: ");
            if (hint != null) {
                fields.put("error_hint", hint);
            }
            String retry = stderrLine(stderr, "Retry: ");
            if (retry != null) {
                fields.put("retry", retry);
            }
        }
        return fields;
    }

    /** 取 stderr 中以 {@code prefix} 开头的那一行（去掉前缀），没有则返回 null。 */
    private static String stderrLine(String stderr, String prefix) {
        if (stderr == null || stderr.isEmpty()) {
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

    /**
     * 失败时给 agent 的错误码：优先取 bsk CLI 首行 {@code Error: <code>: <message>}
     * 里的 code，取不到则退回原有的固定值。
     */
    private static String executionErrorCode(Map<String, Object> fields) {
        Object stderr = fields.get("stderr");
        if (stderr != null) {
            String first = stderrLine(String.valueOf(stderr), "Error: ");
            if (first != null) {
                int colon = first.indexOf(':');
                if (colon > 0) {
                    String code = first.substring(0, colon).trim();
                    if (!code.isEmpty()) {
                        return code;
                    }
                }
            }
        }
        return "opencli_execution_failed";
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
        return "http://127.0.0.1:" + resolvePort(deps) + "/internal/browser/rpc";
    }

    /**
     * bsk 模式下 CLI 该连的网关地址。
     *
     * <p>没显式配置时按本机端口 + CLI 侧端点路径拼出来：本机部署（CLI 由
     * Java 拉起、扩展在同一台机器）零配置即可跑通。</p>
     */
    private static String resolveBskRelayUrl(OpenCliProperties properties, ReactorRuntimeDependencies deps) {
        String configured = StringUtils.trimToNull(properties.getBskRelayUrl());
        if (configured != null) {
            return configured;
        }
        return "ws://127.0.0.1:" + resolvePort(deps) + properties.getBskCliPath();
    }

    private static String resolvePort(ReactorRuntimeDependencies deps) {
        if (deps.getEnvironment() != null) {
            return deps.getEnvironment().getProperty("local.server.port",
                    deps.getEnvironment().getProperty("server.port", "8100"));
        }
        return "8100";
    }

    private static String resolveSecret(ReactorRuntimeDependencies deps) {
        if (deps.getEnvironment() != null) {
            return StringUtils.defaultString(deps.getEnvironment().getProperty("reactor.browser-relay.internal-rpc-secret"));
        }
        return "";
    }

    /** browser 工具当前是否走 bsk 命令面。 */
    private boolean isBskSurface() {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            return false;
        }
        return isBskSurface(agentContext.getRuntimeDependencies().getOpenCliProperties());
    }

    private static boolean isBskSurface(OpenCliProperties properties) {
        return properties != null && "bsk".equalsIgnoreCase(properties.getSurface());
    }

    /** bsk 命令面下执行的命令；未单独配置时复用 {@link OpenCliProperties#getCommand()}。 */
    private static String resolveToolCommand(OpenCliProperties properties, boolean bskSurface) {
        if (bskSurface) {
            String configured = StringUtils.trimToNull(properties.getBskCommand());
            if (configured != null) {
                return configured;
            }
        }
        return properties.getCommand();
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
