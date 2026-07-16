package org.wwz.ai.domain.agent.memory;

/**
 * 唯一标识一条可跨请求恢复的提示词记忆流。
 */
public record PromptMemoryStreamKey(
        String sessionId,
        PromptMemoryScope scope,
        String promptContractId,
        String toolContractId
) {
}
