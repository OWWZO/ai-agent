package org.wwz.ai.domain.agent.ledger.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 单次 LLM 调用账本。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmInvocation {

    private Long id;

    /** 所属 run */
    private Long runId;

    /** run 内递增序号 */
    private Integer invocationSeq;

    /** 当前 agent 名称 */
    private String agentName;

    /** 当前步号 */
    private Integer stepNo;

    /** ask / askTool */
    private String callKind;

    /** 是否流式 */
    private Integer streaming;

    /** 模型名 */
    private String modelName;

    /** 完整响应文本 */
    private String responseText;

    /** 工具调用数量 */
    private Integer toolCallCount;

    /** prompt token */
    private Integer promptTokens;

    /** completion token */
    private Integer completionTokens;

    /** total token */
    private Integer totalTokens;

    /** 完成原因 */
    private String finishReason;

    /** 状态 */
    private Integer status;

    /** 错误信息 */
    private String errorMsg;

    /** 开始时间 */
    private LocalDateTime startedAt;

    /** 结束时间 */
    private LocalDateTime finishedAt;

    /** 耗时 */
    private Long durationMs;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;
}
