package org.wwz.ai.domain.agent.rag.model.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Result of executing a Data Query statement. */
@Data
@Builder
@AllArgsConstructor
public class SqlExecutionResult {
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

    public SqlExecutionResult(String sql) {
        this.querySql = sql;
        this.columnList = Collections.emptyList();
        this.columnEnList = Collections.emptyList();
        this.dataList = Collections.emptyList();
        this.dataSize = 0L;
        this.success = true;
    }

    public SqlExecutionResult() {
    }
}
