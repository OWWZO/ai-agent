package org.wwz.ai.domain.agent.runtime.llm;

import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 Spring AI 响应中拆出 reasoning / content 两路（对齐 LeAgent）。
 * <p>
 * 优先读 OpenAI 兼容网关写入的 {@code reasoningContent} metadata；
 * 再兼容本地模型把 CoT 塞进 {@code <think>...</think>} 的 content 形态。
 * </p>
 */
public final class ReasoningContentExtractor {

    public static final String METADATA_REASONING_CONTENT = "reasoningContent";
    public static final String METADATA_REASONING = "reasoning";
    public static final String METADATA_THINKING = "thinking";

    public static final String EVENT_TYPE = "llm_reasoning";

    private static final Pattern THINK_BLOCK = Pattern.compile(
            "(?is)<think>(.*?)</think>");
    private static final String THINK_OPEN = "<think>";
    private static final String THINK_CLOSE = "</think>";

    private ReasoningContentExtractor() {
    }

    public record SplitResult(String content, String reasoningContent) {
        public static SplitResult empty() {
            return new SplitResult(null, null);
        }

        public boolean hasReasoning() {
            return StringUtils.isNotBlank(reasoningContent);
        }

        public boolean hasContent() {
            return StringUtils.isNotBlank(content);
        }
    }

    public static String extractFromChatResponse(ChatResponse response) {
        if (response == null || response.getResult() == null) {
            return null;
        }
        return extractFromGeneration(response.getResult());
    }

    public static String extractFromGeneration(Generation generation) {
        if (generation == null) {
            return null;
        }
        AssistantMessage output = generation.getOutput();
        if (output == null) {
            return null;
        }
        String fromMeta = firstNonEmpty(
                fromMap(output.getMetadata()),
                generation.getMetadata() == null ? null : fromObjectMap(generation.getMetadata()));
        return trimEndsToNull(fromMeta);
    }

    /**
     * 整轮结果拆分：可对最终全文 trim 两端。
     * 流式 delta 请用 {@link #extractDeltaReasoning(ChatResponse)}，禁止对 delta 做 trim（会吃掉词间空格）。
     */
    public static SplitResult split(String rawContent, String rawReasoning) {
        String reasoning = emptyToNull(rawReasoning);
        String content = rawContent;

        if (StringUtils.isNotEmpty(content)) {
            ThinkSplit thinkSplit = splitThinkTags(content);
            if (StringUtils.isNotEmpty(thinkSplit.reasoning())) {
                reasoning = joinReasoning(reasoning, thinkSplit.reasoning());
            }
            content = thinkSplit.content();
        }

        return new SplitResult(trimEndsToNull(content), trimEndsToNull(reasoning));
    }

    /**
     * 流式单 chunk 的 reasoning 增量：保留前导/尾随空格（token 常带 leading space）。
     */
    public static String extractDeltaReasoning(ChatResponse response) {
        if (response == null || response.getResult() == null) {
            return null;
        }
        Generation generation = response.getResult();
        AssistantMessage output = generation.getOutput();
        return firstNonEmpty(
                output == null ? null : fromMapDelta(output.getMetadata()),
                generation.getMetadata() == null ? null : fromObjectMapDelta(generation.getMetadata()));
    }

    /**
     * 流式单 chunk 的 content 增量：保留空格。
     */
    public static String extractDeltaContent(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        String text = response.getResult().getOutput().getText();
        return StringUtils.isEmpty(text) ? null : text;
    }

    public static SplitResult splitFromAssistantMessage(AssistantMessage output) {
        if (output == null) {
            return SplitResult.empty();
        }
        String reasoning = fromMap(output.getMetadata());
        return split(output.getText(), reasoning);
    }

    public static SplitResult splitFromChatResponse(ChatResponse response) {
        if (response == null || response.getResult() == null) {
            return SplitResult.empty();
        }
        Generation generation = response.getResult();
        AssistantMessage output = generation.getOutput();
        String reasoning = firstNonEmpty(
                output == null ? null : fromMap(output.getMetadata()),
                generation.getMetadata() == null ? null : fromObjectMap(generation.getMetadata()));
        String text = output == null ? null : output.getText();
        return split(text, reasoning);
    }

