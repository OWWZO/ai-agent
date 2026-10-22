package org.wwz.ai.application.agent.dataquery.result;

import java.util.List;

public record DataQueryModelResult(
        String modelCode,
        String modelName,
        String usePrompt,
        String businessPrompt,
        String type,
        String content,
        List<DataQuerySchemaResult> schemaList) {
}
