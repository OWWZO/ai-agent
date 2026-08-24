package org.wwz.ai.domain.agent.memory.ltm;

import org.wwz.ai.domain.agent.runtime.dto.Message;

import java.util.List;

/**
 * 压缩前独立 Memory Flush 小回合。
 * 在真正砍 working memory 之前，用短 LLM 抽取并写入 curated。
 */
public interface MemoryFlushService {

    /**
     * @return 成功写入的条目数；跳过/失败返回 0
     */
    int flushBeforeCompact(String sessionId,
                           String requestId,
                           LtmOwner owner,
                           List<Message> messagesAboutToCompact);
}
