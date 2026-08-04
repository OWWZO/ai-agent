package org.wwz.ai.domain.agent.reactor.data.dto;

import lombok.Data;

import java.util.List;
/**
 * 问数模型信息传输对象，供模型召回和提示词组装使用。
 */
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
