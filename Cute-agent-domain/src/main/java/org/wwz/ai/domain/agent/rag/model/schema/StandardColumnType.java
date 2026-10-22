package org.wwz.ai.domain.agent.rag.model.schema;

/** Shared semantic types used by SQL result presentation and schema inference. */
public enum StandardColumnType {
    VARCHAR(1),
    DATE(2),
    NUMBER(3),
    DECIMAL(4);

    private final int value;

    StandardColumnType(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    public static StandardColumnType of(String type) {
        for (StandardColumnType columnType : values()) {
            if (columnType.name().equalsIgnoreCase(type)) {
                return columnType;
            }
        }
        return VARCHAR;
    }
}
