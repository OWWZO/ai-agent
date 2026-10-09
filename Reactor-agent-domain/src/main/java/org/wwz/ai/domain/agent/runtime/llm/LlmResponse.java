package org.wwz.ai.domain.agent.runtime.llm;

import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 供应商无关的非流式 completion 响应。
 * <p>
 * 不可变契约：所有字段 final，集合与 map 做防御性复制；需要变体时用 {@code toBuilder()}。
 */
@Getter
@Builder(toBuilder = true)
public class LlmResponse {

    private final String content;
    private final String reasoningContent;
    private final List<LlmToolCall> toolCalls;
    private final String finishReason;
    private final LlmUsage usage;
    private final Map<String, Object> metadata;

    private LlmResponse(String content,
                        String reasoningContent,
                        List<LlmToolCall> toolCalls,
                        String finishReason,
                        LlmUsage usage,
                        Map<String, Object> metadata) {
        this.content = content;
        this.reasoningContent = reasoningContent;
        this.toolCalls = toolCalls == null ? null : Collections.unmodifiableList(new ArrayList<>(toolCalls));
        this.finishReason = finishReason;
        this.usage = usage;
        this.metadata = metadata == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
