package org.wwz.ai.trigger.http.dataagent.vo;

import lombok.Data;

import java.util.List;

@Data
public class DataQueryModelResponseVO {
    private String modelCode;
    private String modelName;
    private String usePrompt;
    private String businessPrompt;
    private String type;
    private String content;
    private List<DataQuerySchemaResponseVO> schemaList;
}
