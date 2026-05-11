package org.wwz.ai.domain.agent.reactor.data.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.calcite.sql.JoinConditionType;
import org.apache.calcite.sql.JoinType;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class FromTable {
    /**
     * from表名
     */
    private String tableName;
    /**
     * from表as别名
     */
    private String tableAlias;
    /**
     * comment
     */
    private String tableComment;
    /**
     * 关联类型
     *
     * @see org.apache.calcite.sql.JoinType
     */
    private JoinType joinType;
    /**
     * 条件类型
     *
     * @see JoinConditionType
     */
    private JoinConditionType joinConditionType;
    /**
     * 关联条件内容
     */
    private String condition;
    private List<WhereCondition> joinConditions;
    /**
     * 关联表
     */
    private FromTable rightTable;

    private FromTableType fromTableType;
}
