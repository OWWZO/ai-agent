package org.wwz.ai.domain.agent.memory;

import org.wwz.ai.domain.agent.runtime.dto.Message;

import java.util.List;

/**
 * 单会话上下文记忆服务。
 * <p>
 * 默认主路径：从执行账本 hydrate 为模型可见的 Message 链（prompt-cache 友好）。
 * {@link #buildHistoryDialogue} 仅兼容/调试，不再作为 LLM 主输入。
 */
public interface SessionContextMemoryService {

    /**
     * @deprecated 仅调试/legacy；默认请用 {@link #hydrateWorkingMessages(String, String)}。
     */
    @Deprecated
    String buildHistoryDialogue(String sessionId, String currentRequestId);

    /**
     * 将会话历史 hydrate 为可 preload 的 Message 列表（不含当前 request）。
     * 顺序稳定：按 run 时间序，run 内按 llm invocation 序，工具按 dispatch 序。
     */
    List<Message> hydrateWorkingMessages(String sessionId, String currentRequestId);
}
