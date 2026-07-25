package org.wwz.ai.trigger.http.admin.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 子 Agent 定义创建/更新请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubAgentDefinitionUpsertReqVO {

    private String agentKey;

    private String displayName;

    private String whenToUse;

    private String systemPrompt;

    private List<String> allowedTools;

    private List<String> disallowedTools;

    private Integer maxSteps;

    private Integer status;
}
