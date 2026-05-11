package org.wwz.ai.domain.agent.reactor.data.dto;

import lombok.Data;

import java.util.List;
@Data
public class ChatModelInfoDto {
    private String modelCode;
    private String modelName;
    private String usePrompt;
    private String businessPrompt;
    private String type;
    private String content;
    private List<ChatSchemaDto> schemaList;
}
