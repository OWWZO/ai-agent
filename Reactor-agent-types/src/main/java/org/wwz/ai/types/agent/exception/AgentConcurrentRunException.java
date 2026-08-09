package org.wwz.ai.types.agent.exception;

/**
 * 同一访客已有进行中的 Agent run 时拒绝新启动。
 */
public class AgentConcurrentRunException extends RuntimeException {

    private final String activeRequestId;
    private final String activeSessionId;

    public AgentConcurrentRunException(String message, String activeRequestId, String activeSessionId) {
        super(message);
        this.activeRequestId = activeRequestId;
        this.activeSessionId = activeSessionId;
    }

    public String getActiveRequestId() {
        return activeRequestId;
    }

    public String getActiveSessionId() {
        return activeSessionId;
    }
}
