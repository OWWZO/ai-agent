package org.wwz.ai.domain.agent.ledger.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 结束 LLM 调用的命令对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmInvocationFinishRecord {

    private Long llmInvocationId;

    private String requestId;

    private Integer status;

    private String responseText;

    /** 模型原生 CoT */
    private String reasoningContent;

    private Integer toolCallCount;

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

    private String finishReason;

    private String errorMsg;

    private LocalDateTime finishedAt;

    private String cacheStatus;

    private String cacheRiskFlags;

    private String obsLogJson;
}

