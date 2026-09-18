package org.wwz.ai.trigger.http.reactor.support;

/**
 * 同一访客 SSE 观察连接超过上限。
 */
public class AgentStreamLimitException extends RuntimeException {

    public AgentStreamLimitException(String message) {
        super(message);
    }
}
