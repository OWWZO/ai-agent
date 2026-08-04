package org.wwz.ai.domain.agent.reactor.data.dto;

import lombok.Data;

/**
 * 问数 schema 传输对象，描述可供自然语言召回的表字段信息。
 */
@Data
public class ChatSchemaDto {
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
