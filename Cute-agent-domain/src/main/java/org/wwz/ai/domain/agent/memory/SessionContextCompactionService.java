package org.wwz.ai.domain.agent.memory;

import org.wwz.ai.domain.agent.memory.ltm.LtmOwner;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.llm.ContextTokenTracker;
import org.wwz.ai.domain.agent.runtime.llm.PromptShape;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;

import java.util.List;

/**
 * 会话工作记忆压缩服务。
 * 入口时机：
 * 1) applyIfNeeded 保留兼容，不再由 case 层 pre-run 调用
 * 2) BaseAgent 每 step 主模型调用前（mid-run）——第一次真正压缩检查
 * 压缩按 memoryScope 隔离，默认 main。
 */
public interface SessionContextCompactionService {

    /**
     * mid-run flush 所需的父回合对齐信息（Hermes prompt-cache parity）。
     */
    record LtmFlushParity(
            String parentSystemPrompt,
            ToolCollection parentTools,
            LtmOwner owner
    ) {
        public static LtmFlushParity of(String system, ToolCollection tools, LtmOwner owner) {
            return new LtmFlushParity(system, tools, owner);
        }
    }

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
        return applyIfNeededMidRun(sessionId, WorkingMemoryScopes.MAIN, requestId, messages, null);
    }

    default List<Message> applyIfNeededMidRun(String sessionId,
                                              String memoryScope,
                                              String requestId,
                                              List<Message> messages) {
        return applyIfNeededMidRun(sessionId, memoryScope, requestId, messages, null);
    }

    default List<Message> applyIfNeededMidRun(String sessionId,
                                              String memoryScope,
                                              String requestId,
                                              List<Message> messages,
                                              LtmFlushParity flushParity) {
        return applyIfNeededMidRun(sessionId, memoryScope, requestId, messages, flushParity, null, null);
    }

    default List<Message> applyIfNeededMidRun(String sessionId,
                                              String memoryScope,
                                              String requestId,
                                              List<Message> messages,
                                              LtmFlushParity flushParity,
                                              PromptShape promptShape,
                                              ContextTokenTracker.Snapshot tokenSnapshot) {
        return applyIfNeeded(sessionId, memoryScope, requestId, messages);
    }
}
