package org.wwz.ai.domain.agent.runtime.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 将 Spring AI {@link Prompt} + {@link OpenAiChatOptions} 序列化为 OpenAI 兼容
 * {@code /chat/completions} JSON 请求体。
 * <p>
 * 配置 / tools / options 仍来自 Spring AI 装配；仅序列化层旁路 {@code OpenAiApi}
 * 的 tool-call window 合并，供直解 SSE delta 使用。
 */
@Slf4j
@Component
public class OpenAiCompatibleChatCompletionRequestFactory {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构造流式 chat/completions 请求 JSON。
     */
    public String buildStreamBody(Prompt prompt, OpenAiChatOptions options) {
        Objects.requireNonNull(prompt, "prompt must not be null");
        Objects.requireNonNull(options, "options must not be null");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", options.getModel());
        body.put("messages", expandMessages(prompt.getInstructions()));
        body.put("stream", true);
        // 尽量让网关在最后一帧带回 usage，便于账本落库
        body.put("stream_options", Map.of("include_usage", true));

        putIfNotNull(body, "temperature", options.getTemperature());
        putIfNotNull(body, "top_p", options.getTopP());
        putIfNotNull(body, "max_tokens", options.getMaxTokens());
        putIfNotNull(body, "max_completion_tokens", options.getMaxCompletionTokens());
        putIfNotNull(body, "frequency_penalty", options.getFrequencyPenalty());
        putIfNotNull(body, "presence_penalty", options.getPresencePenalty());
        putIfNotNull(body, "seed", options.getSeed());
        putIfNotNull(body, "user", options.getUser());
        putIfNotNull(body, "store", options.getStore());
        putIfNotNull(body, "reasoning_effort", options.getReasoningEffort());
        putIfNotNull(body, "verbosity", options.getVerbosity());
        putIfNotNull(body, "service_tier", options.getServiceTier());
        putIfNotNull(body, "parallel_tool_calls", options.getParallelToolCalls());

        List<String> stop = options.getStop();
        if (stop != null && !stop.isEmpty()) {
            body.put("stop", stop);
        }

        List<Map<String, Object>> tools = toTools(options.getToolCallbacks());
        if (!tools.isEmpty()) {
            body.put("tools", tools);
        }
        if (options.getToolChoice() != null) {
            body.put("tool_choice", options.getToolChoice());
        }

        Map<String, Object> extraBody = options.getExtraBody();
        if (extraBody != null && !extraBody.isEmpty()) {
            // 与 Spring AI 一致：extraBody 覆盖同名字段
            body.putAll(extraBody);
            // 强制保持 stream=true，避免 extraBody 误关流
            body.put("stream", true);
        }

        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize OpenAI-compatible chat completion body", e);
        }
    }

    /**
     * 展开 ToolResponseMessage 的多条 response，避免丢 tool 结果。
     */
    List<Map<String, Object>> expandMessages(List<Message> messages) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (messages == null) {
            return result;
        }
        for (Message message : messages) {
            if (message == null) {
                continue;
            }
            if (message instanceof ToolResponseMessage toolResponseMessage) {
                List<ToolResponseMessage.ToolResponse> responses = toolResponseMessage.getResponses();
                if (responses == null || responses.isEmpty()) {
                    continue;
                }
                for (ToolResponseMessage.ToolResponse response : responses) {
                    Map<String, Object> row = mapToolResponse(response);
                    if (row != null) {
                        result.add(row);
                    }
                }
                continue;
            }
            Map<String, Object> mapped = mapMessage(message);
            if (mapped != null) {
                result.add(mapped);
            }
        }
        return result;
    }

    private Map<String, Object> mapMessage(Message message) {
        if (message instanceof SystemMessage systemMessage) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("role", "system");
            row.put("content", StringUtils.defaultString(systemMessage.getText()));
            return row;
        }
        if (message instanceof UserMessage userMessage) {
            return mapUserMessage(userMessage);
        }
        if (message instanceof AssistantMessage assistantMessage) {
            return mapAssistantMessage(assistantMessage);
        }
        log.warn("Skip unsupported Spring AI message type: {}", message.getClass().getName());
        return null;
    }

    private Map<String, Object> mapUserMessage(UserMessage userMessage) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("role", "user");
        List<Media> mediaList = userMessage.getMedia();
        if (mediaList == null || mediaList.isEmpty()) {
            row.put("content", StringUtils.defaultString(userMessage.getText()));
            return row;
        }
        List<Map<String, Object>> parts = new ArrayList<>();
        String text = userMessage.getText();
        if (StringUtils.isNotEmpty(text)) {
            Map<String, Object> textPart = new LinkedHashMap<>();
            textPart.put("type", "text");
            textPart.put("text", text);
            parts.add(textPart);
        }
        for (Media media : mediaList) {
            Map<String, Object> imagePart = toImagePart(media);
            if (imagePart != null) {
                parts.add(imagePart);
            }
        }
        row.put("content", parts.isEmpty() ? StringUtils.defaultString(text) : parts);
        return row;
    }

    private Map<String, Object> mapAssistantMessage(AssistantMessage assistantMessage) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("role", "assistant");
        String text = assistantMessage.getText();
        if (StringUtils.isNotEmpty(text)) {
            row.put("content", text);
        } else {
            row.put("content", null);
        }
        Object reasoning = firstMetadata(assistantMessage.getMetadata(),
                ReasoningContentExtractor.METADATA_REASONING_CONTENT,
                ReasoningContentExtractor.METADATA_REASONING,
                "reasoning_content");
        if (reasoning != null && StringUtils.isNotBlank(String.valueOf(reasoning))) {
            row.put("reasoning_content", String.valueOf(reasoning));
        }
        List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();
        if (toolCalls != null && !toolCalls.isEmpty()) {
            List<Map<String, Object>> encoded = new ArrayList<>();
            for (AssistantMessage.ToolCall toolCall : toolCalls) {
                if (toolCall == null) {
                    continue;
                }
                Map<String, Object> function = new LinkedHashMap<>();
                function.put("name", StringUtils.defaultString(toolCall.name()));
                function.put("arguments", StringUtils.defaultString(toolCall.arguments()));
                Map<String, Object> call = new LinkedHashMap<>();
                call.put("id", toolCall.id());
                call.put("type", StringUtils.defaultIfBlank(toolCall.type(), "function"));
                call.put("function", function);
                encoded.add(call);
            }
            if (!encoded.isEmpty()) {
                row.put("tool_calls", encoded);
            }
        }
        return row;
    }

    private Map<String, Object> mapToolResponse(ToolResponseMessage.ToolResponse response) {
        if (response == null) {
            return null;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("role", "tool");
        row.put("tool_call_id", StringUtils.defaultString(response.id()));
        if (StringUtils.isNotBlank(response.name())) {
            row.put("name", response.name());
        }
        row.put("content", StringUtils.defaultString(response.responseData()));
        return row;
    }

    private List<Map<String, Object>> toTools(List<ToolCallback> callbacks) {
        List<Map<String, Object>> tools = new ArrayList<>();
        if (callbacks == null || callbacks.isEmpty()) {
            return tools;
        }
        for (ToolCallback callback : callbacks) {
            if (callback == null || callback.getToolDefinition() == null) {
                continue;
            }
            ToolDefinition definition = callback.getToolDefinition();
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", definition.name());
            function.put("description", StringUtils.defaultString(definition.description()));
            function.put("parameters", parseSchema(definition.inputSchema()));
            Map<String, Object> tool = new LinkedHashMap<>();
            tool.put("type", "function");
            tool.put("function", function);
            tools.add(tool);
        }
        return tools;
    }

    private Object parseSchema(String inputSchema) {
        if (StringUtils.isBlank(inputSchema)) {
            return Map.of("type", "object", "properties", Map.of());
        }
        try {
            JsonNode node = objectMapper.readTree(inputSchema);
            return objectMapper.convertValue(node, Object.class);
        } catch (Exception e) {
            log.warn("Failed to parse tool inputSchema, fallback empty object: {}", e.getMessage());
            return Map.of("type", "object", "properties", Map.of());
        }
    }

    private Map<String, Object> toImagePart(Media media) {
        if (media == null) {
            return null;
        }
        try {
            byte[] bytes = readMediaBytes(media);
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            MimeType mimeType = media.getMimeType();
            String mime = mimeType == null ? "image/jpeg" : mimeType.toString();
            String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
            Map<String, Object> imageUrl = new LinkedHashMap<>();
            imageUrl.put("url", dataUrl);
            Map<String, Object> part = new LinkedHashMap<>();
            part.put("type", "image_url");
            part.put("image_url", imageUrl);
            return part;
        } catch (Exception e) {
            log.warn("Skip media part: {}", e.getMessage());
            return null;
        }
    }

    private byte[] readMediaBytes(Media media) {
        try {
            byte[] asArray = media.getDataAsByteArray();
            if (asArray != null && asArray.length > 0) {
                return asArray;
            }
        } catch (Exception ignored) {
            // fall through
        }
        Object data = media.getData();
        if (data instanceof byte[] bytes) {
            return bytes;
        }
        if (data instanceof String stringData) {
            String normalized = stringData.trim();
            if (normalized.startsWith("data:") && normalized.contains(",")) {
                normalized = normalized.substring(normalized.indexOf(',') + 1);
            }
            return Base64.getDecoder().decode(normalized);
        }
        return null;
    }

    private Object firstMetadata(Map<String, Object> metadata, String... keys) {
        if (metadata == null || metadata.isEmpty() || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (metadata.containsKey(key) && metadata.get(key) != null) {
                return metadata.get(key);
            }
        }
        return null;
    }

    private void putIfNotNull(Map<String, Object> body, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String stringValue && StringUtils.isBlank(stringValue)) {
            return;
        }
        if (value instanceof Collection<?> collection && collection.isEmpty()) {
            return;
        }
        body.put(key, value);
    }
}
