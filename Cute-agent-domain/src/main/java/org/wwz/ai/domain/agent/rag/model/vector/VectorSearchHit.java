package org.wwz.ai.domain.agent.rag.model.vector;

import java.util.Map;

/**
 * 单条向量召回结果。
 */
public final class VectorSearchHit {

    private final String id;
    private final float score;
    private final Map<String, Object> payload;

    public VectorSearchHit(String id, float score, Map<String, Object> payload) {
        this.id = id;
        this.score = score;
        this.payload = Map.copyOf(payload == null ? Map.of() : payload);
    }

    public String getId() {
        return id;
    }

    public float getScore() {
        return score;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }
}
