package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.wwz.ai.domain.agent.adapter.port.BrowserRelayPort;
import org.wwz.ai.domain.agent.adapter.port.cli.CliExecutionPort;
import org.wwz.ai.domain.agent.adapter.port.cli.CliInvocation;
import org.wwz.ai.domain.agent.adapter.port.cli.CliResult;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.infrastructure.adapter.port.InProcessBrowserOperationLockAdapter;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.cli.OpenCliArgv;
import org.wwz.ai.domain.agent.runtime.tool.browser.BrowserTool;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.test.domain.support.ReactorRuntimeTestSupport;
import org.wwz.ai.types.agent.config.OpenCliProperties;

import java.util.List;
import java.util.Map;

public class BrowserToolTest {

    @Test
    public void shouldExposeBrowserToolWithCliUsageGuidance() {
        BrowserTool tool = new BrowserTool();

        Assert.assertEquals("browser", tool.getName());
        Assert.assertEquals("远程操作用户的浏览器。", tool.getDescription());
        Map<?, ?> params = (Map<?, ?>) tool.toParams().get("properties");
        Map<?, ?> args = (Map<?, ?>) params.get("args");
        String argsDescription = String.valueOf(args.get("description"));
        Assert.assertTrue(argsDescription.contains("list"));
        Assert.assertTrue(argsDescription.contains("--help"));
    }

    @Test
    public void shouldInjectVisitorSessionAndFormatOnlySupportedCommands() {
        Assert.assertEquals(
                List.of("browser", "visitor:vis_zhangsan", "click", "12"),
                OpenCliArgv.rewrite(List.of("browser", "click", "12"), "vis_zhangsan", List.of("-f", "json")));
        Assert.assertEquals(
                List.of("browser", "visitor:vis_zhangsan", "click", "12"),
                OpenCliArgv.rewrite(List.of("browser", "visitor:vis_zhangsan", "click", "12"),
                        "vis_zhangsan", List.of("-f", "json")));
        Assert.assertEquals(
                List.of("bilibili", "search", "redis", "-f", "json"),
                OpenCliArgv.rewrite(List.of("bilibili", "search", "redis"), "vis_zhangsan", List.of("-f", "json")));
        Assert.assertEquals(
                List.of("xiaohongshu", "feed", "--limit", "10", "-f", "json"),
                OpenCliArgv.rewrite(List.of("xiaohongshu", "feed", "--limit", "10", "-f", "json"),
                        "vis_zhangsan", List.of("-f", "json")));
        Assert.assertEquals(
                List.of("bilibili", "search", "redis", "--keep-tab", "false", "-f", "json"),
                OpenCliArgv.rewrite(List.of("bilibili", "search", "redis", "--keep-tab", "false"),
                        "vis_zhangsan", List.of("-f", "json")));
        Assert.assertEquals(
                List.of("list", "-f", "json"),
                OpenCliArgv.rewrite(List.of("list"), "vis_zhangsan", List.of("-f", "json")));
    }

    @Test
    public void shouldKeepCookiesInObservationAndRedactLedger() {
        CliExecutionPort cli = Mockito.mock(CliExecutionPort.class);
        Mockito.when(cli.exec(Mockito.any())).thenReturn(CliResult.builder()
                .ok(true)
                .tool("opencli")
                .exitCode(0)
                .stdout("{\"cookies\":[{\"name\":\"sid\",\"value\":\"secret-cookie\"}]}")
                .stderr("")
                .durationMs(12)
                .timedOut(false)
                .truncated(false)
                .artifacts(List.of())
                .build());
        BrowserTool tool = new BrowserTool();
        tool.setAgentContext(onlineContext(cli));

        ToolResultPayload payload = (ToolResultPayload) tool.execute(Map.of("args", List.of("browser", "cookies")));

        Assert.assertFalse(Boolean.TRUE.equals(payload.getFailed()));
        Assert.assertTrue(payload.getLlmObservation().contains("secret-cookie"));
        Assert.assertFalse(payload.getLedgerObservation().contains("secret-cookie"));
        Assert.assertTrue(payload.getLedgerObservation().contains("redacted"));
        ArgumentCaptor<CliInvocation> captor = ArgumentCaptor.forClass(CliInvocation.class);
        Mockito.verify(cli).exec(captor.capture());
        Assert.assertTrue(captor.getValue().getArgs().contains("visitor:visitor-1"));
        Assert.assertTrue(captor.getValue().getEnv().get("OPENCLI_CACHE_DIR").replace('\\', '/').endsWith("vis_cache/visitor-1"));
        Assert.assertEquals("http://127.0.0.1:8100/internal/browser/rpc", captor.getValue().getEnv().get("OPENCLI_RELAY_URL"));
    }

    private AgentContext onlineContext(CliExecutionPort cli) {
        BrowserRelayPort relay = Mockito.mock(BrowserRelayPort.class);
        Mockito.when(relay.isOnline("visitor-1")).thenReturn(true);
        OpenCliProperties properties = new OpenCliProperties();
        properties.setEnabled(true);
        properties.setCommand("node");
        properties.setPrefixArgs(List.of("main.js"));
        properties.setCacheDir("vis_cache");
        AgentContext ctx = AgentContext.builder()
                .requestId("req-1")
                .sessionId("session-1")
                .visitorId("visitor-1")
                .workspaceRoot(System.getProperty("java.io.tmpdir"))
                .runtimeDependencies(ReactorRuntimeTestSupport.runtimeDependencies(new ReactorConfig())
                        .toBuilder()
                        .browserRelayPort(relay)
                        .cliExecutionPort(cli)
                        .openCliProperties(properties)
                        .browserOperationLockPort(new InProcessBrowserOperationLockAdapter())
                        .build())
                .build();
        return ctx;
    }
}
