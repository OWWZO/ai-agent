package org.wwz.ai.trigger.http.agent.vo;

import lombok.Data;

/**
 * 续绑本轮仍在执行的 Agent run 观察流。
 */
@Data
public class AgentRunFollowReqVO {

    private String sessionId;
    /** 本轮流式对话的 requestId */
    private String requestId;

    /** 前端已处理的最后一个投影事件序号。 */
    private long lastEventSeq;
}
