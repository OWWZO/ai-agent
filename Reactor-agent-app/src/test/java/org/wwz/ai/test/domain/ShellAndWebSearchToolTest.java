package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.common.BashTool;
import org.wwz.ai.domain.agent.runtime.tool.common.PowerShellTool;
import org.wwz.ai.domain.agent.runtime.tool.common.WebSearchTool;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.test.domain.support.ReactorRuntimeTestSupport;

import java.util.Map;

/**
 * Bash / PowerShell / WebSearch 移植回归。
 */
public class ShellAndWebSearchToolTest {

    @Test
    public void bashShouldRunEcho() {
        String os = System.getProperty("os.name", "").toLowerCase();
        AgentContext context = AgentContext.builder()
                .requestId("req-bash-001")
                .sessionId("session-bash-001")
                .build();
        BashTool tool = new BashTool();
        tool.setAgentContext(context);

        String command = os.contains("win") ? "echo hello-bash" : "echo hello-bash";
        ToolResultPayload payload = (ToolResultPayload) tool.execute(Map.of(
                "command", command,
                "timeout", 15_000
        ));

        Assert.assertNotNull(payload);
        Assert.assertFalse(Boolean.TRUE.equals(payload.getFailed()));
        Assert.assertTrue(payload.getLlmObservation().contains("hello-bash")
                || payload.getToolResult().contains("hello-bash"));
    }

