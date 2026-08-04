package org.wwz.ai.domain.agent.reactor.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
/**
 * 数据查询结果模型，承载列定义、行数据和分页/统计信息。
 */
public class QueryResult {
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

    public QueryResult(String sql) {
        this.querySql = sql;
        this.columnList = Collections.emptyList();
        this.columnEnList = Collections.emptyList();
        this.dataList = Collections.emptyList();
        this.dataSize = 0L;
        this.success = true;
    }

    public QueryResult() {

    }
}
