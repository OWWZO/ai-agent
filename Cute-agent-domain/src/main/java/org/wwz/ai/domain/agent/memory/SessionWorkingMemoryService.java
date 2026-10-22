package org.wwz.ai.domain.agent.memory;

import org.wwz.ai.domain.agent.runtime.dto.Message;

import java.util.List;

/**
 * 跨轮工作记忆投影服务（working_memory_*）。
 * load 供下一轮 preload；persist 在 run 成功结束后写入本轮 delta。
 * 读写按 {@code sessionId + memoryScope} 隔离（main / sub:{agentId}）。
 */
public interface SessionWorkingMemoryService {

    /**
     * 加载 session 主 scope（main）内 READY 消息链（排除当前 request）。
     */
    default List<Message> loadReadyMessages(String sessionId, String currentRequestId) {
        return loadReadyMessages(sessionId, WorkingMemoryScopes.MAIN, currentRequestId);
    }

    /**
     * 加载指定 scope 的 READY 消息链。
     */
    List<Message> loadReadyMessages(String sessionId, String memoryScope, String currentRequestId);

    /**
     * 持久化本轮 delta 到主 scope。
     */
    default void persistTurn(String sessionId,
                             String requestId,
                             Long runId,
                             String entryAgent,
                             List<Message> turnMessages) {
        persistTurn(sessionId, WorkingMemoryScopes.MAIN, requestId, runId, entryAgent, turnMessages);
    }

    /**
     * 持久化本轮 delta 到指定 scope。
     */
    void persistTurn(String sessionId,
                     String memoryScope,
                     String requestId,
                     Long runId,
                     String entryAgent,
                     List<Message> turnMessages);

    /**
     * 用压缩后的投影整体替换主 scope READY turns。
     */
    default void replaceReadyProjection(String sessionId, String compactRequestId, List<Message> compactedMessages) {
        replaceReadyProjection(sessionId, WorkingMemoryScopes.MAIN, compactRequestId, compactedMessages);
    }

    /**
     * 用压缩后的投影整体替换指定 scope 的 READY turns（旧 turns 标 INVALID）。
     * 仅服务 compaction；不改变 Execution Ledger。
     */
    void replaceReadyProjection(String sessionId,
                                String memoryScope,
                                String compactRequestId,
                                List<Message> compactedMessages);
}
