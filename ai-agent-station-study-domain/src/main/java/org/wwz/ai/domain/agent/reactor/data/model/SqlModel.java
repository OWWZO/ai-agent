package org.wwz.ai.domain.agent.reactor.data.model;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
@Data
public class SqlModel {

    public static final String HINT_TAG = "HINT";

    /**
     * sql模型列信息
     */
    private List<ModelColumn> columnList;
    /**
     * sql模型查询表信息
     */
    private FromTable fromTable;
    /**
     * sql模型where条件
     */
    private List<WhereCondition> whereConditionList;
    /**
     * sql模型中group信息
     */
    private List<ModelColumn> groupList;
    /**
     * sql模型中order by信息
     */
    private List<DataOrderBy> orderByList;

    private String dialect;

    private String sqlComment;

    private String fetch;

    private String having;
    /**
     * sql模型having条件
     */
    private List<WhereCondition> havingConditionList;

    private String selectType;

    private String hint;

    Map<String, ModelColumn> modelColumnMap;


}
