package org.wwz.ai.domain.agent.runtime.tool.browser;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.adapter.port.AgentBrowserSession;
import org.wwz.ai.domain.agent.adapter.port.AgentBrowserSessionPort;
import org.wwz.ai.domain.agent.adapter.port.cli.CliExecutionPort;
import org.wwz.ai.domain.agent.adapter.port.cli.CliInvocation;
import org.wwz.ai.domain.agent.adapter.port.cli.CliResult;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
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
public class AgentBrowserTool implements BaseTool {

    public static final String TOOL_NAME = "agent_browser";
    private static final String KERNEL_API_KEY = "KERNEL_API_KEY";
    private static final String BROWSER_TIMEOUT_TEXT = "Agent 浏览器操作超时";
    private static final String REBUILT_NOTICE = "Agent 浏览器已重建，之前的页面状态已丢失。";

    private AgentContext agentContext;

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "远程操作当前用户的 Agent 浏览器。";
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("args", Map.of(
                "type", "array",
                "items", Map.of("type", "string"),
                "description", "CLI argv 参数数组。先用 list 查看可用命令，再用 <site> --help 或 <site> <command> --help 查询参数；下载命令必须传入当前任务专属的 --output 目录，并行下载使用不同目录；每项命令行参数分别传入。"
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
        List<String> args = BrowserCliSupport.stringList(params.get("args"));
        if (args.isEmpty()) {
            return ToolResultPayload.failure("args 不能为空", "args 不能为空", null, "missing args");
        }
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            return ToolResultPayload.failure("运行时未装配", "运行时未装配", null, "runtime_unavailable");
        }
        String userId = StringUtils.trimToNull(agentContext.getUserId());
        if (userId == null) {
            return ToolResultPayload.failure("缺少用户身份", "缺少用户身份", null, "missing userId");
        }

        ReactorRuntimeDependencies deps = agentContext.getRuntimeDependencies();
        AgentBrowserSessionPort sessionPort = deps.getOptionalAgentBrowserSessionPort();
        if (sessionPort == null || !sessionPort.isConfigured()) {
            return ToolResultPayload.failure("Agent 浏览器未配置", "Agent 浏览器未配置", null,
                    "agent_browser_unavailable");
        }
        CliExecutionPort cli = deps.getOptionalCliExecutionPort();
        OpenCliProperties properties = deps.getOpenCliProperties();
        if (cli == null || properties == null || !properties.isEnabled()) {
            return ToolResultPayload.failure("opencli 未配置", "opencli 未配置", null, "opencli_unavailable");
        }
        long budgetMs = BrowserCliSupport.resolveTimeout(params.get("timeout_ms"), properties);
        long started = System.nanoTime();
        Execution execution = executeForUser(args, userId, sessionPort, cli, properties, budgetMs, started);
        if (execution.failure != null) {
            return execution.failure;
        }
        AgentBrowserSession session = execution.session;
        CliResult result = execution.result;
        List<String> rewritten = execution.rewritten;
        Map<String, Object> fields = BrowserCliSupport.toFields(result, session.getCdpWsUrl());
        BrowserCliSupport.publishViewport(agentContext, String.valueOf(fields.get("stdout")), fields);
        if (session.isReconstructed()) {
            fields.put("notice", REBUILT_NOTICE);
        }
        BrowserCliSupport.redactFields(fields, session.getCdpWsUrl());
        // 文件产物在脱敏之后合并：downloadUrl 来自文件服务，不需要也不应被 CDP URL 替换逻辑改写。
        BrowserCliSupport.appendArtifacts(fields, execution.materialization);

        boolean sensitive = OpenCliArgv.isSensitive(rewritten);
        if (sensitive) {
            Map<String, Object> redacted = new LinkedHashMap<>();
            redacted.put("redacted", OpenCliArgv.sensitiveKind(rewritten));
            redacted.put("exit_code", result.getExitCode());
            redacted.put("stdout_chars", result.getStdout() == null ? 0 : result.getStdout().length());
            redacted.put("timed_out", result.isTimedOut());
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

    private Execution executeForUser(List<String> args,
                                            String userId,
                                             AgentBrowserSessionPort sessionPort,
                                            CliExecutionPort cli,
                                            OpenCliProperties properties,
                                            long budgetMs,
                                            long startedNanos) {
        long remaining = BrowserCliSupport.remainingMillis(budgetMs, startedNanos);
        if (remaining <= 0) {
            return Execution.failed(BrowserCliSupport.browserTimeout(BROWSER_TIMEOUT_TEXT));
        }
        AgentBrowserSession session;
        try {
            session = sessionPort.resolveForUser(userId);
        } catch (RuntimeException e) {
            return Execution.failed(ToolResultPayload.failure(
                    "获取 Agent 浏览器会话失败", "获取 Agent 浏览器会话失败", null,
                    "agent_browser_session_failed"));
        }
        if (session == null || StringUtils.isBlank(session.getCdpWsUrl())) {
            return Execution.failed(ToolResultPayload.failure(
                    "Agent 浏览器会话不可用", "Agent 浏览器会话不可用", null,
                    "agent_browser_session_unavailable"));
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
        env.put("OPENCLI_CDP_ENDPOINT", session.getCdpWsUrl());
        env.put("OPENCLI_CACHE_DIR", BrowserCliSupport.resolveCacheDir(properties, userId));
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
                        .unsetEnv(List.of(KERNEL_API_KEY))
                        .timeoutMs(remaining)
                        .capture("both")
                        .maxOutputChars(properties.getMaxOutputChars())
                        .build());
            } catch (RuntimeException e) {
                CliArtifactMaterialization materialization = BrowserCliSupport.materialize(
                        agentContext, capturePlan, baseline, List.of(), "agent_browser 执行产物");
                return Execution.failed(BrowserCliSupport.cliFailure(materialization), rewritten, materialization);
            }
            if (result == null) {
                CliArtifactMaterialization materialization = BrowserCliSupport.materialize(
                        agentContext, capturePlan, baseline, List.of(), "agent_browser 执行产物");
                return Execution.failed(BrowserCliSupport.cliFailure(materialization), rewritten, materialization);
            }
            CliArtifactMaterialization materialization = BrowserCliSupport.materialize(
                    agentContext, capturePlan, baseline, result.getArtifacts(), "agent_browser 执行产物");
            return Execution.completed(session, result, rewritten, materialization);
        } catch (CliArtifactCaptureCoordinator.CliArtifactCaptureException e) {
            return Execution.failed(ToolResultPayload.failure(
                    "artifact capture 失败", "artifact capture 失败", null, e.getCode()),
                    rewritten, CliArtifactMaterialization.empty());
        }
    }

    private static final class Execution {
        private final ToolResultPayload failure;
        private final AgentBrowserSession session;
        private final CliResult result;
        private final List<String> rewritten;
        private final CliArtifactMaterialization materialization;

        private Execution(ToolResultPayload failure,
                                 AgentBrowserSession session,
                                CliResult result,
                                List<String> rewritten,
                                CliArtifactMaterialization materialization) {
            this.failure = failure;
            this.session = session;
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
            return new Execution(failure, null, null, rewritten, materialization);
        }

        private static Execution completed(AgentBrowserSession session,
                                                 CliResult result,
                                                 List<String> rewritten,
                                                 CliArtifactMaterialization materialization) {
            return new Execution(null, session, result, rewritten, materialization);
        }
    }

}
