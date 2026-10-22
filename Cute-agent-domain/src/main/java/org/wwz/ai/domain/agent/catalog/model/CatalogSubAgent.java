package org.wwz.ai.domain.agent.catalog.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * 登录用户可见的子 Agent 安全目录项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogSubAgent {

    private String agentKey;
    private String displayName;
    private String whenToUse;
    private Set<String> allowedTools;
    private Set<String> disallowedTools;
    private String toolPolicyMode;
    private Integer maxSteps;
    private Integer status;
}
