package org.wwz.ai.domain.agent.runtime.llm;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从供应商无关的 LLM 响应中拆出 reasoning / content 两路。
 */
public final class ReasoningContentExtractor {

    public static final String METADATA_REASONING_CONTENT = "reasoningContent";
    public static final String METADATA_REASONING = "reasoning";
    public static final String METADATA_THINKING = "thinking";

    public static final String EVENT_TYPE = "llm_reasoning";

    private static final Pattern THINK_BLOCK = Pattern.compile("(?is)<think>(.*?)</think>");
    private static final String THINK_OPEN = "<think>";

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

    public static SplitResult splitFromResponse(LlmResponse response) {
        if (response == null) {
            return SplitResult.empty();
        }
        return split(response.getContent(), response.getReasoningContent());
    }

    /** 流式单 chunk 的 reasoning 增量，保留 token 前导/尾随空格。 */
    public static String extractDeltaReasoning(LlmStreamEvent event) {
        return event == null ? null : emptyToNull(event.getReasoningContent());
    }

    /** 流式单 chunk 的 content 增量，保留 token 空格。 */
    public static String extractDeltaContent(LlmStreamEvent event) {
        return event == null ? null : emptyToNull(event.getContent());
    }

    /**
     * 整轮结果拆分：可对最终全文 trim 两端；流式 delta 不应调用此方法。
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

    public static String extractReasoningMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        return firstNonEmpty(
                asStringTrimmed(metadata.get(METADATA_REASONING_CONTENT)),
                asStringTrimmed(metadata.get(METADATA_REASONING)),
                asStringTrimmed(metadata.get(METADATA_THINKING)),
                asStringTrimmed(metadata.get("reasoning_content")));
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

        String remainder = visible.toString();
        String reasoningTail = null;
        int openIdx = StringUtils.indexOfIgnoreCase(remainder, THINK_OPEN);
        if (openIdx >= 0) {
            reasoningTail = remainder.substring(openIdx + THINK_OPEN.length()).trim();
            remainder = remainder.substring(0, openIdx);
        }

        String reasoning = joinReasoning(
                reasoningParts.isEmpty() ? null : String.join("\n", reasoningParts),
                reasoningTail);
        return new ThinkSplit(remainder, reasoning);
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

    private static String asStringTrimmed(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() || "null".equals(text) ? null : text;
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
