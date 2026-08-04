package org.wwz.ai.domain.agent.reactor.data;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
/**
 * 表格列描述模型，统一表达列名、显示名和数据类型。
 */
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
