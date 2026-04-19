package org.wwz.ai.domain.agent.reactor.service;

import org.wwz.ai.domain.agent.reactor.entity.AgentConversation;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionWorkingMemory;

/**
 * 会话记忆服务
 */
public interface IAgentSessionMemoryService {

    /**
     * 在请求开始前重建工作记忆
     */
    SessionWorkingMemory rebuildWorkingMemory(AgentConversation conversation);

    /**
     * 在一轮 COMPLETED 后刷新会话快照
     */
    void refreshSessionMemory(AgentConversation conversation);
}
