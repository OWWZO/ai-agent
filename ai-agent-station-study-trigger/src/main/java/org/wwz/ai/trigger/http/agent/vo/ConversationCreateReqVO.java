package org.wwz.ai.trigger.http.agent.vo;

import lombok.Data;

@Data
public class ConversationCreateReqVO {
    /** 前端生成的会话UUID */
    private String sessionId;
    /** 会话标题 */
    private String title;
    /** 0=CHAT, 1=PLAN_SOLVE, 2=REACT */
    private Integer agentType;
    /** chat/html/docs/ppt/table */
    private String productType;
}
