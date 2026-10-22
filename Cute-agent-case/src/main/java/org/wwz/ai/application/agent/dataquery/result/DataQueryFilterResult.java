package org.wwz.ai.application.agent.dataquery.result;

import java.util.List;

public record DataQueryFilterResult(
        String col,
        String opt,
        String val,
        String optName,
        String name,
        String dataType,
        String operator,
        List<DataQueryFilterResult> subFilters) {
}
