package org.wwz.ai.domain.agent.rag.model.query;

/** User input for an analytical chat query. */
public record DataAgentChatQuery(String content, String traceId) {
}
