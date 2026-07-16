package org.wwz.ai.domain.agent.memory;

import java.time.LocalDateTime;

/**
 * 记忆流的短时写入租约，基线轮次用于阻止过期请求覆盖新内容。
 */
public record PromptMemoryLease(
        PromptMemoryStreamKey key,
        Long streamId,
        String requestId,
        Integer baselineTurnSeq,
        LocalDateTime leaseExpireAt
) {
}
