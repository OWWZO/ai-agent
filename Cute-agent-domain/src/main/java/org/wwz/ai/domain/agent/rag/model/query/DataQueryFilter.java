package org.wwz.ai.domain.agent.rag.model.query;

import lombok.Data;

import java.util.List;

/** Presentation metadata for a parsed query condition. */
@Data
public class DataQueryFilter {
    private String col;
    private String opt;
    private String val;
    private String optName;
    private String name;
    private String dataType;
    private String operator;
    private List<DataQueryFilter> subFilters;
}
