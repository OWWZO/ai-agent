package org.wwz.ai.trigger.http.dataagent.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class SqlExecutionResultResponseVO {
    private String querySql;
    private Long dataSize;
    private List<String> columnList;
    private List<String> columnEnList;
    private List<Map<String, Object>> dataList;
    private Boolean success;
    private String errorMessage;
    private boolean fromCache;
    private Long queryStartTime;
    private Long queryEndTime;
    private Long createConnectionTime;
    private Long wrapAuthTime;
    private Long startInServer;
    private Long endInServer;
}
