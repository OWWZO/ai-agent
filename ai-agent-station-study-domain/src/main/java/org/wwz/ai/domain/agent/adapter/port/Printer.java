
package org.wwz.ai.domain.agent.adapter.port;

import org.wwz.ai.domain.agent.runtime.enums.AgentType;

public interface Printer {
    /**
     * 发送消息
     *
     * @param message 消息内容
     */

    void send(String messageId, String messageType, Object message, String digitalEmployee, Boolean isFinal);

    void send(String messageType, Object message);

    void send(String messageType, Object message, String digitalEmployee);

    void send(String messageId, String messageType, Object message, Boolean isFinal);

    void close();

    void updateAgentType(AgentType agentType);
}

