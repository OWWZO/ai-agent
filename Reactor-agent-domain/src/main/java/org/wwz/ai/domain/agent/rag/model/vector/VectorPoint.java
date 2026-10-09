package org.wwz.ai.domain.agent.rag.model.vector;

import java.util.List;
import java.util.Map;

/**
 * 待写入向量索引的领域点模型。
 */
public final class VectorPoint {

    private final String id;
    private final List<Float> vector;
    private final Map<String, Object> payload;

    public VectorPoint(String id, List<Float> vector, Map<String, Object> payload) {
        this.id = id;
        this.vector = List.copyOf(vector == null ? List.of() : vector);
        this.payload = Map.copyOf(payload == null ? Map.of() : payload);
    }

    public String getId() {
        return id;
    }

    public List<Float> getVector() {
        return vector;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }
}
