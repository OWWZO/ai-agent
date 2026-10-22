package org.wwz.ai.application.agent.dataquery.result;

public record DataQuerySchemaResult(
        String modelCode,
        String columnId,
        String columnName,
        String columnComment,
        String fewShot,
        String dataType,
        String synonyms,
        String vectorUuid,
        int defaultRecall,
        int analyzeSuggest) {
}
