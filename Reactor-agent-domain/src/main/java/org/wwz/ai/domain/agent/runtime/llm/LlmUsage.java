package org.wwz.ai.domain.agent.runtime.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 供应商无关的 token usage。nativeUsage 只承载已转换的普通对象，不暴露 SDK 类型。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmUsage {

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
    private Object nativeUsage;

    public static LlmUsage empty() {
        return new LlmUsage();
    }
}
