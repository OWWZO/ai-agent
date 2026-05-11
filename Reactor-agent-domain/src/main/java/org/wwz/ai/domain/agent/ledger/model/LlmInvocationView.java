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

    private Integer toolCallCount;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    private String finishReason;

    private Integer status;

    private String errorMsg;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private Long durationMs;

    private LocalDateTime createTime;
}
