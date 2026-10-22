package org.wwz.ai.domain.agent.rag.model.column;

import java.util.Map;

/**
 * 列值索引文档。
 */
public final class ColumnValueDocument {

    private final String id;
    private final Map<String, Object> fields;

    public ColumnValueDocument(String id, Map<String, Object> fields) {
        this.id = id;
        this.fields = Map.copyOf(fields == null ? Map.of() : fields);
    }

    public String getId() {
        return id;
    }

    public Map<String, Object> getFields() {
        return fields;
    }
}
