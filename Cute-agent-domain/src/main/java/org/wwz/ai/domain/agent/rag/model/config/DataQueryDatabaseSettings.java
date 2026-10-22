package org.wwz.ai.domain.agent.rag.model.config;

import lombok.Data;

/** SQL dialect and schema context used by Data Query business rules. */
@Data
public class DataQueryDatabaseSettings {
    private String type;
    private String schema;
}
