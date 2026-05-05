package org.wwz.ai.domain.agent.ledger.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 单次工具调用账本。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolInvocation {

    private Long id;

    /** 所属 run */
    private Long runId;

    /** 来源 LLM 调用 */
    private Long llmInvocationId;

    /** 模型返回的 toolCallId */
    private String toolCallId;

    /** 原始分发顺序 */
    private Integer dispatchIndex;

    /** 当前 agent 名称 */
    private String agentName;

    /** 当前步号 */
    private Integer stepNo;

    /** 工具名称 */
    private String toolName;

    /** local / mcp */
    private String toolProvider;

    /** 入参 JSON */
    private String inputJson;

    /** 主智能体 observation */
    private String llmObservation;

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
