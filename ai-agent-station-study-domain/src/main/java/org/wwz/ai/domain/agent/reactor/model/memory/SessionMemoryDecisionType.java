package org.wwz.ai.domain.agent.reactor.model.memory;

/**
 * 请求前会话压缩决策类型。
 */
public enum SessionMemoryDecisionType {
    BYPASS,
    COMPACTED,
    DEGRADED_CONTINUE,
    REJECTED,
    SKIPPED_CIRCUIT_OPEN
}
