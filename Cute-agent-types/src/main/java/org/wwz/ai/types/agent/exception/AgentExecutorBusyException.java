package org.wwz.ai.types.agent.exception;

/**
 * Agent 主链路执行器繁忙异常。
 */
public class AgentExecutorBusyException extends RuntimeException {

    public AgentExecutorBusyException(String message) {
        super(message);
    }

    public AgentExecutorBusyException(String message, Throwable cause) {
        super(message, cause);
    }
}
