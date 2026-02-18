package org.wwz.ai.domain.agent.genie.entity;

import lombok.Data;

import java.io.Serializable;

@Data
public class ChatModelSchema implements Serializable {
    private static final long serialVersionUID = -6284827149526794290L;
    private Long id;
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
    private Integer yn;

}
