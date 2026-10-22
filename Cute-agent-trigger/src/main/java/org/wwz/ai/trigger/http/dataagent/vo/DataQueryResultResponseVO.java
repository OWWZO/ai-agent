package org.wwz.ai.trigger.http.dataagent.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class DataQueryResultResponseVO {
    private String question;
    private List<Map<String, Object>> dataList;
    private List<DataQueryColumnResponseVO> columnList;
    private List<DataQueryFilterResponseVO> filters;
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
