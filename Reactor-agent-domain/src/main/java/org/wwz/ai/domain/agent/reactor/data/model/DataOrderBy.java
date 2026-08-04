package org.wwz.ai.domain.agent.reactor.data.model;

import lombok.Data;

@Data
/**
 * 查询结果排序描述，记录排序列和升降序方向。
 */
public class DataOrderBy {
    private String tableAlias;
    private String columnName;
    private String columnKind;
    private String columnAlias;
    private String columnKey;
    private OrderByType orderType;
}
