package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;
import org.wwz.ai.domain.agent.runtime.llm.LLM;
import org.wwz.ai.domain.agent.runtime.llm.LlmChatResponseMapper;
import org.wwz.ai.domain.agent.runtime.llm.StreamResponseHandler;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * StreamResponseHandler 测试
 */
public class StreamResponseHandlerTest {

    @Test
    public void test_handleStringStreamStopsForwardingAfterStructParseMarker() throws Exception {
        StreamResponseHandler handler = new StreamResponseHandler();
        ReactorConfig reactorConfig = new ReactorConfig();
        reactorConfig.setMessageInterval("{\"llm\":\"1,2\"}");
        ReflectionTestUtils.setField(handler, "reactorConfig", reactorConfig);
        ReflectionTestUtils.setField(handler, "chatResponseMapper", new LlmChatResponseMapper());

        RecordingPrinter printer = new RecordingPrinter();
        AgentContext context = AgentContext.builder()
                .requestId("req-1")
                .isStream(true)
                .streamMessageType("tool_thought")
                .printer(printer)
                .build();

        String fullContent = handler.handleStringStream(
                context,
                Flux.just(
                        textChunk("先分析"),
                        textChunk("```"),
                        textChunk("json {\"function_name\":\"deep_search\"}```")
                ),
                "```json",
                true
        ).get(5, java.util.concurrent.TimeUnit.SECONDS);

        Assert.assertEquals("先分析```json {\"function_name\":\"deep_search\"}```", fullContent);
        Assert.assertTrue(printer.messages.stream().anyMatch(message -> "先分析".equals(message.message) && message.isFinal));
        Assert.assertFalse(printer.messages.stream().anyMatch(message -> String.valueOf(message.message).contains("function_name")));
    }

    @Test
    public void test_handleToolCallStreamPushesReasoningEvenWithToolCalls() throws Exception {
        StreamResponseHandler handler = new StreamResponseHandler();
        ReactorConfig reactorConfig = new ReactorConfig();
        reactorConfig.setMessageInterval("{\"llm\":\"1,1\"}");
        ReflectionTestUtils.setField(handler, "reactorConfig", reactorConfig);
        ReflectionTestUtils.setField(handler, "chatResponseMapper", new LlmChatResponseMapper());

        RecordingPrinter printer = new RecordingPrinter();
        AgentContext context = AgentContext.builder()
                .requestId("req-reasoning")
                .isStream(true)
                .streamMessageType("tool_thought")
                .printer(printer)
                .build();

        LLM.ToolCallResponse response = handler.handleToolCallStream(
                context,
                Flux.just(
                        reasoningChunk("先想清楚", "要查资料",
                                new AssistantMessage.ToolCall("call-r", "function", "deep_search", "{\"q\":"), null, null),
                        reasoningChunk("", "再搜",
                                new AssistantMessage.ToolCall("call-r", "function", "deep_search", "\"x\"}"), "tool_calls", 20)
                ),
                System.currentTimeMillis() - 10
        ).get(5, java.util.concurrent.TimeUnit.SECONDS);

        Assert.assertEquals("要查资料再搜", response.getReasoningContent());
        Assert.assertEquals("先想清楚", response.getContent());
        Assert.assertEquals(1, response.getToolCalls().size());
        Assert.assertTrue(printer.messages.stream().anyMatch(
                m -> "llm_reasoning".equals(m.messageType) && Boolean.TRUE.equals(m.isFinal)));
        Assert.assertTrue(printer.messages.stream().anyMatch(
                m -> "tool_thought".equals(m.messageType)));
    }

    @Test
    public void test_handleToolCallStreamPushesContentBeforeToolCallsArrive() throws Exception {
        StreamResponseHandler handler = new StreamResponseHandler();
        ReactorConfig reactorConfig = new ReactorConfig();
        reactorConfig.setMessageInterval("{\"llm\":\"1,1\"}");
        ReflectionTestUtils.setField(handler, "reactorConfig", reactorConfig);
        ReflectionTestUtils.setField(handler, "chatResponseMapper", new LlmChatResponseMapper());

        RecordingPrinter printer = new RecordingPrinter();
        AgentContext context = AgentContext.builder()
                .requestId("req-content-first")
                .isStream(true)
                .streamMessageType("tool_thought")
                .printer(printer)
                .build();

        // 先只有 content，后才出现 tool_call —— 过程文必须先被推送
        LLM.ToolCallResponse response = handler.handleToolCallStream(
                context,
                Flux.just(
                        textChunk("我先说明下一步"),
                        toolChunk("", new AssistantMessage.ToolCall("call-x", "function", "read_file", "{\"path\":\"a\"}"), "tool_calls", 12)
                ),
                System.currentTimeMillis() - 10
        ).get(5, java.util.concurrent.TimeUnit.SECONDS);

        Assert.assertEquals("我先说明下一步", response.getContent());
        Assert.assertEquals(1, response.getToolCalls().size());
        Assert.assertTrue(
                "content 应在 tool_call 出现前就已推送",
                printer.messages.stream().anyMatch(
                        m -> "tool_thought".equals(m.messageType)
                                && String.valueOf(m.message).contains("我先说明下一步"))
        );
    }

