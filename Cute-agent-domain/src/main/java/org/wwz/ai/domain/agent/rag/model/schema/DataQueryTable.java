package org.wwz.ai.domain.agent.rag.model.schema;

import lombok.Data;

/** Lightweight table metadata used during schema discovery. */
@Data
public class DataQueryTable {
    private String tableSchema;
    private String tableName;
    private String tableType;
    private String comments;
    private Long datasourceId;
}
