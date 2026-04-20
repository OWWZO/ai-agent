package org.wwz.ai.domain.agent.reactor.service;

import org.wwz.ai.domain.agent.reactor.entity.AgentConversation;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionMemoryPreparationResult;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionWorkingMemory;

/**
 * 会话记忆服务
 */
public interface IAgentSessionMemoryService {

    /**
     * 在请求入口准备工作记忆，并在必要时先执行会话压缩。
     */
    SessionMemoryPreparationResult prepareForRequest(AgentConversation conversation);

    /**
     * 在请求开始前重建工作记忆
     */
    SessionWorkingMemory rebuildWorkingMemory(AgentConversation conversation);
}
