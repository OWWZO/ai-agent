package org.wwz.ai.domain.agent.runtime.llm;

import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;

import java.lang.reflect.RecordComponent;
import java.util.Map;

/**
 * LLM 接口返回的 usage 快照，优先取原生响应字段。
 *
 * <pre>
 * usage: {
 *   prompt_tokens, completion_tokens, total_tokens,
 *   prompt_tokens_details: { cached_tokens, text_tokens, audio_tokens, image_tokens },
 *   completion_tokens_details: { text_tokens, audio_tokens, reasoning_tokens }
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmUsageSnapshot {

    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Integer cachedPromptTokens;
    private Integer promptTextTokens;
    private Integer promptAudioTokens;
    private Integer promptImageTokens;
    private Integer completionTextTokens;
    private Integer completionAudioTokens;
    private Integer reasoningTokens;

    public static LlmUsageSnapshot empty() {
        return new LlmUsageSnapshot();
    }

    /**
     * 优先从接口原生 usage 解析；缺失字段再回退 Spring AI Usage getter。
     */
    public static LlmUsageSnapshot resolve(ChatResponseMetadata metadata) {
        if (metadata == null) {
            return empty();
        }
        LlmUsageSnapshot fromNative = empty();
        LlmUsageSnapshot fromMetaKey = empty();
        Usage usage = metadata.getUsage();
        if (usage != null) {
            try {
                fromNative = fromObject(usage.getNativeUsage());
            } catch (Exception ignored) {
            }
        }
        try {
            fromMetaKey = fromObject(metadata.get("usage"));
        } catch (Exception ignored) {
        }
        // 原生接口字段优先于 metadata 旁路字段
        LlmUsageSnapshot snapshot = mergePreferPrimary(fromMetaKey, fromNative);
        if (usage != null) {
            if (snapshot.getPromptTokens() == null) {
                snapshot.setPromptTokens(usage.getPromptTokens());
            }
            if (snapshot.getCompletionTokens() == null) {
                snapshot.setCompletionTokens(usage.getCompletionTokens());
            }
            if (snapshot.getTotalTokens() == null) {
                snapshot.setTotalTokens(usage.getTotalTokens());
            }
        }
        fillTotalIfMissing(snapshot);
        return snapshot;
    }

    public static LlmUsageSnapshot fromJsonNode(JsonNode usageNode) {
        if (usageNode == null || usageNode.isNull() || !usageNode.isObject()) {
            return empty();
        }
        LlmUsageSnapshot snapshot = empty();
        snapshot.setPromptTokens(firstInt(usageNode, "prompt_tokens", "input_tokens", "promptTokens"));
        snapshot.setCompletionTokens(firstInt(usageNode, "completion_tokens", "output_tokens", "completionTokens"));
        snapshot.setTotalTokens(firstInt(usageNode, "total_tokens", "totalTokens"));

        JsonNode promptDetails = firstNode(usageNode, "prompt_tokens_details", "input_tokens_details", "promptTokensDetails");
        if (promptDetails != null && promptDetails.isObject()) {
            snapshot.setCachedPromptTokens(firstInt(promptDetails, "cached_tokens", "cachedTokens"));
            snapshot.setPromptTextTokens(firstInt(promptDetails, "text_tokens", "textTokens"));
            snapshot.setPromptAudioTokens(firstInt(promptDetails, "audio_tokens", "audioTokens"));
            snapshot.setPromptImageTokens(firstInt(promptDetails, "image_tokens", "imageTokens"));
        }
        if (snapshot.getCachedPromptTokens() == null) {
            snapshot.setCachedPromptTokens(firstInt(usageNode,
                    "cached_tokens", "cachedTokens", "cache_read_input_tokens", "cacheReadInputTokens"));
        }

        JsonNode completionDetails = firstNode(usageNode,
                "completion_tokens_details", "output_tokens_details", "completionTokensDetails");
        if (completionDetails != null && completionDetails.isObject()) {
            snapshot.setCompletionTextTokens(firstInt(completionDetails, "text_tokens", "textTokens"));
            snapshot.setCompletionAudioTokens(firstInt(completionDetails, "audio_tokens", "audioTokens"));
            snapshot.setReasoningTokens(firstInt(completionDetails, "reasoning_tokens", "reasoningTokens"));
        }
        if (snapshot.getReasoningTokens() == null) {
            snapshot.setReasoningTokens(firstInt(usageNode, "reasoning_tokens", "reasoningTokens"));
        }
        fillTotalIfMissing(snapshot);
        return snapshot;
    }

    public static LlmUsageSnapshot fromObject(Object raw) {
        if (raw == null) {
            return empty();
        }
        if (raw instanceof JsonNode node) {
            return fromJsonNode(node);
        }
        if (raw.getClass().isRecord()) {
            return fromObject(recordToMap(raw));
        }
        if (raw instanceof Map<?, ?> map) {
            LlmUsageSnapshot snapshot = empty();
            snapshot.setPromptTokens(firstNumber(map, "prompt_tokens", "input_tokens", "promptTokens"));
            snapshot.setCompletionTokens(firstNumber(map, "completion_tokens", "output_tokens", "completionTokens"));
            snapshot.setTotalTokens(firstNumber(map, "total_tokens", "totalTokens"));

            Object promptDetails = normalizeStructuredObject(
                    firstValue(map, "prompt_tokens_details", "input_tokens_details", "promptTokensDetails"));
            if (promptDetails instanceof Map<?, ?> details) {
                snapshot.setCachedPromptTokens(firstNumber(details, "cached_tokens", "cachedTokens"));
                snapshot.setPromptTextTokens(firstNumber(details, "text_tokens", "textTokens"));
                snapshot.setPromptAudioTokens(firstNumber(details, "audio_tokens", "audioTokens"));
                snapshot.setPromptImageTokens(firstNumber(details, "image_tokens", "imageTokens"));
            }
            if (snapshot.getCachedPromptTokens() == null) {
                snapshot.setCachedPromptTokens(firstNumber(map,
                        "cached_tokens", "cachedTokens", "cache_read_input_tokens", "cacheReadInputTokens"));
            }

            Object completionDetails = normalizeStructuredObject(firstValue(map,
                    "completion_tokens_details", "output_tokens_details", "completionTokensDetails", "completionTokenDetails"));
            if (completionDetails instanceof Map<?, ?> details) {
                snapshot.setCompletionTextTokens(firstNumber(details, "text_tokens", "textTokens"));
                snapshot.setCompletionAudioTokens(firstNumber(details, "audio_tokens", "audioTokens"));
                snapshot.setReasoningTokens(firstNumber(details, "reasoning_tokens", "reasoningTokens"));
            }
            if (snapshot.getReasoningTokens() == null) {
                snapshot.setReasoningTokens(firstNumber(map, "reasoning_tokens", "reasoningTokens"));
            }
            fillTotalIfMissing(snapshot);
            return snapshot;
        }
        try {
            Object parsed = JSON.parse(JSON.toJSONString(raw));
            if (parsed instanceof Map<?, ?> || parsed instanceof JsonNode) {
                return fromObject(parsed);
            }
        } catch (Exception ignored) {
        }
        return empty();
    }

    private static Map<String, Object> recordToMap(Object record) {
        Map<String, Object> values = new java.util.LinkedHashMap<>();
        for (RecordComponent component : record.getClass().getRecordComponents()) {
            try {
                values.put(component.getName(), component.getAccessor().invoke(record));
            } catch (Exception ignored) {
            }
        }
        return values;
    }

    private static Object normalizeStructuredObject(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Map<?, ?> || raw instanceof JsonNode) {
            return raw;
        }
        if (raw.getClass().isRecord()) {
            return recordToMap(raw);
        }
        return raw;
    }

    /**
     * 流式场景：用新快照覆盖旧值中仍为空的字段；新快照非空字段优先。
     */
    public LlmUsageSnapshot mergeLatest(LlmUsageSnapshot incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return this;
        }
        return mergePreferPrimary(this, incoming);
    }

    public boolean isEmpty() {
        return promptTokens == null
                && completionTokens == null
                && totalTokens == null
                && cachedPromptTokens == null
                && promptTextTokens == null
                && promptAudioTokens == null
                && promptImageTokens == null
                && completionTextTokens == null
                && completionAudioTokens == null
                && reasoningTokens == null;
    }

    private static LlmUsageSnapshot mergePreferPrimary(LlmUsageSnapshot base, LlmUsageSnapshot primary) {
        if (primary == null || primary.isEmpty()) {
            return base == null ? empty() : base;
        }
        if (base == null || base.isEmpty()) {
            return copyOf(primary);
        }
        LlmUsageSnapshot out = copyOf(base);
        boolean promptOrCompletionUpdated = false;
        if (primary.getPromptTokens() != null) {
            out.setPromptTokens(primary.getPromptTokens());
            promptOrCompletionUpdated = true;
        }
        if (primary.getCompletionTokens() != null) {
            out.setCompletionTokens(primary.getCompletionTokens());
            promptOrCompletionUpdated = true;
        }
        if (primary.getTotalTokens() != null) {
            out.setTotalTokens(primary.getTotalTokens());
        } else if (promptOrCompletionUpdated
                && out.getPromptTokens() != null
                && out.getCompletionTokens() != null) {
            // 流式后到的 usage 可能只带 prompt/completion，重算 total 覆盖旧值
            out.setTotalTokens(out.getPromptTokens() + out.getCompletionTokens());
        }
        if (primary.getCachedPromptTokens() != null) {
            out.setCachedPromptTokens(primary.getCachedPromptTokens());
        }
        if (primary.getPromptTextTokens() != null) {
            out.setPromptTextTokens(primary.getPromptTextTokens());
        }
        if (primary.getPromptAudioTokens() != null) {
            out.setPromptAudioTokens(primary.getPromptAudioTokens());
        }
        if (primary.getPromptImageTokens() != null) {
            out.setPromptImageTokens(primary.getPromptImageTokens());
        }
        if (primary.getCompletionTextTokens() != null) {
            out.setCompletionTextTokens(primary.getCompletionTextTokens());
        }
        if (primary.getCompletionAudioTokens() != null) {
            out.setCompletionAudioTokens(primary.getCompletionAudioTokens());
        }
        if (primary.getReasoningTokens() != null) {
            out.setReasoningTokens(primary.getReasoningTokens());
        }
        fillTotalIfMissing(out);
        return out;
    }

    private static LlmUsageSnapshot copyOf(LlmUsageSnapshot src) {
        if (src == null) {
            return empty();
        }
        return LlmUsageSnapshot.builder()
                .promptTokens(src.getPromptTokens())
                .completionTokens(src.getCompletionTokens())
                .totalTokens(src.getTotalTokens())
                .cachedPromptTokens(src.getCachedPromptTokens())
                .promptTextTokens(src.getPromptTextTokens())
                .promptAudioTokens(src.getPromptAudioTokens())
                .promptImageTokens(src.getPromptImageTokens())
                .completionTextTokens(src.getCompletionTextTokens())
                .completionAudioTokens(src.getCompletionAudioTokens())
                .reasoningTokens(src.getReasoningTokens())
                .build();
    }

    private static void fillTotalIfMissing(LlmUsageSnapshot snapshot) {
        if (snapshot == null || snapshot.getTotalTokens() != null) {
            return;
        }
        if (snapshot.getPromptTokens() != null && snapshot.getCompletionTokens() != null) {
            snapshot.setTotalTokens(snapshot.getPromptTokens() + snapshot.getCompletionTokens());
        }
    }

    private static Integer firstNumber(Map<?, ?> map, String... keys) {
        Object value = firstValue(map, keys);
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private static Object firstValue(Map<?, ?> map, String... keys) {
        if (map == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }

    private static Integer firstInt(JsonNode node, String... keys) {
        JsonNode value = firstNode(node, keys);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.asInt();
        }
        if (value.isTextual()) {
            try {
                return Integer.parseInt(value.asText().trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private static JsonNode firstNode(JsonNode node, String... keys) {
        if (node == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (node.has(key)) {
                return node.get(key);
            }
        }
        return null;
    }
}
