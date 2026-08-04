package org.wwz.ai.domain.agent.reactor.data;

import lombok.Data;

@Data
/**
 * 面向问数链路的轻量表格模型。
 */
public class SimpleTable {
    private String tableSchema;
    private String tableName;
    private String tableType;
    private String comments;
    private Long datasourceId;
}
