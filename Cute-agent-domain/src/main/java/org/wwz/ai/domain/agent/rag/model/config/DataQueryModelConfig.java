package org.wwz.ai.domain.agent.rag.model.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A configured analytical model and its schema-generation rules. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataQueryModelConfig {
    private String name;
    private String id;
    private String type;
    private String content;
    private String remark;
    private String businessPrompt;
    private String ignoreFields;
    private String defaultRecallFields;
    private String analyzeSuggestFields;
    private String analyzeForbidFields;
    private String syncValueFields;
    private String columnAliasMap;
}