    @Test
    public void test_handleToolCallStreamAggregatesArgumentsAndFinalContent() throws Exception {
        StreamResponseHandler handler = new StreamResponseHandler();
        ReactorConfig reactorConfig = new ReactorConfig();
        reactorConfig.setMessageInterval("{\"llm\":\"1,2\"}");
        ReflectionTestUtils.setField(handler, "reactorConfig", reactorConfig);
        ReflectionTestUtils.setField(handler, "chatResponseMapper", new LlmChatResponseMapper());

        RecordingPrinter printer = new RecordingPrinter();
        AgentContext context = AgentContext.builder()
                .requestId("req-2")
                .isStream(true)
                .streamMessageType("tool_thought")
                .printer(printer)
                .build();

        LLM.ToolCallResponse response = handler.handleToolCallStream(
                context,
                Flux.just(
                        toolChunk("先思考", new AssistantMessage.ToolCall("call-1", "function", "deep_search", "{\"query\":"), null, null),
                        toolChunk("", new AssistantMessage.ToolCall("call-1", "function", "deep_search", "\"spring ai\"}"), "tool_calls", 28)
                ),
                System.currentTimeMillis() - 10
        ).get(5, java.util.concurrent.TimeUnit.SECONDS);

        Assert.assertEquals("先思考", response.getContent());
        Assert.assertEquals("tool_calls", response.getFinishReason());
        Assert.assertEquals(Integer.valueOf(28), response.getTotalTokens());
        Assert.assertEquals(1, response.getToolCalls().size());
        Assert.assertEquals("{\"query\":\"spring ai\"}", response.getToolCalls().get(0).getFunction().getArguments());
        Assert.assertTrue(printer.messages.stream().anyMatch(message -> "先思考".equals(message.message) && message.isFinal));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void test_handleToolCallStreamPushesArgumentDeltasBeforeExecution() throws Exception {
        StreamResponseHandler handler = new StreamResponseHandler();
        ReactorConfig reactorConfig = new ReactorConfig();
        reactorConfig.setMessageInterval("{\"llm\":\"1,1\"}");
        ReflectionTestUtils.setField(handler, "reactorConfig", reactorConfig);
        ReflectionTestUtils.setField(handler, "chatResponseMapper", new LlmChatResponseMapper());

        RecordingPrinter printer = new RecordingPrinter();
        AgentContext context = AgentContext.builder()
                .requestId("req-tool-arguments")
                .isStream(true)
                .streamMessageType("tool_thought")
                .printer(printer)
                .build();

        LLM.ToolCallResponse response = handler.handleToolCallStream(
                context,
                Flux.just(
                        toolChunk("", new AssistantMessage.ToolCall(
                                "call-stream", "function", "workspace_grep", "{\"query\":\"spr"), null, null),
                        toolChunk("", new AssistantMessage.ToolCall(
                                "call-stream", "function", "workspace_grep", "ing\"}"), "tool_calls", 12)
                ),
                System.currentTimeMillis() - 10
        ).get(5, java.util.concurrent.TimeUnit.SECONDS);

        List<PrinterMessage> toolCallEvents = printer.messages.stream()
                .filter(message -> "tool_call_delta".equals(message.messageType))
                .toList();
        // 中间增量帧 + onComplete flush，至少 2 帧
        Assert.assertTrue("应推送多帧 tool_call_delta，实际=" + toolCallEvents.size(),
                toolCallEvents.size() >= 2);
        // messageId 为稳定 streamKey，全程相同；toolCallId 才是真实 call id
        String stableId = toolCallEvents.get(0).messageId;
        Assert.assertNotEquals("call-stream", stableId);
        Assert.assertTrue(String.valueOf(stableId).startsWith("stream-tool-"));
        for (PrinterMessage event : toolCallEvents) {
            Assert.assertEquals(stableId, event.messageId);
            Assert.assertEquals("call-stream",
                    ((Map<String, Object>) event.message).get("toolCallId"));
            Assert.assertFalse(event.isFinal);
        }
        Assert.assertEquals("{\"query\":\"spr",
                ((Map<String, Object>) toolCallEvents.get(0).message).get("argumentsText"));
        Assert.assertEquals("{\"query\":\"spr",
                ((Map<String, Object>) toolCallEvents.get(0).message).get("argumentsRaw"));
        Map<String, Object> lastPayload =
                (Map<String, Object>) toolCallEvents.get(toolCallEvents.size() - 1).message;
        Assert.assertEquals("{\"query\":\"spring\"}", lastPayload.get("argumentsText"));
        Assert.assertEquals("{\"query\":\"spring\"}", lastPayload.get("argumentsRaw"));
        Assert.assertEquals("tool_call_delta", lastPayload.get("messageType"));
        Assert.assertFalse(printer.messages.stream().anyMatch(message -> "tool_result".equals(message.messageType)));
        Assert.assertEquals("{\"query\":\"spring\"}",
                response.getToolCalls().get(0).getFunction().getArguments());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void test_handleToolCallStreamEmitsBeforeRealToolCallIdArrives() throws Exception {
        StreamResponseHandler handler = new StreamResponseHandler();
        ReactorConfig reactorConfig = new ReactorConfig();
        reactorConfig.setMessageInterval("{\"llm\":\"1,1\"}");
        ReflectionTestUtils.setField(handler, "reactorConfig", reactorConfig);
        ReflectionTestUtils.setField(handler, "chatResponseMapper", new LlmChatResponseMapper());

        RecordingPrinter printer = new RecordingPrinter();
        AgentContext context = AgentContext.builder()
                .requestId("req-tool-no-id-first")
                .isStream(true)
                .streamMessageType("tool_thought")
                .printer(printer)
                .build();

        // 兼容接口常见形态：先 name+args 无 id，后补 id
        LLM.ToolCallResponse response = handler.handleToolCallStream(
                context,
                Flux.just(
                        toolChunk("", new AssistantMessage.ToolCall(
                                "", "function", "code_execute", "{\"code\":\"print("), null, null),
                        toolChunk("", new AssistantMessage.ToolCall(
                                "", "function", "code_execute", "1)\"}"), null, null),
                        toolChunk("", new AssistantMessage.ToolCall(
                                "call-late-id", "function", "code_execute", ""), "tool_calls", 8)
                ),
                System.currentTimeMillis() - 10
        ).get(5, java.util.concurrent.TimeUnit.SECONDS);

        List<PrinterMessage> toolCallEvents = printer.messages.stream()
                .filter(message -> "tool_call_delta".equals(message.messageType))
                .toList();
        Assert.assertTrue("应在真实 id 到达前就推送 tool_call_delta", toolCallEvents.size() >= 2);
        String stableMessageId = toolCallEvents.get(0).messageId;
        Assert.assertTrue(stableMessageId.startsWith("stream-tool-"));
        for (PrinterMessage event : toolCallEvents) {
            Assert.assertEquals("全程 messageId 应稳定", stableMessageId, event.messageId);
        }
        Map<String, Object> first = (Map<String, Object>) toolCallEvents.get(0).message;
        Assert.assertEquals("code_execute", first.get("toolName"));
        Assert.assertEquals(stableMessageId, first.get("toolCallId"));
        Assert.assertTrue(String.valueOf(first.get("argumentsText")).contains("print"));
        Assert.assertTrue(String.valueOf(first.get("argumentsRaw")).contains("print"));

        Map<String, Object> last = (Map<String, Object>) toolCallEvents.get(toolCallEvents.size() - 1).message;
        Assert.assertEquals("call-late-id", last.get("toolCallId"));
        Assert.assertEquals("{\"code\":\"print(1)\"}", last.get("argumentsText"));
        Assert.assertEquals("{\"code\":\"print(1)\"}", last.get("argumentsRaw"));
        Assert.assertEquals("call-late-id", response.getToolCalls().get(0).getId());
        Assert.assertEquals("{\"code\":\"print(1)\"}",
                response.getToolCalls().get(0).getFunction().getArguments());
    }

    @Test
    public void test_handleToolCallStreamKeepsMessageIdWhenForwardingIsDisabled() throws Exception {
        StreamResponseHandler handler = new StreamResponseHandler();
        ReactorConfig reactorConfig = new ReactorConfig();
        reactorConfig.setMessageInterval("{\"llm\":\"1,2\"}");
        ReflectionTestUtils.setField(handler, "reactorConfig", reactorConfig);
        ReflectionTestUtils.setField(handler, "chatResponseMapper", new LlmChatResponseMapper());

        RecordingPrinter printer = new RecordingPrinter();
        AgentContext context = AgentContext.builder()
                .requestId("req-3")
                .isStream(true)
                .streamMessageType("plan_thought")
                .printer(printer)
                .build();

        LLM.ToolCallResponse response = handler.handleToolCallStream(
                context,
                Flux.just(toolChunk("先规划", new AssistantMessage.ToolCall("call-2", "function", "planning", "{\"command\":\"create\"}"), "tool_calls", 18)),
                System.currentTimeMillis() - 10,
                false
        ).get(5, java.util.concurrent.TimeUnit.SECONDS);

        Assert.assertNotNull(response.getStreamMessageId());
        Assert.assertTrue(printer.messages.isEmpty());
    }

    private ChatResponse textChunk(String content) {
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content(content)
                .properties(java.util.Map.of())
                .build();
        return new ChatResponse(List.of(new Generation(assistantMessage)));
    }

    private ChatResponse reasoningChunk(String content,
                                        String reasoning,
                                        AssistantMessage.ToolCall toolCall,
                                        String finishReason,
                                        Integer totalTokens) {
        java.util.Map<String, Object> props = new java.util.LinkedHashMap<>();
        if (reasoning != null && !reasoning.isBlank()) {
            props.put("reasoningContent", reasoning);
        }
        AssistantMessage.Builder builder = AssistantMessage.builder()
                .content(content == null ? "" : content)
                .properties(props);
        if (toolCall != null) {
            builder.toolCalls(List.of(toolCall));
        }
        AssistantMessage assistantMessage = builder.build();
        if (finishReason == null && totalTokens == null) {
            return new ChatResponse(List.of(new Generation(assistantMessage)));
        }
        ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.builder()
                .finishReason(finishReason)
                .build();
        if (totalTokens == null) {
            return new ChatResponse(List.of(new Generation(assistantMessage, generationMetadata)));
        }
        ChatResponseMetadata responseMetadata = ChatResponseMetadata.builder()
                .usage(new DefaultUsage(10, totalTokens - 10, totalTokens))
                .build();
        return new ChatResponse(List.of(new Generation(assistantMessage, generationMetadata)), responseMetadata);
    }

    private ChatResponse toolChunk(String content,
                                   AssistantMessage.ToolCall toolCall,
                                   String finishReason,
                                   Integer totalTokens) {
        return reasoningChunk(content, null, toolCall, finishReason, totalTokens);
    }

    private static class RecordingPrinter implements Printer {
        private final List<PrinterMessage> messages = new ArrayList<>();

        @Override
        public void send(String messageId, String messageType, Object message, String digitalEmployee, Boolean isFinal) {
            messages.add(new PrinterMessage(messageId, messageType, message, isFinal));
        }

        @Override
        public void send(String messageId, String messageType, Object message, Map<String, Object> extraResultMap, String digitalEmployee, Boolean isFinal) {
            messages.add(new PrinterMessage(messageId, messageType, message, isFinal));
        }

        @Override
        public void send(String messageType, Object message) {
            messages.add(new PrinterMessage(null, messageType, message, true));
        }

        @Override
        public void send(String messageType, Object message, String digitalEmployee) {
            messages.add(new PrinterMessage(null, messageType, message, true));
        }

        @Override
        public void send(String messageId, String messageType, Object message, Boolean isFinal) {
            messages.add(new PrinterMessage(messageId, messageType, message, isFinal));
        }

        @Override
        public void sendWithResultMap(String messageId, String messageType, Object message, Map<String, Object> extraResultMap, Boolean isFinal) {
            messages.add(new PrinterMessage(messageId, messageType, message, isFinal));
        }

        @Override
        public void sendWithResultMap(String messageType, Object message, Map<String, Object> extraResultMap) {
            messages.add(new PrinterMessage(null, messageType, message, true));
        }

        @Override
        public void close() {
        }

        @Override
        public void updateAgentType(AgentType agentType) {
        }
    }

    private record PrinterMessage(String messageId, String messageType, Object message, Boolean isFinal) {
    }
}
