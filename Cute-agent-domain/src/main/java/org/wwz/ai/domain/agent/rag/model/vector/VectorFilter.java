package org.wwz.ai.domain.agent.rag.model.vector;

import java.util.Map;

/**
 * 向量索引的等值过滤条件。
 */
public final class VectorFilter {

    private final Map<String, Object> must;

    public VectorFilter(Map<String, Object> must) {
        this.must = Map.copyOf(must == null ? Map.of() : must);
    }

    public Map<String, Object> getMust() {
        return must;
    }
}