    @Test
    public void powerShellShouldRunWriteOutputOnWindowsOrSkip() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("win")) {
            return;
        }
        AgentContext context = AgentContext.builder()
                .requestId("req-ps-001")
                .sessionId("session-ps-001")
                .build();
        PowerShellTool tool = new PowerShellTool();
        tool.setAgentContext(context);

        ToolResultPayload payload = (ToolResultPayload) tool.execute(Map.of(
                "command", "Write-Output 'hello-ps'",
                "timeout", 20_000
        ));

        Assert.assertNotNull(payload);
        Assert.assertFalse(Boolean.TRUE.equals(payload.getFailed()));
        Assert.assertTrue(payload.getLlmObservation().contains("hello-ps")
                || payload.getToolResult().contains("hello-ps"));
    }

    @Test
    public void webSearchShouldFailWhenNoApiKey() {
        ReactorConfig config = new ReactorConfig();
        ReflectionTestUtils.setField(config, "webSearchMode", "auto");
        ReflectionTestUtils.setField(config, "webSearchGrokApiKey", "");
        ReflectionTestUtils.setField(config, "webSearchGrokBaseUrl", "");
        ReflectionTestUtils.setField(config, "webSearchGrokModel", "");
        ReflectionTestUtils.setField(config, "webSearchTavilyApiKey", "");
        ReflectionTestUtils.setField(config, "webSearchBraveApiKey", "");

        AgentContext context = AgentContext.builder()
                .requestId("req-ws-001")
                .sessionId("session-ws-001")
                .runtimeDependencies(ReactorRuntimeTestSupport.runtimeDependencies(config))
                .build();
        WebSearchTool tool = new WebSearchTool();
        tool.setAgentContext(context);

        ToolResultPayload payload = (ToolResultPayload) tool.execute(Map.of("query", "Spring AI"));
        Assert.assertTrue(Boolean.TRUE.equals(payload.getFailed()));
        Assert.assertTrue(payload.getErrorMsg().contains("未配置")
                || payload.getLlmObservation().contains("未配置"));
    }

    @Test
    public void webSearchShouldPreferGrokThenParseCitations() {
        ReactorConfig config = new ReactorConfig();
        ReflectionTestUtils.setField(config, "webSearchMode", "auto");
        ReflectionTestUtils.setField(config, "webSearchGrokApiKey", "grok-key");
        ReflectionTestUtils.setField(config, "webSearchGrokBaseUrl", "https://api.x.ai");
        ReflectionTestUtils.setField(config, "webSearchGrokModel", "grok-4");
        ReflectionTestUtils.setField(config, "webSearchGrokInterfaceUrl", "/v1/chat/completions");
        ReflectionTestUtils.setField(config, "webSearchTavilyApiKey", "tavily-should-not-use");
        ReflectionTestUtils.setField(config, "webSearchBraveApiKey", "");

        RemoteHttpPort httpPort = request -> {
            Assert.assertEquals("POST", request.getMethod());
            Assert.assertTrue(request.getUrl().contains("api.x.ai"));
            Assert.assertTrue(request.getBody().contains("web_search")
                    || request.getBody().contains("search_parameters"));
            return """
                    {
                      "choices": [
                        {
                          "message": {
                            "role": "assistant",
                            "content": "Grok found [Example Title](https://example.com) about Spring."
                          }
                        }
                      ],
                      "citations": [
                        {"title": "Example Title", "url": "https://example.com", "snippet": "snippet"}
                      ]
                    }
                    """;
        };

        AgentContext context = AgentContext.builder()
                .requestId("req-ws-grok")
                .sessionId("session-ws-grok")
                .runtimeDependencies(ReactorRuntimeTestSupport.runtimeDependencies(config, httpPort))
                .build();
        WebSearchTool tool = new WebSearchTool();
        tool.setAgentContext(context);

        ToolResultPayload payload = (ToolResultPayload) tool.execute(Map.of("query", "Spring AI"));
        Assert.assertFalse(Boolean.TRUE.equals(payload.getFailed()));
        Assert.assertTrue(payload.getLlmObservation().contains("grok"));
        Assert.assertTrue(payload.getLlmObservation().contains("https://example.com"));
        Assert.assertTrue(payload.getLlmObservation().contains("Example Title"));
    }

    @Test
    public void webSearchShouldFallbackToTavilyWhenGrokFails() {
        ReactorConfig config = new ReactorConfig();
        ReflectionTestUtils.setField(config, "webSearchMode", "auto");
        ReflectionTestUtils.setField(config, "webSearchGrokApiKey", "grok-key");
        ReflectionTestUtils.setField(config, "webSearchGrokBaseUrl", "https://api.x.ai");
        ReflectionTestUtils.setField(config, "webSearchGrokModel", "grok-4");
        ReflectionTestUtils.setField(config, "webSearchTavilyApiKey", "tavily-key");
        ReflectionTestUtils.setField(config, "webSearchBraveApiKey", "");

        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        RemoteHttpPort httpPort = request -> {
            int n = calls.incrementAndGet();
            if (request.getUrl().contains("x.ai")) {
                throw new RuntimeException("grok down");
            }
            Assert.assertTrue(request.getUrl().contains("tavily.com"));
            return """
                    {
                      "results": [
                        {"title": "Tavily Hit", "url": "https://tavily.example", "content": "ok"}
                      ]
                    }
                    """;
        };

        AgentContext context = AgentContext.builder()
                .requestId("req-ws-fallback")
                .sessionId("session-ws-fallback")
                .runtimeDependencies(ReactorRuntimeTestSupport.runtimeDependencies(config, httpPort))
                .build();
        WebSearchTool tool = new WebSearchTool();
        tool.setAgentContext(context);

        ToolResultPayload payload = (ToolResultPayload) tool.execute(Map.of("query", "example search"));
        Assert.assertFalse(Boolean.TRUE.equals(payload.getFailed()));
        Assert.assertTrue(payload.getLlmObservation().contains("tavily"));
        Assert.assertTrue(payload.getLlmObservation().contains("Tavily Hit"));
        Assert.assertTrue(calls.get() >= 2);
    }

    @Test
    public void webSearchShouldParseTavilyResponse() throws Exception {
        ReactorConfig config = new ReactorConfig();
        ReflectionTestUtils.setField(config, "webSearchMode", "tavily");
        ReflectionTestUtils.setField(config, "webSearchTavilyApiKey", "test-key");
        ReflectionTestUtils.setField(config, "webSearchBraveApiKey", "");

        RemoteHttpPort httpPort = request -> {
            Assert.assertEquals("POST", request.getMethod());
            Assert.assertTrue(request.getUrl().contains("tavily.com"));
            return """
                    {
                      "results": [
                        {"title": "Example Title", "url": "https://example.com", "content": "snippet here"}
                      ]
                    }
                    """;
        };

        AgentContext context = AgentContext.builder()
                .requestId("req-ws-002")
                .sessionId("session-ws-002")
                .runtimeDependencies(ReactorRuntimeTestSupport.runtimeDependencies(config, httpPort))
                .build();
        WebSearchTool tool = new WebSearchTool();
        tool.setAgentContext(context);

        ToolResultPayload payload = (ToolResultPayload) tool.execute(Map.of("query", "example search"));
        Assert.assertFalse(Boolean.TRUE.equals(payload.getFailed()));
        Assert.assertTrue(payload.getLlmObservation().contains("Example Title"));
        Assert.assertTrue(payload.getLlmObservation().contains("https://example.com"));
        Assert.assertTrue(payload.getLlmObservation().contains("tavily"));
    }
}
