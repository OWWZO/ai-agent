package org.wwz.ai.domain.agent.memory;

import org.wwz.ai.domain.agent.runtime.dto.Message;

import java.util.List;

/**
 * 会话工作记忆压缩服务（P0 drop-oldest + P1 full LLM compact）。
 * 只改 hydrate 用的 Message 列表 / working_memory 投影，不改 Execution Ledger。
 */
public interface SessionContextCompactionService {

    /**
     * 若超阈值则压缩；失败时降级为 P0 drop-oldest。
     *
     * @param sessionId 会话 ID
     * @param requestId 当前请求 ID（仅日志/熔断键）
     * @param messages  已 hydrate 的 working memory 前缀
     * @return 压缩后的消息列表（可能原样返回）
     */
    List<Message> applyIfNeeded(String sessionId, String requestId, List<Message> messages);
}
