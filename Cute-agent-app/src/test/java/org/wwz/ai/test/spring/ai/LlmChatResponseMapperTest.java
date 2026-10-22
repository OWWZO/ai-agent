package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.wwz.ai.domain.agent.runtime.llm.LlmResponse;
import org.wwz.ai.infrastructure.llm.springai.SpringAiResponseMapper;

import java.util.List;

/**
 * LlmChatResponseMapper 测试
 */
public class LlmChatResponseMapperTest {

    @Test
    public void test_toTextReadsAssistantContent() {
        SpringAiResponseMapper mapper = new SpringAiResponseMapper();
        ChatResponse response = buildChatResponse(
                AssistantMessage.builder().content("最终答案").properties(java.util.Map.of()).build(),
                "stop",
                18
        );

        Assert.assertEquals("最终答案", mapper.toResponse(response).getContent());
    }

    @Test
    public void test_toToolCallResponseNormalizesArgumentsAndUsage() {
        SpringAiResponseMapper mapper = new SpringAiResponseMapper();
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content("我需要调用工具")
                .properties(java.util.Map.of())
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call-1",
                        "function",
                        "deep_search",
                        "\"{\\\"query\\\":\\\"spring ai\\\"}\""
                )))
                .build();

        LlmResponse response = mapper.toResponse(buildChatResponse(assistantMessage, "tool_calls", 36));

        Assert.assertEquals("我需要调用工具", response.getContent());
        Assert.assertEquals("tool_calls", response.getFinishReason());
        Assert.assertEquals(Integer.valueOf(36), response.getUsage().getTotalTokens());
        Assert.assertEquals(Integer.valueOf(10), response.getUsage().getPromptTokens());
        Assert.assertEquals(Integer.valueOf(26), response.getUsage().getCompletionTokens());
        Assert.assertEquals(1, response.getToolCalls().size());
        Assert.assertEquals("deep_search", response.getToolCalls().get(0).getName());
        Assert.assertEquals("{\"query\":\"spring ai\"}", response.getToolCalls().get(0).getArguments());
    }

    @Test
    public void test_toToolCallResponseReadsNativeCachedAndDetails() {
        SpringAiResponseMapper mapper = new SpringAiResponseMapper();
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content("ok")
                .properties(java.util.Map.of())
                .build();
        java.util.Map<String, Object> promptDetails = new java.util.LinkedHashMap<>();
        promptDetails.put("cached_tokens", 7);
        promptDetails.put("text_tokens", 80);
        java.util.Map<String, Object> completionDetails = new java.util.LinkedHashMap<>();
        completionDetails.put("reasoning_tokens", 5);
        java.util.Map<String, Object> nativeUsage = new java.util.LinkedHashMap<>();
        nativeUsage.put("prompt_tokens", 80);
        nativeUsage.put("completion_tokens", 20);
        nativeUsage.put("total_tokens", 100);
        nativeUsage.put("prompt_tokens_details", promptDetails);
        nativeUsage.put("completion_tokens_details", completionDetails);

        ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.builder()
                .finishReason("stop")
                .build();
        ChatResponseMetadata responseMetadata = ChatResponseMetadata.builder()
                .usage(new DefaultUsage(1, 2, 3, nativeUsage))
                .build();
        ChatResponse chatResponse = new ChatResponse(
                List.of(new Generation(assistantMessage, generationMetadata)),
                responseMetadata
        );

        LlmResponse response = mapper.toResponse(chatResponse);
        Assert.assertEquals(Integer.valueOf(80), response.getUsage().getPromptTokens());
        Assert.assertEquals(Integer.valueOf(20), response.getUsage().getCompletionTokens());
        Assert.assertEquals(Integer.valueOf(100), response.getUsage().getTotalTokens());
        Assert.assertEquals(Integer.valueOf(7), response.getUsage().getCachedPromptTokens());
        Assert.assertEquals(Integer.valueOf(80), response.getUsage().getPromptTextTokens());
        Assert.assertEquals(Integer.valueOf(5), response.getUsage().getReasoningTokens());
    }

    @Test
    public void test_normalizeToolArgumentsFallsBackToEmptyObject() {
        SpringAiResponseMapper mapper = new SpringAiResponseMapper();
        Assert.assertEquals("{}", mapper.toResponse(buildChatResponse(
                AssistantMessage.builder().content("test").properties(java.util.Map.of())
                        .toolCalls(List.of(new AssistantMessage.ToolCall("invalid", "function", "test", "not-json")))
                        .build(), "tool_calls", 10)).getToolCalls().get(0).getArguments());
        Assert.assertEquals("{}", mapper.toResponse(buildChatResponse(
                AssistantMessage.builder().content("test").properties(java.util.Map.of())
                        .toolCalls(List.of(new AssistantMessage.ToolCall("empty", "function", "test", "")))
                        .build(), "tool_calls", 10)).getToolCalls().get(0).getArguments());
    }

    private ChatResponse buildChatResponse(AssistantMessage assistantMessage, String finishReason, int totalTokens) {
        ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.builder()
                .finishReason(finishReason)
                .build();
        ChatResponseMetadata responseMetadata = ChatResponseMetadata.builder()
                .usage(new DefaultUsage(10, totalTokens - 10, totalTokens))
                .build();
        return new ChatResponse(List.of(new Generation(assistantMessage, generationMetadata)), responseMetadata);
    }
}
