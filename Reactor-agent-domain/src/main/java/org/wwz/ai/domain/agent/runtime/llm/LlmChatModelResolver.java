package org.wwz.ai.domain.agent.runtime.llm;

/**
 * LLM model cache invalidation seam.
 * <p>
 * The concrete model resolver belongs to Infrastructure. The catalog only needs
 * to invalidate provider instances after an admin configuration change.
 */
public interface LlmChatModelResolver {

    void invalidateAll();
}
