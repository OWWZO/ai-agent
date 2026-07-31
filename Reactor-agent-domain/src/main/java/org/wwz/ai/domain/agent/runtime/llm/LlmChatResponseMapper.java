package org.wwz.ai.domain.agent.runtime.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;

import java.util.ArrayList;
import java.util.List;

/**
 * 将 Spring AI ChatResponse 映射回现有 LLM 返回契约。
 */
@Slf4j
@Component
public class LlmChatResponseMapper {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 提取纯文本响应。
     */
    public String toText(ChatResponse response) {
        Generation generation = requireGeneration(response);
        String content = sanitizeContent(generation.getOutput().getText());
        if (content == null) {
            throw new IllegalArgumentException("Empty or invalid response from LLM");
        }
        return content;
    }

    /**
     * 映射工具调用响应。
     */
    public LLM.ToolCallResponse toToolCallResponse(ChatResponse response, long startTimeMs) {
        Generation generation = requireGeneration(response);
        AssistantMessage output = generation.getOutput();
        List<ToolCall> toolCalls = toToolCalls(output);
        LlmUsageSnapshot usage = LlmUsageSnapshot.resolve(response == null ? null : response.getMetadata());
        ReasoningContentExtractor.SplitResult split = ReasoningContentExtractor.splitFromChatResponse(response);
        String content = sanitizeContent(split.content());
        if (content == null) {
            content = sanitizeContent(output.getText());
        }
        return applyUsage(LLM.ToolCallResponse.builder()
                .content(content)
                .reasoningContent(sanitizeContent(split.reasoningContent()))
                .toolCalls(toolCalls)
                .finishReason(generation.getMetadata() != null ? generation.getMetadata().getFinishReason() : null)
                .duration(System.currentTimeMillis() - startTimeMs)
                .build(), usage);
    }

    public LLM.ToolCallResponse applyUsage(LLM.ToolCallResponse response, LlmUsageSnapshot usage) {
        if (response == null || usage == null) {
            return response;
        }
        response.setPromptTokens(usage.getPromptTokens());
        response.setCompletionTokens(usage.getCompletionTokens());
        response.setTotalTokens(usage.getTotalTokens());
        response.setCachedPromptTokens(usage.getCachedPromptTokens());
        response.setPromptTextTokens(usage.getPromptTextTokens());
        response.setPromptAudioTokens(usage.getPromptAudioTokens());
        response.setPromptImageTokens(usage.getPromptImageTokens());
        response.setCompletionTextTokens(usage.getCompletionTextTokens());
        response.setCompletionAudioTokens(usage.getCompletionAudioTokens());
        response.setReasoningTokens(usage.getReasoningTokens());
        return response;
    }

    /**
     * 将 Spring AI ToolCall 结构映射回领域 ToolCall。
     */
    public List<ToolCall> toToolCalls(AssistantMessage output) {
        List<ToolCall> toolCalls = new ArrayList<>();
        if (output == null || output.getToolCalls() == null || output.getToolCalls().isEmpty()) {
            return toolCalls;
        }

        for (AssistantMessage.ToolCall toolCall : output.getToolCalls()) {
            if (toolCall == null || StringUtils.isBlank(toolCall.name())) {
                log.warn("skip invalid spring-ai tool call: {}", toolCall);
                continue;
            }
            toolCalls.add(ToolCall.builder()
                    .id(toolCall.id())
                    .type(toolCall.type())
                    .function(ToolCall.Function.builder()
                            .name(toolCall.name())
                            .arguments(normalizeToolArguments(toolCall.arguments()))
                            .build())
                    .build());
        }
        return toolCalls;
    }

    /**
     * 规范化 tool arguments，兼容字符串包裹 JSON 与对象 JSON。
     */
    public String normalizeToolArguments(String arguments) {
        if (StringUtils.isBlank(arguments)) {
            return "{}";
        }

        String normalized = arguments.trim();
        if (normalized.startsWith("\"") && normalized.endsWith("\"")) {
            try {
                normalized = objectMapper.readValue(normalized, String.class);
            } catch (Exception ignore) {
            }
        }
        if (StringUtils.isBlank(normalized)) {
            return "{}";
        }

        normalized = normalized.trim();
        if ((normalized.startsWith("{") && normalized.endsWith("}"))
                || (normalized.startsWith("[") && normalized.endsWith("]"))) {
            return normalized;
        }

        try {
            JsonNode parsed = objectMapper.readTree(normalized);
            return parsed.toString();
        } catch (Exception ignore) {
            return "{}";
        }
    }

    private Generation requireGeneration(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalArgumentException("Empty or invalid response from LLM");
        }
        return response.getResult();
    }

    private String sanitizeContent(String content) {
        if (content == null) {
            return null;
        }
        if ("null".equals(content)) {
            return null;
        }
        return content;
    }
}
