package org.wwz.ai.application.agent.stream;

/**
 * 会话级事件总线：投影只负责赋序和发布，观察连接由 trigger Hub 订阅。
 */
public interface AgentSessionEventBus {

    void publish(String sessionId, Object frame);
}
