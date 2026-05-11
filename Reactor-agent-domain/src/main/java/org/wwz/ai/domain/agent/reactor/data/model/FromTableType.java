package org.wwz.ai.domain.agent.reactor.data.model;

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
