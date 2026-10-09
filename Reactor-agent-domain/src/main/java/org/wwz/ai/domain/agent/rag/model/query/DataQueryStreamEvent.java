package org.wwz.ai.domain.agent.rag.model.query;

/** Protocol-neutral progress or completion event for a Data Query stream. */
public record DataQueryStreamEvent(String eventType, Object data) {
}
