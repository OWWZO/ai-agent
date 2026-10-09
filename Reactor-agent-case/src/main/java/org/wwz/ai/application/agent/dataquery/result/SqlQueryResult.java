package org.wwz.ai.application.agent.dataquery.result;

import java.util.List;
import java.util.Map;

public record SqlQueryResult(
        String querySql,
        Long dataSize,
        List<String> columnList,
        List<String> columnEnList,
        List<Map<String, Object>> dataList,
        Boolean success,
        String errorMessage,
        boolean fromCache,
        Long queryStartTime,
        Long queryEndTime,
        Long createConnectionTime,
        Long wrapAuthTime,
        Long startInServer,
        Long endInServer) {
}
