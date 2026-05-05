package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.wwz.ai.domain.agent.runtime.llm.LLM;
import org.wwz.ai.domain.agent.runtime.llm.LlmChatResponseMapper;

import java.util.List;

/**
 * LlmChatResponseMapper 测试
 */
public class LlmChatResponseMapperTest {

    @Test
    public void test_toTextReadsAssistantContent() {
        LlmChatResponseMapper mapper = new LlmChatResponseMapper();
        ChatResponse response = buildChatResponse(
                AssistantMessage.builder().content("最终答案").properties(java.util.Map.of()).build(),
                "stop",
                18
        );

        Assert.assertEquals("最终答案", mapper.toText(response));
    }

    @Test
    public void test_toToolCallResponseNormalizesArgumentsAndUsage() {
        LlmChatResponseMapper mapper = new LlmChatResponseMapper();
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

        LLM.ToolCallResponse response = mapper.toToolCallResponse(
                buildChatResponse(assistantMessage, "tool_calls", 36),
                System.currentTimeMillis() - 5
        );

        Assert.assertEquals("我需要调用工具", response.getContent());
        Assert.assertEquals("tool_calls", response.getFinishReason());
        Assert.assertEquals(Integer.valueOf(36), response.getTotalTokens());
        Assert.assertEquals(1, response.getToolCalls().size());
        Assert.assertEquals("deep_search", response.getToolCalls().get(0).getFunction().getName());
        Assert.assertEquals("{\"query\":\"spring ai\"}", response.getToolCalls().get(0).getFunction().getArguments());
    }

    @Test
    public void test_normalizeToolArgumentsFallsBackToEmptyObject() {
        LlmChatResponseMapper mapper = new LlmChatResponseMapper();
        Assert.assertEquals("{}", mapper.normalizeToolArguments("not-json"));
        Assert.assertEquals("{}", mapper.normalizeToolArguments(""));
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
