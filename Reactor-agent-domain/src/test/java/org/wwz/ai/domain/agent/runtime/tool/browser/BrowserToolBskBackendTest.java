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
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.types.agent.config.OpenCliProperties;

import java.util.List;
import java.util.Map;

/**
 * 021：browser 工具在 bsk 后端下的环境变量注入与在线守卫。
 *
 * <p>两条硬约束：</p>
 * <ul>
 *   <li>bsk 模式<b>不注入</b> {@code OPENCLI_RELAY_URL}——它在 opencli 侧是 relay 的
 *       HTTP 语义，在 bsk 侧是 WS 语义，同一变量两套语义会互相打断。</li>
 *   <li>bsk 模式的在线判定看 bsk 自己的连接状态，不看老 relay。</li>
 * </ul>
 */
public class BrowserToolBskBackendTest {

    @Test
    public void shouldInjectBskGatewayEnvironmentInsteadOfLegacyRelayUrl() {
        CliExecutionPort cli = successfulCli();
        BrowserTool tool = new BrowserTool();
        tool.setAgentContext(context(cli, onlineBskPort()));

        ToolResultPayload payload = (ToolResultPayload) tool.execute(
                Map.of("args", List.of("bilibili", "hot", "--limit", "1")));

        Assert.assertFalse(Boolean.TRUE.equals(payload.getFailed()));
        ArgumentCaptor<CliInvocation> captor = ArgumentCaptor.forClass(CliInvocation.class);
        Mockito.verify(cli).exec(captor.capture());
        Map<String, String> env = captor.getValue().getEnv();
        Assert.assertEquals("bsk", env.get("BROWSER_BACKEND"));
        // 没配 bsk-relay-url 时按本机端口 + CLI 侧端点路径拼出来。
        Assert.assertEquals("ws://127.0.0.1:8100/api/agent/browser/bsk-cli",
                env.get("OPENCLI_BSK_RELAY_URL"));
        Assert.assertEquals("user-1", env.get("OPENCLI_BSK_VISITOR_ID"));
        // 关键：老 relay 的 HTTP 地址不能泄漏进 bsk 链路。
        Assert.assertFalse(env.containsKey("OPENCLI_RELAY_URL"));
        Assert.assertFalse(env.containsKey("OPENCLI_RELAY_VISITOR_ID"));
    }

    @Test
    public void shouldUseConfiguredBskRelayUrlWhenPresent() {
        CliExecutionPort cli = successfulCli();
        OpenCliProperties properties = bskProperties();
        properties.setBskRelayUrl("wss://gw.test/api/agent/browser/bsk-cli");
        BrowserTool tool = new BrowserTool();
        tool.setAgentContext(context(cli, onlineBskPort(), properties));

        tool.execute(Map.of("args", List.of("bilibili", "hot")));

        ArgumentCaptor<CliInvocation> captor = ArgumentCaptor.forClass(CliInvocation.class);
        Mockito.verify(cli).exec(captor.capture());
        Assert.assertEquals("wss://gw.test/api/agent/browser/bsk-cli",
                captor.getValue().getEnv().get("OPENCLI_BSK_RELAY_URL"));
    }

    @Test
    public void shouldRejectCallWhenBskExtensionIsOffline() {
        CliExecutionPort cli = successfulCli();
        BrowserTool tool = new BrowserTool();
        tool.setAgentContext(context(cli, offlineBskPort()));

        ToolResultPayload payload = (ToolResultPayload) tool.execute(
                Map.of("args", List.of("bilibili", "hot")));

        Assert.assertTrue(Boolean.TRUE.equals(payload.getFailed()));
        Assert.assertEquals("browser_offline", payload.getErrorMsg());
        Mockito.verify(cli, Mockito.never()).exec(Mockito.any());
    }

    // ── 测试脚手架 ────────────────────────────────────────────

    private static CliExecutionPort successfulCli() {
        CliExecutionPort cli = Mockito.mock(CliExecutionPort.class);
        Mockito.when(cli.exec(Mockito.any())).thenReturn(CliResult.builder()
                .ok(true)
                .tool("opencli")
                .exitCode(0)
                .stdout("[]")
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

    private static BrowserBskConnectionPort offlineBskPort() {
        return new BrowserBskConnectionPort() {
            @Override
            public boolean isOnline(String userId) {
                return false;
            }

            @Override
            public void disconnect(String userId) {
                // 无状态。
            }
        };
    }

    private static OpenCliProperties bskProperties() {
        OpenCliProperties properties = new OpenCliProperties();
        properties.setEnabled(true);
        properties.setCommand("node");
        properties.setPrefixArgs(List.of("main.js"));
        properties.setBackend("bsk");
        properties.setCacheDir("vis_cache");
        return properties;
    }

    private static AgentContext context(CliExecutionPort cli,
                                        BrowserBskConnectionPort bskPort) {
        return context(cli, bskPort, bskProperties());
    }

    private static AgentContext context(CliExecutionPort cli,
                                        BrowserBskConnectionPort bskPort,
                                        OpenCliProperties properties) {
        return AgentContext.builder()
                .requestId("req-1")
                .sessionId("session-1")
                .userId("user-1")
                .workspaceRoot(System.getProperty("java.io.tmpdir"))
                .runtimeDependencies(ReactorRuntimeDependencies.builder()
                        .environment(new StandardEnvironment())
                        .cliExecutionPort(cli)
                        .browserBskConnectionPort(bskPort)
                        .openCliProperties(properties)
                        .build())
                .build();
    }
}
