package org.wwz.ai.domain.agent.runtime.tool.browser;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.core.env.StandardEnvironment;
import org.wwz.ai.domain.agent.adapter.port.BrowserBskConnectionPort;
import org.wwz.ai.domain.agent.adapter.port.cli.CliExecutionPort;
import org.wwz.ai.domain.agent.adapter.port.cli.CliInvocation;
import org.wwz.ai.domain.agent.adapter.port.cli.CliResult;
import org.wwz.ai.domain.agent.runtime.CuteRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.types.agent.config.OpenCliProperties;

import java.util.List;
import java.util.Map;

/**
 * 021：browser 工具的 bsk 命令面。
 *
 * <p>切到 bsk 命令面后，工具只跑 bsk CLI、只讲 bsk 的命令：</p>
 * <ul>
 *   <li>argv 是 {@code [bsk-cli.js, --json, <命令>]}——不追加 opencli 的 {@code -f json}；</li>
 *   <li>环境变量走 bsk 网关那一套，并按本次请求注入会话 id；</li>
 *   <li>工具描述里不再出现 opencli 的 {@code list} / {@code --help} 词汇。</li>
 * </ul>
 */
public class BrowserToolBskSurfaceTest {

    @Test
    public void shouldRunBskCliWithJsonFlagAndNoOpencliFormatFlag() {
        CliExecutionPort cli = successfulCli();
        BrowserTool tool = new BrowserTool();
        tool.setAgentContext(context(cli, bskSurfaceProperties()));

        ToolResultPayload payload = (ToolResultPayload) tool.execute(
                Map.of("args", List.of("click", "@e3")));

        Assert.assertFalse(Boolean.TRUE.equals(payload.getFailed()));
        ArgumentCaptor<CliInvocation> captor = ArgumentCaptor.forClass(CliInvocation.class);
        Mockito.verify(cli).exec(captor.capture());
        // "--json" 必须在子命令之前（commander 只在子命令前识别全局选项）。
        Assert.assertEquals(List.of("bsk-cli.js", "--json", "click", "@e3"),
                captor.getValue().getArgs());
        // opencli 的输出格式开关不能混进来。
        Assert.assertFalse(captor.getValue().getArgs().contains("-f"));
    }

    @Test
    public void shouldInjectBskGatewayEnvironmentButNotASessionId() {
        CliExecutionPort cli = successfulCli();
        BrowserTool tool = new BrowserTool();
        tool.setAgentContext(context(cli, bskSurfaceProperties()));

        tool.execute(Map.of("args", List.of("observe")));

        ArgumentCaptor<CliInvocation> captor = ArgumentCaptor.forClass(CliInvocation.class);
        Mockito.verify(cli).exec(captor.capture());
        Map<String, String> env = captor.getValue().getEnv();
        Assert.assertEquals("ws://127.0.0.1:8100/api/agent/browser/bsk-cli",
                env.get("OPENCLI_BSK_RELAY_URL"));
        Assert.assertEquals("user-1", env.get("OPENCLI_BSK_VISITOR_ID"));
        // 会话身份由 agent 用 --session 自己给（对齐 OpenCLI），Java 不注入。
        Assert.assertFalse(env.containsKey("OPENCLI_BSK_SESSION_ID"));
        // 老 relay 的 HTTP 地址不能泄漏进 bsk 链路。
        Assert.assertFalse(env.containsKey("OPENCLI_RELAY_URL"));
        Assert.assertFalse(env.containsKey("OPENCLI_RELAY_VISITOR_ID"));
        // opencli 进程不参与，浏览器后端开关没有意义。
        Assert.assertFalse(env.containsKey("BROWSER_BACKEND"));
    }

    @Test
    public void shouldDescribeOnlyBskCommandsInBskSurface() {
        BrowserTool tool = new BrowserTool();
        tool.setAgentContext(context(successfulCli(), bskSurfaceProperties()));

        Map<?, ?> params = (Map<?, ?>) tool.toParams().get("properties");
        Map<?, ?> args = (Map<?, ?>) params.get("args");
        String description = String.valueOf(args.get("description"));
        Assert.assertTrue(description.contains("observe"));
        Assert.assertTrue(description.contains("navigate"));
        // opencli 的词汇不许出现，否则模型还是会在两套命令之间串台。
        Assert.assertFalse(description.contains("先用 list"));
        Assert.assertFalse(description.contains("<site>"));
    }

    @Test
    public void shouldKeepOpencliGuidanceWhenSurfaceIsNotBsk() {
        BrowserTool tool = new BrowserTool();
        tool.setAgentContext(context(successfulCli(), opencliSurfaceProperties()));

        Map<?, ?> params = (Map<?, ?>) tool.toParams().get("properties");
        Map<?, ?> args = (Map<?, ?>) params.get("args");
        Assert.assertTrue(String.valueOf(args.get("description")).contains("list"));
    }

