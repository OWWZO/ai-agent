package org.wwz.ai.domain.agent.memory.model;

/**
 * 提示词记忆轮次状态。
 */
public final class PromptMemoryTurnStatus {

    public static final int BUILDING = 0;
    public static final int READY = 1;
    public static final int INVALID = 2;

    private PromptMemoryTurnStatus() {
    }
}
