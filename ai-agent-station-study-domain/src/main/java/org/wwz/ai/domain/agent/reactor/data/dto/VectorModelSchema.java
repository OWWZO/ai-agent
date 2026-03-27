package org.wwz.ai.domain.agent.reactor.data.dto;

import lombok.Data;

@Data
public class VectorModelSchema {
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
