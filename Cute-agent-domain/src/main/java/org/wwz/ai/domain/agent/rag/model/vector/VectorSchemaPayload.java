package org.wwz.ai.domain.agent.rag.model.vector;

import lombok.Data;

/** Schema fields stored as a vector payload; property names are part of the recall contract. */
@Data
public class VectorSchemaPayload {
    private String modelCode;
    private String columnId;
    private String columnName;
    private String columnComment;
    private String fewShot;
    private String dataType;
    private String synonyms;
    private String vectorUuid;
    private String defaultRecall;
    private String analyzeSuggest;
}
