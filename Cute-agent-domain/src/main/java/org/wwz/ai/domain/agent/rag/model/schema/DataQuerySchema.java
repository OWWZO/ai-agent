package org.wwz.ai.domain.agent.rag.model.schema;

import lombok.Data;

/** Schema knowledge for one model column, including recall and analysis policy. */
@Data
public class DataQuerySchema {
    private String modelCode;
    private String columnId;
    private String columnName;
    private String columnComment;
    private String fewShot;
    private String dataType;
    private String synonyms;
    private String vectorUuid;
    private int defaultRecall;
    private int analyzeSuggest;
}
