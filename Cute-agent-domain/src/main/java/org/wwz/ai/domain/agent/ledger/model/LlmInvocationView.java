package org.wwz.ai.domain.agent.ledger.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * LLM 调用查询视图。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmInvocationView {

    private Long id;

    private Long runId;

    private Integer invocationSeq;

    private String agentName;

    private Integer stepNo;

    private String callKind;

    private Integer streaming;

    private String modelName;

    private String responseText;

    /** 模型原生 CoT */
    private String reasoningContent;

    private Integer toolCallCount;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    /** 请求开始前的上下文分段估算。 */
    private Integer estTotalTokens;

    private Integer estSystemTokens;

    private Integer estMessageTokens;

    private Integer estToolTokens;

    private String finishReason;

    private Integer status;

    private String errorMsg;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private Long durationMs;

    private LocalDateTime createTime;
}
