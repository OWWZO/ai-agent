package org.wwz.ai.application.agent.dataquery.result;

public record DataQueryColumnResult(
        String col,
        String agg,
        String order,
        String guid,
        String name,
        String dataType,
        String colType) {
}
