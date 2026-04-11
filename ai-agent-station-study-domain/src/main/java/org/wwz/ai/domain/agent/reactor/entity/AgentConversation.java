package org.wwz.ai.domain.agent.reactor.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI Agent 会话表 PO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentConversation {

    private Long id;

    /** 前端生成的会话UUID */
    private String sessionId;

    /** 匿名设备标识 */
    private String deviceId;

    /** 认证用户ID(匿名时为null) */
    private Long userId;

    /** 会话标题 */
    private String title;

    /** 0=CHAT, 1=PLAN_SOLVE(深度思考), 2=REACT(深度研究) */
    private Integer agentType;

    /** 产品形态: chat/html/docs/ppt/table */
    private String productType;

    /** chat 会话绑定的角色ID */
    private String aiAgentId;

    /** 角色名称快照 */
    private String aiAgentNameSnapshot;

    /** 消息轮数 */
    private Integer messageCount;

    /** 是否置顶 */
    private Integer pinned;

    /** 最后一条消息摘要 */
    private String lastMessagePreview;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 软删除 */
    private Integer deleted;
}
