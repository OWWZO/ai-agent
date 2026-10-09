package org.wwz.ai.domain.agent.runtime.llm;

import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 供应商无关的 LLM completion 请求。
 * <p>
 * 不可变契约：所有字段 final，集合与 map 做防御性复制；需要变体时用 {@code toBuilder()}。
 */
@Getter
@Builder(toBuilder = true)
public class LlmRequest {

    private final String model;
    private final LLMSettings settings;
    private final List<LlmMessage> messages;
    private final List<LlmToolDefinition> tools;
    private final String toolChoice;
    private final Double temperature;
    private final String sessionId;
    private final Map<String, Object> metadata;

    private LlmRequest(String model,
                       LLMSettings settings,
                       List<LlmMessage> messages,
                       List<LlmToolDefinition> tools,
                       String toolChoice,
                       Double temperature,
                       String sessionId,
                       Map<String, Object> metadata) {
        this.model = model;
        this.settings = settings;
        this.messages = immutableList(messages);
        this.tools = immutableList(tools);
        this.toolChoice = toolChoice;
        this.temperature = temperature;
        this.sessionId = sessionId;
        this.metadata = metadata == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    private static <T> List<T> immutableList(List<T> source) {
        return source == null ? null : Collections.unmodifiableList(new ArrayList<>(source));
    }
}
