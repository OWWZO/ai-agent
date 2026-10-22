package org.wwz.ai.application.agent.dataquery.result;

import java.util.List;
import java.util.Map;

public record DataQueryResult(
        String question,
        List<Map<String, Object>> dataList,
        List<DataQueryColumnResult> columnList,
        List<DataQueryFilterResult> filters,
        String modelCode,
        String modelName,
        Boolean loadSucceed,
        String errorMessage,
        List<String> querySqlList,
        int limit,
        List<String> dimCols,
        List<String> measureCols,
        String nl2sqlResult,
        String id) {
}
