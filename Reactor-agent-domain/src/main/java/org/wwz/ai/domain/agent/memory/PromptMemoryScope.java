package org.wwz.ai.domain.agent.memory;

/**
 * 可复用提示词记忆所属的运行时范围。
 */
public enum PromptMemoryScope {
    REACT,
    PLAN,
    EXECUTOR,
    SUMMARY
}
