package org.wwz.ai.domain.agent.reactor.data.model;

/**
 * FROM 子句的数据来源类型。
 */
public enum FromTableType {
    /**
     * 普通表
     */
    TABLE,
    /**
     * 子查询
     */
    INNER_SQL,
    /**
     * join 查询
     */
    JOIN_SQL
}
