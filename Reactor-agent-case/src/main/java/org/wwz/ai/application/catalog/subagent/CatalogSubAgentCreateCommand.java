package org.wwz.ai.application.catalog.subagent;

import java.util.Set;

/** 普通目录入口创建 SubAgent 的应用命令。 */
public record CatalogSubAgentCreateCommand(
        String agentKey,
        String displayName,
        String whenToUse,
        String systemPrompt,
        Set<String> allowedTools,
        Set<String> disallowedTools,
        String toolPolicyMode,
        Set<String> deferredTools,
        Integer maxSteps,
        Integer status) {
}
