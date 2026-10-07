package org.wwz.ai.test.domain;

import com.alibaba.fastjson.JSON;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.wwz.ai.domain.agent.adapter.port.AgentBrowserSession;
import org.wwz.ai.domain.agent.adapter.port.AgentBrowserSessionPort;
import org.wwz.ai.domain.agent.adapter.port.cli.CliExecutionPort;
import org.wwz.ai.domain.agent.adapter.port.cli.CliInvocation;
import org.wwz.ai.domain.agent.adapter.port.cli.CliResult;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.ToolObservationSerializer;
import org.wwz.ai.domain.agent.runtime.tool.browser.AgentBrowserTool;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.test.domain.support.ReactorRuntimeTestSupport;
import org.wwz.ai.types.agent.config.OpenCliProperties;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class AgentBrowserToolTest {

    @Test
    public void shouldExecuteUsingKernelCdpWithoutRelayAndHideEndpointFromPayload() {
        String cdpWsUrl = "wss://proxy.kernel.example/browser/session-secret";
        CliExecutionPort cli = mockSuccessfulCli("connected to " + cdpWsUrl);
        AgentBrowserSessionPort sessionPort = Mockito.mock(AgentBrowserSessionPort.class);
        Mockito.when(sessionPort.isConfigured()).thenReturn(true);
        Mockito.when(sessionPort.resolveForUser("user-1")).thenReturn(AgentBrowserSession.builder()
                .userId("user-1")
                .agentSessionId("session-1")
                .agentBrowserName("rb-user-1")
                .cdpWsUrl(cdpWsUrl)
                .reconstructed(false)
                .build());

        AgentBrowserTool tool = new AgentBrowserTool();
        AgentContext context = context(cli, sessionPort);
        tool.setAgentContext(context);
        ToolResultPayload payload = (ToolResultPayload) tool.execute(Map.of("args", List.of("browser", "list")));

        Assert.assertEquals("agent_browser", tool.getName());
        Assert.assertFalse(Boolean.TRUE.equals(payload.getFailed()));
        Assert.assertNull(context.getRuntimeDependencies().getOptionalBrowserRelayPort());
        ArgumentCaptor<CliInvocation> captor = ArgumentCaptor.forClass(CliInvocation.class);
        Mockito.verify(cli).exec(captor.capture());
        CliInvocation invocation = captor.getValue();
        Assert.assertEquals(cdpWsUrl, invocation.getEnv().get("OPENCLI_CDP_ENDPOINT"));
        Assert.assertFalse(invocation.getEnv().containsKey("OPENCLI_RELAY_URL"));
        Assert.assertFalse(invocation.getEnv().containsKey("OPENCLI_RELAY_SECRET"));
        Assert.assertFalse(invocation.getEnv().containsKey("OPENCLI_RELAY_VISITOR_ID"));
        Assert.assertEquals(List.of("KERNEL_API_KEY"), invocation.getUnsetEnv());
        Assert.assertEquals(List.of("main.js", "browser", "list"), invocation.getArgs());
        Assert.assertFalse(JSON.toJSONString(payload).contains(cdpWsUrl));
    }

    @Test
    public void shouldIncludeNoticeWhenKernelReconstructsBrowser() {
        CliExecutionPort cli = mockSuccessfulCli("{\"ok\":true}");
        AgentBrowserSessionPort sessionPort = Mockito.mock(AgentBrowserSessionPort.class);
        Mockito.when(sessionPort.isConfigured()).thenReturn(true);
        Mockito.when(sessionPort.resolveForUser("user-1")).thenReturn(AgentBrowserSession.builder()
                .userId("user-1")
                .agentSessionId("session-2")
                .agentBrowserName("rb-user-1")
                .cdpWsUrl("wss://proxy.kernel.example/browser/session-2")
                .reconstructed(true)
                .build());

        AgentBrowserTool tool = new AgentBrowserTool();
        tool.setAgentContext(context(cli, sessionPort));
        ToolResultPayload payload = (ToolResultPayload) tool.execute(Map.of("args", List.of("browser", "list")));

        Assert.assertTrue(ToolObservationSerializer.serializePayload(payload)
                .contains("Agent 浏览器已重建，之前的页面状态已丢失。"));
    }

    @Test
    public void shouldRunKernelCallsForSameOwnerConcurrently() throws Exception {
        String cdpWsUrl = "wss://proxy.kernel.example/browser/session-1";
        AgentBrowserSessionPort sessionPort = Mockito.mock(AgentBrowserSessionPort.class);
        Mockito.when(sessionPort.isConfigured()).thenReturn(true);
        Mockito.when(sessionPort.resolveForUser("user-1")).thenReturn(AgentBrowserSession.builder()
                .userId("user-1")
                .agentSessionId("session-1")
                .agentBrowserName("rb-user-1")
                .cdpWsUrl(cdpWsUrl)
                .reconstructed(false)
                .build());

        CliExecutionPort cli = Mockito.mock(CliExecutionPort.class);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Mockito.doAnswer(invocation -> {
            int current = active.incrementAndGet();
            maxActive.updateAndGet(previous -> Math.max(previous, current));
            if (current == 1) {
                firstEntered.countDown();
            }
            if (current >= 2) {
                secondEntered.countDown();
            }
            try {
                if (!release.await(2, TimeUnit.SECONDS)) {
                    throw new AssertionError("CLI calls were not released");
                }
                return successfulCliResult("{\"ok\":true}");
            } finally {
                active.decrementAndGet();
            }
        }).when(cli).exec(Mockito.any(CliInvocation.class));

        AgentBrowserTool tool = new AgentBrowserTool();
        tool.setAgentContext(context(cli, sessionPort));
        Map<String, Object> input = Map.of("args", List.of("browser", "list"));
        CompletableFuture<Object> first = CompletableFuture.supplyAsync(() -> tool.execute(input));
        CompletableFuture<Object> second = null;
        try {
            Assert.assertTrue(firstEntered.await(1, TimeUnit.SECONDS));
            second = CompletableFuture.supplyAsync(() -> tool.execute(input));
            Assert.assertTrue(secondEntered.await(1, TimeUnit.SECONDS));
        } finally {
            release.countDown();
        }

        Assert.assertNotNull(second);
        ToolResultPayload firstPayload = (ToolResultPayload) first.get(1, TimeUnit.SECONDS);
        ToolResultPayload secondPayload = (ToolResultPayload) second.get(1, TimeUnit.SECONDS);
        Assert.assertFalse(Boolean.TRUE.equals(firstPayload.getFailed()));
        Assert.assertFalse(Boolean.TRUE.equals(secondPayload.getFailed()));
        Assert.assertEquals(2, maxActive.get());
        Mockito.verify(cli, Mockito.times(2)).exec(Mockito.any(CliInvocation.class));
    }

    private static CliExecutionPort mockSuccessfulCli(String stdout) {
        CliExecutionPort cli = Mockito.mock(CliExecutionPort.class);
        Mockito.when(cli.exec(Mockito.any())).thenReturn(successfulCliResult(stdout));
        return cli;
    }

    private static CliResult successfulCliResult(String stdout) {
        return CliResult.builder()
                .ok(true)
                .tool("opencli")
                .exitCode(0)
                .stdout(stdout)
                .stderr("")
                .durationMs(12)
                .timedOut(false)
                .truncated(false)
                .artifacts(List.of())
                .build();
    }

    private static AgentContext context(CliExecutionPort cli, AgentBrowserSessionPort sessionPort) {
        OpenCliProperties properties = new OpenCliProperties();
        properties.setEnabled(true);
        properties.setCommand("node");
        properties.setPrefixArgs(List.of("main.js"));
        properties.setCacheDir("kernel_cache");
        ReactorRuntimeDependencies dependencies = ReactorRuntimeTestSupport.runtimeDependencies(new ReactorConfig())
                .toBuilder()
                .cliExecutionPort(cli)
                .openCliProperties(properties)
                .agentBrowserSessionPort(sessionPort)
                .build();
        return AgentContext.builder()
                .requestId("req-1")
                .sessionId("session-1")
                .userId("user-1")
                .workspaceRoot(System.getProperty("java.io.tmpdir"))
                .runtimeDependencies(dependencies)
                .build();
    }
}
