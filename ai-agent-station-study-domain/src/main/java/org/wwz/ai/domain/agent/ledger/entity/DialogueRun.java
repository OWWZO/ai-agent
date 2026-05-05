package org.wwz.ai.domain.agent.ledger.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 单次对话执行总账。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DialogueRun {

    private Long id;

    /** 对外稳定运行标识，首期直接复用 requestId */
    private String runUid;

    /** 单次请求ID */
    private String requestId;

    /** 会话ID */
    private String sessionId;

    /** 匿名访客ID */
    private String visitorId;

    /** 入口执行链 react / plan_solve */
    private String entryAgent;

    /** 运行状态 */
    private Integer status;

    /** 用户原始问题 */
    private String queryText;

    /** 最终总结文本 */
    private String finalSummaryText;

    /** LLM 调用次数 */
    private Integer llmCallCount;

    /** 工具调用次数 */
    private Integer toolCallCount;

    /** 产物数量 */
    private Integer artifactCount;

    /** LLM 输入 token 总量 */
    private Integer promptTokensTotal;

    /** LLM 输出 token 总量 */
    private Integer completionTokensTotal;

    /** LLM token 总量 */
    private Integer totalTokensTotal;

    /** 失败码 */
    private String errorCode;

    /** 失败信息 */
    private String errorMsg;

    /** 开始时间 */
    private LocalDateTime startedAt;

    /** 结束时间 */
    private LocalDateTime finishedAt;

    /** 总耗时 */
    private Long durationMs;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;
}
