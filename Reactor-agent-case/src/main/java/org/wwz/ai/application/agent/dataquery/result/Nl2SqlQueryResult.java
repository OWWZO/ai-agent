package org.wwz.ai.application.agent.dataquery.result;

import java.util.List;

/** Query template returned to the existing DataAgent API. */
public record Nl2SqlQueryResult(
        String requestId,
        String query,
        List<String> modelCodeList,
        List<DataQueryModelResult> schemaInfo,
        String currentDateInfo,
        String traceId,
        String recallType,
        Boolean stream,
        String userInfo,
        String dbType,
        boolean useVector,
        boolean useElastic) {
}
