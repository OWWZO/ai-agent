package org.wwz.ai.domain.agent.rag.model.schema;

import lombok.Builder;
import lombok.Data;

/** A column in a configured analytical model. */
@Data
@Builder
public class DataQueryTableColumn {
    private String name;
    private String dataType;
    private String originDataType;
    private Integer columnLength;
    private Boolean nullable;
    private Object defaultValue;
    private String comment;
    private Integer position;
}
