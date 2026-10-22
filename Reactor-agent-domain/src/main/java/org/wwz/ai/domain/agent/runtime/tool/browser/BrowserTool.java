package org.wwz.ai.domain.agent.runtime.tool.browser;

import com.alibaba.fastjson.JSON;
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
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.tool.cli.CliArtifactCaptureCoordinator;
import org.wwz.ai.domain.agent.runtime.tool.cli.CliArtifactCaptureLease;
import org.wwz.ai.domain.agent.runtime.tool.cli.CliArtifactCapturePlan;
import org.wwz.ai.domain.agent.runtime.tool.cli.CliArtifactMaterialization;
import org.wwz.ai.domain.agent.runtime.tool.cli.CliArtifactMaterializer;
import org.wwz.ai.domain.agent.runtime.tool.cli.OpenCliArgv;
import org.wwz.ai.types.agent.config.OpenCliProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Setter
public class BrowserTool implements BaseTool {

    public static final String TOOL_NAME = "browser";
    private static final String BROWSER_TIMEOUT_TEXT = "浏览器操作超时";

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
        return BrowserCliSupport.toParams();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        Map<String, Object> params = input instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : new LinkedHashMap<>();
        List<String> args = BrowserCliSupport.stringList(params.get("args"));
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
        long budgetMs = BrowserCliSupport.resolveTimeout(params.get("timeout_ms"), properties);
        long started = System.nanoTime();
        Execution execution = executeCli(deps, args, userId, cli, properties, budgetMs, started);
        if (execution.failure != null) {
            return execution.failure;
        }
        CliResult result = execution.result;
        List<String> rewritten = execution.rewritten;
        Map<String, Object> fields = BrowserCliSupport.toFields(result, null);
        BrowserCliSupport.appendArtifacts(fields, execution.materialization);
        BrowserCliSupport.publishViewport(agentContext, result.getStdout(), fields);
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
        long remaining = BrowserCliSupport.remainingMillis(budgetMs, started);
        if (remaining <= 0) {
            return Execution.failed(BrowserCliSupport.browserTimeout(BROWSER_TIMEOUT_TEXT));
        }
        BrowserRelayPort relay = deps.getOptionalBrowserRelayPort();
        if (relay == null || !relay.isOnline(userId)) {
            // browser 常驻挂载：relay 离线是运行期事实，不能靠 tools[] 消失来表达。
            // 这里必须给出可执行的下一步，否则模型只会重试同一条命令。
            String offline = "浏览器未连接：用户的本机浏览器 relay 当前不在线。"
                    + "请提示用户打开并登录浏览器连接后重试；在用户确认已连接前不要重复调用本工具。";
            return Execution.failed(ToolResultPayload.failure(
                    offline, offline, null, "browser_offline"));
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
        ToolArtifactSource source = agentContext.getCurrentToolArtifactSource();
        CliArtifactCapturePlan capturePlan = CliArtifactCaptureCoordinator.plan(
                TOOL_NAME,
                args,
                processArgs,
                agentContext.getWorkspaceRoot(),
                source == null ? null : source.getToolCallId(),
                agentContext.getRequestId());
        if (!capturePlan.isValid()) {
            return Execution.failed(BrowserCliSupport.captureFailure(capturePlan), rewritten, CliArtifactMaterialization.empty());
        }
        Map<String, String> env = new LinkedHashMap<>();
        env.put("OPENCLI_RELAY_URL", resolveRelayUrl(properties, deps));
        env.put("OPENCLI_RELAY_VISITOR_ID", userId);
        String secret = resolveSecret(deps);
        if (StringUtils.isNotBlank(secret)) {
            env.put("OPENCLI_RELAY_SECRET", secret);
        }
        env.put("OPENCLI_CACHE_DIR", BrowserCliSupport.resolveCacheDir(properties, userId));
        try (CliArtifactCaptureLease lease = CliArtifactCaptureCoordinator.acquire(capturePlan)) {
            Set<String> baseline = capturePlan.requiresCaptureLease()
                    ? CliArtifactMaterializer.snapshotWorkspace(lease.getCaptureRootAbsolute())
                    : null;
            CliResult result;
            try {
                result = cli.exec(CliInvocation.builder()
                        .tool(properties.getCommand())
                        .args(capturePlan.getRewrittenArgs())
                        .cwd(agentContext.getWorkspaceRoot())
                        .env(env)
                        .timeoutMs(remaining)
                        .capture("both")
                        .maxOutputChars(properties.getMaxOutputChars())
                        .build());
            } catch (RuntimeException e) {
                CliArtifactMaterialization materialization = BrowserCliSupport.materialize(
                    agentContext, capturePlan, baseline, List.of(), "browser 执行产物");
                return Execution.failed(BrowserCliSupport.cliFailure(materialization), rewritten, materialization);
            }
            if (result == null) {
                CliArtifactMaterialization materialization = BrowserCliSupport.materialize(
                    agentContext, capturePlan, baseline, List.of(), "browser 执行产物");
                return Execution.failed(BrowserCliSupport.cliFailure(materialization), rewritten, materialization);
            }
            CliArtifactMaterialization materialization = BrowserCliSupport.materialize(
                    agentContext, capturePlan, baseline, result.getArtifacts(), "browser 执行产物");
            return Execution.completed(result, rewritten, materialization);
        } catch (CliArtifactCaptureCoordinator.CliArtifactCaptureException e) {
            return Execution.failed(ToolResultPayload.failure(
                    "artifact capture 失败", "artifact capture 失败", null, e.getCode()),
                    rewritten, CliArtifactMaterialization.empty());
        }
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

}