    private static String fromMap(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        return firstNonEmpty(
                asStringTrimmed(metadata.get(METADATA_REASONING_CONTENT)),
                asStringTrimmed(metadata.get(METADATA_REASONING)),
                asStringTrimmed(metadata.get(METADATA_THINKING)),
                asStringTrimmed(metadata.get("reasoning_content")));
    }

    /** 流式：不 trim，保留 token 前导空格 */
    private static String fromMapDelta(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        return firstNonEmpty(
                asDelta(metadata.get(METADATA_REASONING_CONTENT)),
                asDelta(metadata.get(METADATA_REASONING)),
                asDelta(metadata.get(METADATA_THINKING)),
                asDelta(metadata.get("reasoning_content")));
    }

    private static String fromObjectMap(Object metadata) {
        return fromObjectMapInternal(metadata, true);
    }

    private static String fromObjectMapDelta(Object metadata) {
        return fromObjectMapInternal(metadata, false);
    }

    private static String fromObjectMapInternal(Object metadata, boolean trimEnds) {
        if (metadata == null) {
            return null;
        }
        if (metadata instanceof Map<?, ?> map) {
            Object reasoningContent = map.get(METADATA_REASONING_CONTENT);
            if (reasoningContent == null) {
                reasoningContent = map.get("reasoning_content");
            }
            if (reasoningContent == null) {
                reasoningContent = map.get(METADATA_REASONING);
            }
            if (reasoningContent == null) {
                reasoningContent = map.get(METADATA_THINKING);
            }
            return trimEnds ? asStringTrimmed(reasoningContent) : asDelta(reasoningContent);
        }
        try {
            var method = metadata.getClass().getMethod("get", String.class);
            Object value = method.invoke(metadata, METADATA_REASONING_CONTENT);
            if (value == null) {
                value = method.invoke(metadata, "reasoning_content");
            }
            return trimEnds ? asStringTrimmed(value) : asDelta(value);
        } catch (Exception ignore) {
            return null;
        }
    }

    private record ThinkSplit(String content, String reasoning) {
    }

    private static ThinkSplit splitThinkTags(String text) {
        if (StringUtils.isBlank(text) || !StringUtils.containsIgnoreCase(text, THINK_OPEN)) {
            return new ThinkSplit(text, null);
        }
        Matcher matcher = THINK_BLOCK.matcher(text);
        List<String> reasoningParts = new ArrayList<>();
        StringBuffer visible = new StringBuffer();
        while (matcher.find()) {
            String body = matcher.group(1);
            if (StringUtils.isNotBlank(body)) {
                reasoningParts.add(body.trim());
            }
            matcher.appendReplacement(visible, "");
        }
        matcher.appendTail(visible);

        // 未闭合的 <think>：其后全部当 reasoning
        String remainder = visible.toString();
        int openIdx = indexOfIgnoreCase(remainder, THINK_OPEN);
        String reasoningTail = null;
        if (openIdx >= 0) {
            reasoningTail = remainder.substring(openIdx + THINK_OPEN.length()).trim();
            remainder = remainder.substring(0, openIdx);
        }

        String reasoning = joinReasoning(
                reasoningParts.isEmpty() ? null : String.join("\n", reasoningParts),
                reasoningTail);
        return new ThinkSplit(remainder, reasoning);
    }

    private static int indexOfIgnoreCase(String text, String token) {
        return StringUtils.indexOfIgnoreCase(text, token);
    }

    private static String joinReasoning(String left, String right) {
        if (StringUtils.isEmpty(left)) {
            return emptyToNull(right);
        }
        if (StringUtils.isEmpty(right)) {
            return emptyToNull(left);
        }
        return left + "\n" + right;
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.isNotEmpty(value)) {
                return value;
            }
        }
        return null;
    }

    /** 整轮字段：trim 两端 */
    private static String asStringTrimmed(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() || "null".equals(text) ? null : text;
    }

    /**
     * 流式 delta：禁止 trim，否则 " code" 变成 "code"，单词会粘在一起。
     */
    private static String asDelta(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        if (text.isEmpty() || "null".equals(text)) {
            return null;
        }
        return text;
    }

    private static String emptyToNull(String value) {
        return StringUtils.isEmpty(value) ? null : value;
    }

    private static String trimEndsToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
