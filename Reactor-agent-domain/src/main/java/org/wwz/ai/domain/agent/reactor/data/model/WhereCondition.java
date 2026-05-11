package org.wwz.ai.domain.agent.reactor.data.model;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class WhereCondition  implements Serializable {
    private String operator;
    private List<WhereCondition> conditionList;
    private String identifier;
    private String identifierTableAlias;
    private String identifierKind;
    private String value;
    private boolean numericValue;
    private String comparison;
    private String comparisonType;
    private List<String> valueList;
    private String identifierComment;

    private String valueKind;
    private String secondValueKind;
    /**
     * 是否为动态时间字段
     */
    private Integer dateColumn;
    /**
     * 日期字段值格式
     */
    private String dateColumnFormat;
    /**
     * 动态时间编码
     */
    private String dynamicDateCode;
    private String dynamicDateLabel;
    /**
     * 是否取反，LIKE类型取反为NOT LIKE
     */
    private boolean negaed;
}
