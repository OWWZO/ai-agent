package org.wwz.ai.domain.agent.runtime.printer;


import org.wwz.ai.domain.agent.runtime.enums.AgentType;

import java.util.Map;

/**
 * Agent 输出端口。
 * <p>领域执行器通过该接口发送增量事件，具体实现负责适配 SSE、日志或测试输出。</p>
 */
public interface Printer {
    /**
     * 发送消息
     *
     * @param message 消息内容
     */

    void send(String messageId, String messageType, Object message, String digitalEmployee, Boolean isFinal);

    /**
     * 发送带扩展结果字段的消息。
     * 主要用于 planner round 这类需要透传稳定标识的场景。
     */
    void send(String messageId,
              String messageType,
              Object message,
              Map<String, Object> extraResultMap,
              String digitalEmployee,
              Boolean isFinal);

    /**
     * 以下便捷重载只是核心方法的参数转发，接口统一提供 default 实现。
     * <p>
     * 所有实现此前都逐字重复这 5 段转发，且没有任何实现改写过语义——
     * 收在接口里后，新增实现只需覆盖两个核心方法。
     */

    default void send(String messageType, Object message) {
        send(null, messageType, message, null, true);
    }

    default void send(String messageType, Object message, String digitalEmployee) {
        send(null, messageType, message, digitalEmployee, true);
    }

    default void send(String messageId, String messageType, Object message, Boolean isFinal) {
        send(messageId, messageType, message, (String) null, isFinal);
    }

    /**
     * 发送带扩展结果字段的简化消息。
     */
    default void sendWithResultMap(String messageId,
                                   String messageType,
                                   Object message,
                                   Map<String, Object> extraResultMap,
                                   Boolean isFinal) {
        send(messageId, messageType, message, extraResultMap, null, isFinal);
    }

    /**
     * 发送带扩展结果字段的最终消息。
     */
    default void sendWithResultMap(String messageType, Object message, Map<String, Object> extraResultMap) {
        send(null, messageType, message, extraResultMap, null, true);
    }

    void close();

    void updateAgentType(AgentType agentType);
}
