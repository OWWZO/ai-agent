 package org.wwz.ai.domain.agent.reactor.data.model;

import lombok.Data;

import java.util.List;

@Data
public class ModelColumn {

    private String columnKey;

    private String columnName;

    private String columnAlias;

    private String comment;

    private StandardColumnType dataType;

    private Integer position;

    /**
     * 小数位
     */
    private Integer decimalPlaces;

    private String dateFormat;

    private String columnKind;

    private String type;

    private boolean aggregator;

    private boolean simpleIdentify;

    private List<String> identifyNameList;

    private String functionName;
    private List<String> functionArgList;

    private boolean isStar;

    private String tableAlias;

    private String tableName;
     /**
      * 计算字段属性(0非计算字段，1计算字段，2分组字段)
      */
     private Integer calculateMember;
     /**
      * 计算列json配置
      */
     private String calculateExpression;
     /**
      * 查询指定的函数
      */
     private String colFunction;
}
