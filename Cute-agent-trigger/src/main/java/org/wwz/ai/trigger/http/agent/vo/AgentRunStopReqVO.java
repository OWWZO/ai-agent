package org.wwz.ai.trigger.http.agent.vo;

import lombok.Data;

/**
 * 停止本轮 Agent run。
 */
@Data
public class AgentRunStopReqVO {

    private String sessionId;
    /** 本轮流式对话的 requestId */
    private String requestId;
}
