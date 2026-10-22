package org.wwz.ai.application.agent.query;

import lombok.Builder;
import lombok.Value;

/**
 * POST 发消息的命令回执。SSE 观察走 GET，不再跟这条 HTTP 连接绑定。
 */
@Value
@Builder
public class AgentQuerySubmitResult {

    boolean accepted;
    String sessionId;
    String requestId;
}
