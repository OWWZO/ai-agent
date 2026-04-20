package org.wwz.ai.domain.agent.reactor.model.memory;

/**
 * transcript 中最小上下文块类型。
 */
public enum TranscriptBlockType {
    USER_INPUT,
    ASSISTANT_THOUGHT,
    ASSISTANT_ANSWER,
    TOOL_USE,
    TOOL_RESULT,
    ARTIFACT_REFERENCE
}
