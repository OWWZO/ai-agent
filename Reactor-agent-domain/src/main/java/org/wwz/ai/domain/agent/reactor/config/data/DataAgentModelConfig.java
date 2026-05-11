package org.wwz.ai.domain.agent.reactor.config.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataAgentModelConfig {
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
