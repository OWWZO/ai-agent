package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.common.WebSearchTool;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.test.domain.support.ReactorRuntimeTestSupport;

import com.alibaba.fastjson.JSON;

import java.util.Map;

/**
 * WebSearch 回归。
 */
public class ShellAndWebSearchToolTest {

    @Test
    public void webSearchShouldFailWhenNoApiKey() {
        ReactorConfig config = new ReactorConfig();
        ReflectionTestUtils.setField(config, "webSearchMode", "auto");
        ReflectionTestUtils.setField(config, "webSearchGrokApiKey", "");
        ReflectionTestUtils.setField(config, "webSearchGrokBaseUrl", "");
        ReflectionTestUtils.setField(config, "webSearchGrokModel", "");
        ReflectionTestUtils.setField(config, "webSearchExaApiKey", "");
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
        String data = JSON.toJSONString(payload.getLlmData());
        Assert.assertTrue(data.contains("grok"));
        Assert.assertTrue(data.contains("https://example.com"));
        Assert.assertTrue(data.contains("Example Title"));
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
        String data = JSON.toJSONString(payload.getLlmData());
        Assert.assertTrue(data.contains("tavily"));
        Assert.assertTrue(data.contains("Tavily Hit"));
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
        String data = JSON.toJSONString(payload.getLlmData());
        Assert.assertTrue(data.contains("Example Title"));
        Assert.assertTrue(data.contains("https://example.com"));
        Assert.assertTrue(data.contains("tavily"));
    }

    @Test
    public void webSearchShouldUseGptResponsesApiAndParseCitations() {
        ReactorConfig config = new ReactorConfig();
        ReflectionTestUtils.setField(config, "webSearchMode", "gpt");
        ReflectionTestUtils.setField(config, "webSearchGptApiKey", "openai-test-key");
        ReflectionTestUtils.setField(config, "webSearchGptBaseUrl", "https://api.openai.com/v1");
        ReflectionTestUtils.setField(config, "webSearchGptModel", "gpt-4.1");
        ReflectionTestUtils.setField(config, "webSearchGptInterfaceUrl", "/responses");

        RemoteHttpPort httpPort = request -> {
            Assert.assertEquals("POST", request.getMethod());
            Assert.assertEquals("https://api.openai.com/v1/responses", request.getUrl());
            Assert.assertEquals("Bearer openai-test-key", request.getHeaders().get("Authorization"));
            Assert.assertTrue(request.getBody().contains("web_search_preview"));
            Assert.assertTrue(request.getBody().contains("gpt-4.1"));
            return """
                    {
                      "output_text": "OpenAI found [Example](https://example.com).",
                      "output": [
                        {
                          "type": "message",
                          "content": [
                            {
                              "type": "output_text",
                              "text": "OpenAI found Example.",
                              "annotations": [
                                {"type": "url_citation", "url": "https://example.com", "title": "Example"}
                              ]
                            }
                          ]
                        }
                      ]
                    }
                    """;
        };

        AgentContext context = AgentContext.builder()
                .requestId("req-ws-gpt")
                .sessionId("session-ws-gpt")
                .runtimeDependencies(ReactorRuntimeTestSupport.runtimeDependencies(config, httpPort))
                .build();
        WebSearchTool tool = new WebSearchTool();
        tool.setAgentContext(context);

        ToolResultPayload payload = (ToolResultPayload) tool.execute(Map.of(
                "query", "latest Spring AI docs",
                "allowed_domains", java.util.List.of("spring.io")
        ));
        Assert.assertFalse(Boolean.TRUE.equals(payload.getFailed()));
        String data = JSON.toJSONString(payload.getLlmData());
        Assert.assertTrue(data.contains("gpt"));
        Assert.assertTrue(data.contains("https://example.com"));
        Assert.assertTrue(data.contains("Example"));
    }

    @Test
    public void webSearchShouldParseExaResponse() {
        ReactorConfig config = new ReactorConfig();
        ReflectionTestUtils.setField(config, "webSearchMode", "exa");
        ReflectionTestUtils.setField(config, "webSearchExaApiKey", "exa-test-key");
        ReflectionTestUtils.setField(config, "webSearchExaSearchUrl", "https://api.exa.ai/search");

        RemoteHttpPort httpPort = request -> {
            Assert.assertEquals("POST", request.getMethod());
            Assert.assertTrue(request.getUrl().contains("api.exa.ai/search"));
            Assert.assertEquals("exa-test-key", request.getHeaders().get("x-api-key"));
            Assert.assertTrue(request.getBody().contains("numResults"));
            return """
                    {
                      "results": [
                        {
                          "title": "Exa Hit",
                          "url": "https://exa.example/doc",
                          "text": "semantic search snippet"
                        }
                      ]
                    }
                    """;
        };

        AgentContext context = AgentContext.builder()
                .requestId("req-ws-exa")
                .sessionId("session-ws-exa")
                .runtimeDependencies(ReactorRuntimeTestSupport.runtimeDependencies(config, httpPort))
                .build();
        WebSearchTool tool = new WebSearchTool();
        tool.setAgentContext(context);

        ToolResultPayload payload = (ToolResultPayload) tool.execute(Map.of("query", "exa semantic search"));
        Assert.assertFalse(Boolean.TRUE.equals(payload.getFailed()));
        String data = JSON.toJSONString(payload.getLlmData());
        Assert.assertTrue(data.contains("exa"));
        Assert.assertTrue(data.contains("Exa Hit"));
        Assert.assertTrue(data.contains("https://exa.example/doc"));
    }
}
