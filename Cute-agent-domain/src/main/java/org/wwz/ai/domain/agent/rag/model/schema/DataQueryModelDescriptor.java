package org.wwz.ai.domain.agent.rag.model.schema;

import lombok.Data;

import java.util.List;

/** Model context sent to NL2SQL generation and exposed by model metadata queries. */
@Data
public class DataQueryModelDescriptor {
    private String modelCode;
    private String modelName;
    private String usePrompt;
    private String businessPrompt;
    private String type;
    private String content;
    private List<DataQuerySchema> schemaList;
}
