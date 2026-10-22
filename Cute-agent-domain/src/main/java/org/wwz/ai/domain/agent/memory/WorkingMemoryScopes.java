package org.wwz.ai.domain.agent.memory;

import org.apache.commons.lang3.StringUtils;

/**
 * working_memory 投影作用域：主会话与子 Agent 侧链隔离。
 */
public final class WorkingMemoryScopes {

    public static final String MAIN = "main";

    private WorkingMemoryScopes() {
    }

    public static String normalize(String scope) {
        return StringUtils.isBlank(scope) ? MAIN : scope.trim();
    }

    public static String forSubAgent(String agentId) {
        if (StringUtils.isBlank(agentId)) {
            throw new IllegalArgumentException("agentId 不能为空");
        }
        return "sub:" + agentId.trim();
    }

    public static boolean isSubScope(String scope) {
        String s = normalize(scope);
        return s.startsWith("sub:") && s.length() > 4;
    }

    public static String extractSubAgentId(String scope) {
        if (!isSubScope(scope)) {
            return null;
        }
        return normalize(scope).substring(4);
    }
}
