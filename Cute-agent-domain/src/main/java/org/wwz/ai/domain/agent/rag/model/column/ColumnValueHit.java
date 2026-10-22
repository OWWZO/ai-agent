package org.wwz.ai.domain.agent.rag.model.column;

import java.util.Map;

/**
 * 单条列值召回结果。
 */
public final class ColumnValueHit {

    private final Map<String, Object> fields;
    private final Float score;

    public ColumnValueHit(Map<String, Object> fields, Float score) {
        this.fields = Map.copyOf(fields == null ? Map.of() : fields);
        this.score = score;
    }

    public Map<String, Object> getFields() {
        return fields;
    }

    public Float getScore() {
        return score;
    }
}
