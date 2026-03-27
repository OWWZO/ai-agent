package org.wwz.ai.domain.agent.reactor.data;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class TableColumn {
    private String name;

    private String dataType;

    private String originDataType;

    private Integer columnLength;

    private Boolean nullable;

    private Object defaultValue;

    private String comment;

    private Integer position;

}
