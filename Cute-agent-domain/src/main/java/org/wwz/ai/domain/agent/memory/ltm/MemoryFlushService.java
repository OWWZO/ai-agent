package org.wwz.ai.domain.agent.memory.ltm;

import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;

import java.util.List;

/**
 * 压缩前独立 Memory Flush 小回合。
 * Hermes 对齐：尽量复用父 system/tools 前缀；指令仅尾部 user。
 */
public interface MemoryFlushService {

    /**
     * @return 成功写入的条目数；跳过/失败返回 0
     */
    default int flushBeforeCompact(String sessionId,
                                   String requestId,
                                   LtmOwner owner,
                                   List<Message> messagesAboutToCompact) {
        return flushBeforeCompact(sessionId, requestId, owner, messagesAboutToCompact, null, null);
    }

    int flushBeforeCompact(String sessionId,
                           String requestId,
                           LtmOwner owner,
                           List<Message> messagesAboutToCompact,
                           String parentSystemPrompt,
                           ToolCollection parentTools);
}
