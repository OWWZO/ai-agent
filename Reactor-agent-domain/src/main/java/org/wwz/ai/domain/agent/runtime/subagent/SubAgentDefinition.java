package org.wwz.ai.domain.agent.runtime.subagent;

import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.Set;

/**
 * 子 Agent 类型定义（对标 cc-haha AgentDefinition）。
 * 描述 system prompt、工具 allow/deny 与步数上限。
 */
@Data
@Builder
public class SubAgentDefinition {

    /** 类型名，如 Explore / general-purpose */
    private String agentType;

    /** 何时使用该类型（注入主 Agent 的工具描述） */
    private String whenToUse;

    /** 子 Agent 专属 system 补充提示 */
    private String systemPrompt;

    /**
     * 允许的工具名；null 或含 "*" 表示在全局过滤后全部允许。
     */
    @Builder.Default
    private Set<String> allowedTools = null;

    /**
     * 额外禁止的工具名（在全局禁止之上叠加）。
     */
    @Builder.Default
    private Set<String> disallowedTools = Collections.emptySet();

    /** 最大步数；null 表示沿用 React 配置 */
    private Integer maxSteps;

    public boolean allowsAllTools() {
        return allowedTools == null
                || allowedTools.isEmpty()
                || allowedTools.contains("*");
    }
}
