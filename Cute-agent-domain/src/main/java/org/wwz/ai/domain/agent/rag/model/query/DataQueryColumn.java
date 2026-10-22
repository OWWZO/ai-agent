package org.wwz.ai.domain.agent.rag.model.query;

import lombok.Data;

/** Presentation metadata derived from a parsed query column. */
@Data
public class DataQueryColumn {
    private String col;
    private String agg;
    private String order;
    private String guid;
    private String name;
    private String dataType;
    private String colType;
}