    @Test
    public void shouldFailInsteadOfFallingBackToOpencliWhenBskCliIsNotConfigured() {
        CliExecutionPort cli = successfulCli();
        OpenCliProperties properties = bskSurfaceProperties();
        properties.setBskPrefixArgs(List.of());
        BrowserTool tool = new BrowserTool();
        tool.setAgentContext(context(cli, properties));

        ToolResultPayload payload = (ToolResultPayload) tool.execute(
                Map.of("args", List.of("click", "@e3")));

        Assert.assertTrue(Boolean.TRUE.equals(payload.getFailed()));
        Assert.assertEquals("bsk_surface_unavailable", payload.getErrorMsg());
        Mockito.verify(cli, Mockito.never()).exec(Mockito.any());
    }

    @Test
    public void shouldLiftHintAndRetryOutOfStderrIntoStructuredFields() {
        CliExecutionPort cli = failingCli(
                "Error: cdp_failed: Detached while handling command\n"
                        + "Hint: The page changed while the command was running. Inspect the page state before retrying.\n"
                        + "Retry: unsafe_to_retry\n");
        BrowserTool tool = new BrowserTool();
        tool.setAgentContext(context(cli, bskSurfaceProperties()));

        ToolResultPayload payload = (ToolResultPayload) tool.execute(
                Map.of("args", List.of("click", "@e3")));

        Assert.assertTrue(Boolean.TRUE.equals(payload.getFailed()));
        Map<?, ?> data = (Map<?, ?>) payload.getLlmData();
        // agent 直接读结构化字段，不必再解析自由文本。
        Assert.assertEquals("unsafe_to_retry", data.get("retry"));
        Assert.assertTrue(String.valueOf(data.get("error_hint")).contains("Inspect the page state"));
        // 原文仍然保留，便于排查。
        Assert.assertTrue(String.valueOf(data.get("stderr")).contains("Error: cdp_failed"));
    }

    @Test
    public void shouldOmitRetryFieldsWhenStderrHasNoClassification() {
        CliExecutionPort cli = failingCli("some unrelated crash\n");
        BrowserTool tool = new BrowserTool();
        tool.setAgentContext(context(cli, bskSurfaceProperties()));

        ToolResultPayload payload = (ToolResultPayload) tool.execute(
                Map.of("args", List.of("click", "@e3")));

        Map<?, ?> data = (Map<?, ?>) payload.getLlmData();
        Assert.assertFalse(data.containsKey("retry"));
        Assert.assertFalse(data.containsKey("error_hint"));
    }

    // ── 测试脚手架 ────────────────────────────────────────────

    /**
     * 失败的 CLI：stderr 里带 bsk CLI 追加的 Hint / Retry 两行。
     *
     * <p>agent 不该再从自由文本里解析「能不能重试」——这两个字段必须提升到
     * {@code llmData} 的结构化字段里。</p>
     */
    private static CliExecutionPort failingCli(String stderr) {
        CliExecutionPort cli = Mockito.mock(CliExecutionPort.class);
        Mockito.when(cli.exec(Mockito.any())).thenReturn(CliResult.builder()
                .ok(false)
                .tool("opencli")
                .exitCode(3)
                .stdout("")
                .stderr(stderr)
                .durationMs(5)
                .timedOut(false)
                .truncated(false)
                .artifacts(List.of())
                .build());
        return cli;
    }

    private static CliExecutionPort successfulCli() {
        CliExecutionPort cli = Mockito.mock(CliExecutionPort.class);
        Mockito.when(cli.exec(Mockito.any())).thenReturn(CliResult.builder()
                .ok(true)
                .tool("opencli")
                .exitCode(0)
                .stdout("{}")
                .stderr("")
                .durationMs(5)
                .timedOut(false)
                .truncated(false)
                .artifacts(List.of())
                .build());
        return cli;
    }

    private static BrowserBskConnectionPort onlineBskPort() {
        return new BrowserBskConnectionPort() {
            @Override
            public boolean isOnline(String userId) {
                return true;
            }

            @Override
            public void disconnect(String userId) {
                // 无状态。
            }
        };
    }

    private static OpenCliProperties bskSurfaceProperties() {
        OpenCliProperties properties = new OpenCliProperties();
        properties.setEnabled(true);
        properties.setCommand("node");
        properties.setPrefixArgs(List.of("main.js"));
        properties.setSurface("bsk");
        properties.setBskPrefixArgs(List.of("bsk-cli.js"));
        properties.setCacheDir("vis_cache");
        return properties;
    }

    private static OpenCliProperties opencliSurfaceProperties() {
        OpenCliProperties properties = new OpenCliProperties();
        properties.setEnabled(true);
        properties.setCommand("node");
        properties.setPrefixArgs(List.of("main.js"));
        properties.setCacheDir("vis_cache");
        return properties;
    }

    private static AgentContext context(CliExecutionPort cli, OpenCliProperties properties) {
        return AgentContext.builder()
                .requestId("req-1")
                .sessionId("session-1")
                .userId("user-1")
                .workspaceRoot(System.getProperty("java.io.tmpdir"))
                .runtimeDependencies(CuteRuntimeDependencies.builder()
                        .environment(new StandardEnvironment())
                        .cliExecutionPort(cli)
                        .browserBskConnectionPort(onlineBskPort())
                        .openCliProperties(properties)
                        .build())
                .build();
    }
}
