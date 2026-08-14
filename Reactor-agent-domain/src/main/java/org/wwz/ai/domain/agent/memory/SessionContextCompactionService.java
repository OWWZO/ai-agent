package org.wwz.ai.domain.agent.memory;

import org.wwz.ai.domain.agent.runtime.dto.Message;

import java.util.List;

/**
 * 会话工作记忆压缩服务。
 * 入口时机：
 * 1) 每轮请求 enrichWorkingMemory（pre-run）
 * 2) BaseAgent 每 step 主模型调用前（mid-run）
 * 压缩按 memoryScope 隔离，默认 main。
 */
public interface SessionContextCompactionService {

    /**
     * 若超阈值则压缩；失败时降级为 drop-oldest。默认 main scope。
     */
    default List<Message> applyIfNeeded(String sessionId, String requestId, List<Message> messages) {
        return applyIfNeeded(sessionId, WorkingMemoryScopes.MAIN, requestId, messages);
    }

    List<Message> applyIfNeeded(String sessionId, String memoryScope, String requestId, List<Message> messages);

    /**
     * mid-run 压缩：可单独开关；默认 main scope。
     */
    default List<Message> applyIfNeededMidRun(String sessionId, String requestId, List<Message> messages) {
        return applyIfNeededMidRun(sessionId, WorkingMemoryScopes.MAIN, requestId, messages);
    }

    default List<Message> applyIfNeededMidRun(String sessionId,
                                              String memoryScope,
                                              String requestId,
                                              List<Message> messages) {
        return applyIfNeeded(sessionId, memoryScope, requestId, messages);
    }
}
