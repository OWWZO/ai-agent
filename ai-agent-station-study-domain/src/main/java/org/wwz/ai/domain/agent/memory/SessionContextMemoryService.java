package org.wwz.ai.domain.agent.memory;

/**
 * 单会话上下文记忆服务。
 * 负责从执行账本重建 historyDialogue。
 */
public interface SessionContextMemoryService {

    String buildHistoryDialogue(String sessionId, String currentRequestId);
}
