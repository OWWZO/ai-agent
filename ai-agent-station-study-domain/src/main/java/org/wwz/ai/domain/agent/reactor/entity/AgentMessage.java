package org.wwz.ai.domain.agent.reactor.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI Agent 消息表 PO
 * 仅保留单轮请求账本所需字段，不再承载 rich replay 细节。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentMessage {

    private Long id;

    /** FK -> ai_agent_conversation.id */
    private Long conversationId;

    /** 前端请求UUID,每轮唯一 */
    private String requestId;

    /** 轮次序号(0-based) */
    private Integer sortOrder;

    // ---- 用户输入 ----

    /** 用户问题 */
    private String query;

    /** 上传文件列表JSON */
    private String filesJson;

    /** 本轮生成文件列表JSON */
    private String generatedFilesJson;

    /** 0=CHAT, 1=PLAN_SOLVE, 2=REACT */
    private Integer agentType;

    /** 单轮最终回答/上下文文本 */
    private String response;

    /** 执行指标JSON */
    private String metricsJson;

    // ---- 状态 ----

    /** 0=流式中,1=完成,2=错误,3=强制停止 */
    private Integer status;

    /** 是否强制停止 */
    private Integer forceStop;

    /** 流式开始时间 */
    private LocalDateTime startedAt;

    /** 流式结束时间 */
    private LocalDateTime finishedAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 软删除 */
    private Integer deleted;
}
