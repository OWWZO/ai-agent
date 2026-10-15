package org.wwz.ai.application.agent.authorization;

/**
 * 会话不可交互异常。
 * <p>
 * 用于子智能体隐藏会话（{@code session_kind=subagent}）等只读会话：
 * owner 可以读取 history / stream，但不得提交新 query、续跑、改名、删除或继续对话。
 * 服务端强制校验，不依赖前端隐藏 composer。
 */
public class SessionNotInteractiveException extends RuntimeException {

    public SessionNotInteractiveException(String message) {
        super(message);
    }
}
