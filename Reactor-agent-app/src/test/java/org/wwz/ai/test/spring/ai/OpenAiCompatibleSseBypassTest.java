package org.wwz.ai.test.spring.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamListener;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamRequest;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamSession;
import org.wwz.ai.domain.agent.runtime.llm.LLMSettings;
import org.wwz.ai.domain.agent.runtime.llm.OpenAiCompatibleChatCompletionRequestFactory;
import org.wwz.ai.domain.agent.runtime.llm.OpenAiCompatibleSseChatStreamClient;
import org.wwz.ai.domain.agent.runtime.llm.StreamResponseHandler;
import org.wwz.ai.domain.agent.runtime.llm.LlmChatResponseMapper;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 方案 A：旁路 Spring AI tool-call window 合并，直解 OpenAI 兼容 SSE delta。
 */
public class OpenAiCompatibleSseBypassTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void test_requestBodyContainsStreamToolsAndMessages() throws Exception {
        OpenAiCompatibleChatCompletionRequestFactory factory = new OpenAiCompatibleChatCompletionRequestFactory();
        ToolCallback tool = new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name("deep_search")
                        .description("search web")
                        .inputSchema("{\"type\":\"object\",\"properties\":{\"q\":{\"type\":\"string\"}},\"required\":[\"q\"]}")
                        .build();
            }

            @Override
            public String call(String toolInput) {
                return "";
            }
        };
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model("grok-4.5")
                .temperature(0.2)
                .toolCallbacks(List.of(tool))
                .toolChoice("auto")
                .internalToolExecutionEnabled(false)
                .build();
        Prompt prompt = new Prompt(List.of(
                new SystemMessage("you are helper"),
                new UserMessage("hello")
        ), options);

        String body = factory.buildStreamBody(prompt, options);
        JsonNode root = objectMapper.readTree(body);

        Assert.assertEquals("grok-4.5", root.get("model").asText());
        Assert.assertTrue(root.get("stream").asBoolean());
        Assert.assertEquals(2, root.get("messages").size());
        Assert.assertEquals("system", root.get("messages").get(0).get("role").asText());
        Assert.assertEquals("user", root.get("messages").get(1).get("role").asText());
        Assert.assertEquals(1, root.get("tools").size());
        Assert.assertEquals("deep_search", root.get("tools").get(0).get("function").get("name").asText());
        Assert.assertEquals("auto", root.get("tool_choice").asText());
        Assert.assertTrue(root.get("stream_options").get("include_usage").asBoolean());
    }

    @Test
    public void test_parseChunkKeepsFragmentedToolCallArguments() throws Exception {
        OpenAiCompatibleSseChatStreamClient client =
                new OpenAiCompatibleSseChatStreamClient(new OpenAiCompatibleChatCompletionRequestFactory());

        ChatResponse first = client.parseChunk("""
                {"id":"c1","choices":[{"index":0,"delta":{"role":"assistant","tool_calls":[{"index":0,"id":"call-1","type":"function","function":{"name":"deep_search","arguments":"{\\"q\\":"}}]}}]}
                """);
        ChatResponse second = client.parseChunk("""
                {"id":"c1","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\\"react\\"}"}}]},"finish_reason":null}]}
                """);
        ChatResponse done = client.parseChunk("""
                {"id":"c1","choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}],"usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}
                """);

        Assert.assertNotNull(first.getResult().getOutput().getToolCalls());
        Assert.assertEquals(1, first.getResult().getOutput().getToolCalls().size());
        AssistantMessage.ToolCall t1 = first.getResult().getOutput().getToolCalls().get(0);
        Assert.assertEquals("call-1", t1.id());
        Assert.assertEquals("deep_search", t1.name());
        Assert.assertEquals("{\"q\":", t1.arguments());

        AssistantMessage.ToolCall t2 = second.getResult().getOutput().getToolCalls().get(0);
        Assert.assertEquals("\"react\"}", t2.arguments());

        Assert.assertEquals("tool_calls", done.getResult().getMetadata().getFinishReason());
        Assert.assertEquals(Integer.valueOf(15), done.getMetadata().getUsage().getTotalTokens());
    }

    @Test
    public void test_streamEmitsSeparateToolArgDeltasWithoutMerge() throws Exception {
        List<String> lines = List.of(
                "data: {\"choices\":[{\"delta\":{\"content\":\"先说明\"}}]}",
                "",
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call-x\",\"type\":\"function\",\"function\":{\"name\":\"read_file\",\"arguments\":\"{\\\"path\\\":\"}}]}}]}",
                "",
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"\\\"a.txt\\\"}\"}}]}}]}",
                "",
                "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"tool_calls\"}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2,\"total_tokens\":3}}",
                "",
                "data: [DONE]",
                ""
        );
        RecordingRemoteStreamPort port = new RecordingRemoteStreamPort(lines);
        OpenAiCompatibleSseChatStreamClient client =
                new OpenAiCompatibleSseChatStreamClient(new OpenAiCompatibleChatCompletionRequestFactory());

        LLMSettings settings = LLMSettings.builder()
                .model("grok-4.5")
                .baseUrl("https://example.com")
                .interfaceUrl("/v1/chat/completions")
                .apiKey("sk-test")
                .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder().model("grok-4.5").build();
        Prompt prompt = new Prompt(List.of(new UserMessage("hi")), options);

        List<ChatResponse> chunks = client.stream(port, settings, prompt, options, 30L)
                .collectList()
                .block(java.time.Duration.ofSeconds(5));

        Assert.assertNotNull(chunks);
        Assert.assertTrue("应收到多帧未合并 chunk", chunks.size() >= 3);
        Assert.assertEquals("https://example.com/v1/chat/completions", port.lastUrl.get());
        Assert.assertTrue(port.lastBody.get().contains("\"stream\":true"));

        // 验证 tool args 仍是碎片，而非合并后的完整串
        List<String> argFragments = new ArrayList<>();
        for (ChatResponse chunk : chunks) {
            List<AssistantMessage.ToolCall> toolCalls = chunk.getResult().getOutput().getToolCalls();
            if (toolCalls == null) {
                continue;
            }
            for (AssistantMessage.ToolCall tc : toolCalls) {
                if (tc.arguments() != null && !tc.arguments().isEmpty()) {
                    argFragments.add(tc.arguments());
                }
            }
        }
        Assert.assertTrue(argFragments.stream().anyMatch(a -> a.contains("path")));
        Assert.assertTrue(argFragments.stream().anyMatch(a -> a.contains("a.txt")));
        Assert.assertFalse("不应只有一帧完整 arguments",
                argFragments.size() == 1 && argFragments.get(0).contains("path") && argFragments.get(0).contains("a.txt"));
    }

    @Test
    public void test_streamResponseHandlerAccumulatesDirectSseToolDeltas() throws Exception {
        OpenAiCompatibleSseChatStreamClient client =
                new OpenAiCompatibleSseChatStreamClient(new OpenAiCompatibleChatCompletionRequestFactory());
        // 真实 OpenAI：首帧带 id，后续帧常只带 index + arguments 碎片
        ChatResponse c1 = client.parseChunk(
                "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call-r\",\"type\":\"function\",\"function\":{\"name\":\"deep_search\",\"arguments\":\"{\\\"q\\\":\"}}]}}]}");
        ChatResponse c2 = client.parseChunk(
                "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"\\\"x\\\"}\"}}]},\"finish_reason\":\"tool_calls\"}]}");

        StreamResponseHandler handler = new StreamResponseHandler();
        ReactorConfig reactorConfig = new ReactorConfig();
        reactorConfig.setMessageInterval("{\"llm\":\"1,1\"}");
        ReflectionTestUtils.setField(handler, "reactorConfig", reactorConfig);
        ReflectionTestUtils.setField(handler, "chatResponseMapper", new LlmChatResponseMapper());

        AgentContext context = AgentContext.builder()
                .requestId("req-sse-bypass")
                .isStream(true)
                .streamMessageType("tool_thought")
                .build();

        var response = handler.handleToolCallStream(
                context,
                Flux.just(c1, c2),
                System.currentTimeMillis() - 10,
                false
        ).get(5, TimeUnit.SECONDS);

        Assert.assertEquals(1, response.getToolCalls().size());
        Assert.assertEquals("deep_search", response.getToolCalls().get(0).getFunction().getName());
        Assert.assertEquals("{\"q\":\"x\"}", response.getToolCalls().get(0).getFunction().getArguments());
        Assert.assertEquals("call-r", response.getToolCalls().get(0).getId());
    }

    private static final class RecordingRemoteStreamPort implements RemoteStreamPort {
        private final List<String> lines;
        private final AtomicReference<String> lastUrl = new AtomicReference<>();
        private final AtomicReference<String> lastBody = new AtomicReference<>();

        private RecordingRemoteStreamPort(List<String> lines) {
            this.lines = lines;
        }

        @Override
        public RemoteStreamSession openStream(RemoteStreamRequest request, RemoteStreamListener listener) {
            lastUrl.set(request.getUrl());
            lastBody.set(request.getBody());
            Thread t = new Thread(() -> {
                try {
                    listener.onOpen();
                    for (String line : lines) {
                        listener.onLine(line);
                    }
                    listener.onClosed();
                } catch (Exception e) {
                    listener.onFailure(e, null, null);
                }
            }, "test-sse-feed");
            t.setDaemon(true);
            t.start();
            return () -> {
            };
        }
    }
}
