package org.wwz.ai.trigger.http.dataagent.vo;

import lombok.Data;

@Data
public class DataQuerySchemaResponseVO {
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
