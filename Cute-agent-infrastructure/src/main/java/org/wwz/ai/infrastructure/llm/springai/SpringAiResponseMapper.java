package org.wwz.ai.infrastructure.llm.springai;

import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.runtime.llm.LlmResponse;
import org.wwz.ai.domain.agent.runtime.llm.LlmStreamEvent;
import org.wwz.ai.domain.agent.runtime.llm.LlmToolCall;
import org.wwz.ai.domain.agent.runtime.llm.LlmUsage;
import org.wwz.ai.domain.agent.runtime.llm.LlmUsageSnapshot;
import org.wwz.ai.domain.agent.runtime.llm.ReasoningContentExtractor;
import org.wwz.ai.domain.agent.runtime.util.StringUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 将 Spring AI 响应转换为 Domain completion/stream 模型。
 */
@Slf4j
@Component
public class SpringAiResponseMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public LlmResponse toResponse(ChatResponse response) {
        Generation generation = requireGeneration(response);
        AssistantMessage output = generation.getOutput();
        return LlmResponse.builder()
                .content(sanitize(output.getText()))
                .reasoningContent(resolveReasoning(output, response))
                .toolCalls(toToolCalls(output, true))
                .finishReason(generation.getMetadata() == null ? null : generation.getMetadata().getFinishReason())
                .usage(toUsage(response == null ? null : response.getMetadata()))
                .build();
    }

    public LlmStreamEvent toStreamEvent(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return LlmStreamEvent.builder().usage(toUsage(response == null ? null : response.getMetadata())).build();
        }
        Generation generation = response.getResult();
        AssistantMessage output = generation.getOutput();
        return LlmStreamEvent.builder()
                .content(output.getText())
                .reasoningContent(resolveReasoning(output, response))
                .toolCalls(toToolCalls(output, false))
                .finishReason(generation.getMetadata() == null ? null : generation.getMetadata().getFinishReason())
                .usage(toUsage(response.getMetadata()))
                .build();
    }

    private Generation requireGeneration(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalArgumentException("Empty or invalid response from LLM");
        }
        return response.getResult();
    }

    private List<LlmToolCall> toToolCalls(AssistantMessage output, boolean normalizeArguments) {
        List<LlmToolCall> toolCalls = new ArrayList<>();
        if (output == null || output.getToolCalls() == null) {
            return toolCalls;
        }
        for (AssistantMessage.ToolCall toolCall : output.getToolCalls()) {
            if (toolCall == null || StringUtils.isBlank(toolCall.name())) {
                log.warn("skip invalid Spring AI tool call: {}", toolCall);
                continue;
            }
            toolCalls.add(LlmToolCall.builder()
                    .id(StringUtils.defaultIfBlank(toolCall.id(), StringUtil.getUUID()))
                    .type(toolCall.type())
                    .name(toolCall.name())
                    .arguments(normalizeArguments ? normalizeArguments(toolCall.arguments()) : toolCall.arguments())
                    .build());
        }
        return toolCalls;
    }

    private String normalizeArguments(String arguments) {
        if (StringUtils.isBlank(arguments)) {
            return "{}";
        }
        String value = arguments.trim();
        if (value.startsWith("\"") && value.endsWith("\"")) {
            try {
                value = OBJECT_MAPPER.readValue(value, String.class);
            } catch (Exception ignored) {
            }
        }
        if ((value.startsWith("{") && value.endsWith("}")) || (value.startsWith("[") && value.endsWith("]"))) {
            return value;
        }
        try {
            JsonNode parsed = OBJECT_MAPPER.readTree(value);
            return parsed.toString();
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private String resolveReasoning(AssistantMessage output, ChatResponse response) {
        String reasoning = output == null ? null : ReasoningContentExtractor.extractReasoningMetadata(output.getMetadata());
        if (StringUtils.isNotBlank(reasoning)) {
            return reasoning;
        }
        ChatResponseMetadata metadata = response == null ? null : response.getMetadata();
        if (metadata == null) {
            return null;
        }
        Object value = metadata.get(ReasoningContentExtractor.METADATA_REASONING_CONTENT);
        return value == null ? null : String.valueOf(value);
    }

    private LlmUsage toUsage(ChatResponseMetadata metadata) {
        if (metadata == null) {
            return LlmUsage.empty();
        }
        Usage usage = metadata.getUsage();
        Object nativeUsage = usage == null ? null : usage.getNativeUsage();
        if (nativeUsage == null) {
            nativeUsage = metadata.get("usage");
        }
        LlmUsage mapped = LlmUsage.builder().nativeUsage(nativeUsage)
                .promptTokens(usage == null ? null : usage.getPromptTokens())
                .completionTokens(usage == null ? null : usage.getCompletionTokens())
                .totalTokens(usage == null ? null : usage.getTotalTokens())
                .build();
        LlmUsageSnapshot snapshot = LlmUsageSnapshot.resolve(mapped);
        mapped.setPromptTokens(snapshot.getPromptTokens());
        mapped.setCompletionTokens(snapshot.getCompletionTokens());
        mapped.setTotalTokens(snapshot.getTotalTokens());
        mapped.setCachedPromptTokens(snapshot.getCachedPromptTokens());
        mapped.setPromptTextTokens(snapshot.getPromptTextTokens());
        mapped.setPromptAudioTokens(snapshot.getPromptAudioTokens());
        mapped.setPromptImageTokens(snapshot.getPromptImageTokens());
        mapped.setCompletionTextTokens(snapshot.getCompletionTextTokens());
        mapped.setCompletionAudioTokens(snapshot.getCompletionAudioTokens());
        mapped.setReasoningTokens(snapshot.getReasoningTokens());
        return mapped;
    }

    private String sanitize(String content) {
        return content == null || "null".equals(content) ? null : content;
    }
}
