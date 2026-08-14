package org.wwz.ai.trigger.http.agent.vo;

import lombok.Data;

/**
 * 向进行中的 run 注入用户指导（控制面，不开新 SSE run）。
 */
@Data
public class AgentRunInjectReqVO {

    private String sessionId;
    /** 本轮流式对话的 requestId */
    private String requestId;
    /** 指导文本 */
    private String text;
}
