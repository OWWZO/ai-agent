package org.wwz.ai.domain.agent.rag.model.query;

import java.util.List;

/** Query context for recalling model schema columns. */
public record ColumnSchemaRecallQuery(
        String query,
        Integer limit,
        Float scoreThreshold,
        Long timeoutMillis,
        List<String> modelCodes) {
    public ColumnSchemaRecallQuery {
        modelCodes = modelCodes == null ? List.of() : List.copyOf(modelCodes);
    }
}
