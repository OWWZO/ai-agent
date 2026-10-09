package org.wwz.ai.domain.agent.rag.model.query;

import lombok.Data;

import java.util.List;
import java.util.Map;

/** Structured answer for one NL2SQL-generated query. */
@Data
public class DataQueryResult {
    private String question;
    private List<Map<String, Object>> dataList;
    private List<DataQueryColumn> columnList;
    private List<DataQueryFilter> filters;
    private String modelCode;
    private String modelName;
    private Boolean loadSucceed;
    private String errorMessage;
    private List<String> querySqlList;
    private int limit;
    private List<String> dimCols;
    private List<String> measureCols;
    private String nl2sqlResult;
    private String id;
}
