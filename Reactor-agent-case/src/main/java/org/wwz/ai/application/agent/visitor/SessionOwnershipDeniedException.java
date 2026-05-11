package org.wwz.ai.application.agent.visitor;

/**
 * 会话归属校验失败异常。
 */
public class SessionOwnershipDeniedException extends RuntimeException {

    public SessionOwnershipDeniedException(String message) {
        super(message);
    }
}
