package org.wwz.ai.trigger.http.agent.vo;

import lombok.Data;

@Data
public class MessageSendReqVO {
    private String sessionId;
    private String requestId;
    private String query;
    /** null=chat(自动判断), 0=深度研究(REACT), 1=深度思考(PLAN_SOLVE) */
    private Integer deepThink;
    /** chat/html/docs/ppt/table */
    private String outputStyle;
    /** 文件列表JSON字符串 */
    private String filesJson;
    /** chat 模式角色ID */
    private String aiAgentId;
}
