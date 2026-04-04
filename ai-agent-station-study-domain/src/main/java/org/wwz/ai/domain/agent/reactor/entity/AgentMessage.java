package org.wwz.ai.domain.agent.reactor.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI Agent 消息表 PO (每轮对话一行,包含用户问题+AI回答)
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentMessage {

    private Long id;

    /** FK -> ai_agent_conversation.id */
    private Long conversationId;

    /** 冗余会话ID */
    private String sessionId;

    /** 前端请求UUID,每轮唯一 */
    private String requestId;

    /** 轮次序号(0-based) */
    private Integer sortOrder;

    // ---- 用户输入 ----

    /** 用户问题 */
    private String query;

    /** 上传文件列表JSON */
    private String filesJson;

    /** 0=CHAT, 1=PLAN_SOLVE, 2=REACT */
    private Integer agentType;

    // ---- Chat模式 ----

    /** LLM纯文本回答 */
    private String response;

    // ---- 深度思考模式 ----

    /** 推理过程文本(plan_thought) */
    private String thought;

    /** Plan对象JSON */
    private String planJson;

    // ---- 深度模式共用 ----

    /** Task[][] 二维数组JSON */
    private String tasksJson;

    /** MultiAgent元数据JSON */
    private String multiAgentJson;

    /** 最终结论Task JSON */
    private String conclusionJson;

    /** PlanItem[]计划列表JSON */
    private String planListJson;

    /** 版本化渲染快照 */
    private String renderSnapshotJson;

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
