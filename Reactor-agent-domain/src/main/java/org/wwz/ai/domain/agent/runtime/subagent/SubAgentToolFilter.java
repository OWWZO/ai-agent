package org.wwz.ai.domain.agent.runtime.subagent;

import org.wwz.ai.domain.agent.runtime.dto.tool.McpToolInfo;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ContextScopedTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.memory.ltm.LtmMemoryGuard;
import org.wwz.ai.domain.agent.runtime.tool.common.AgentDispatchTool;
import org.wwz.ai.domain.agent.runtime.tool.common.MemoryTool;
import org.wwz.ai.domain.agent.runtime.tool.common.SessionSearchTool;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.TaskToolNames;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 子 Agent 工具池三层过滤（对标 cc-haha filterToolsForAgent / resolveAgentTools）。
 * 1) 全局禁止 Agent 自身（防递归）
 * 2) 定义 disallowedTools
 * 3) 定义 allowedTools 白名单
 */
public final class SubAgentToolFilter {

    private SubAgentToolFilter() {
    }

    private static final Set<String> PLAN_MODE_MUTATING = Set.of(
            "workspace_write",
            "workspace_edit",
            "file_tool",
            "code_interpreter",
            "report_tool",
            "image_generation",
            "data_analysis",
            "multimodalagent_tool"
    );

    /**
     * 从父工具池筛选出子 Agent 可用工具。
     * 此处先共享引用；调用方必须 {@link ContextScopedTool#bindAll} /
     * {@link org.wwz.ai.domain.agent.runtime.tool.ToolIsolation#bindAll}，
     * 将工具隔离为子 Agent 独占实例（优先）或共享锁 rebind（兜底）。
     */
    public static ToolCollection filter(ToolCollection parentTools, SubAgentDefinition definition) {
        return filter(parentTools, definition, false);
    }

    /**
     * @param parentInPlanMode 父会话处于 plan mode 时，额外剥离写工具（对标 cchaha 只读子代理）
     */
    public static ToolCollection filter(ToolCollection parentTools,
                                        SubAgentDefinition definition,
                                        boolean parentInPlanMode) {
        ToolCollection child = new ToolCollection();
        if (parentTools == null || definition == null) {
            return child;
        }
        child.setMcpToolExecutor(parentTools.getMcpToolExecutor());
        child.restoreTaskScopedState(parentTools.snapshotTaskScopedState());

        Set<String> disallowed = new HashSet<>();
        disallowed.add(AgentDispatchTool.NAME);
        // 对标 cc-haha ALL_AGENT_DISALLOWED：子 Agent 禁止 TaskStop / Enter/Exit PlanMode
        disallowed.add(TaskToolNames.TASK_STOP);
        disallowed.add(TaskToolNames.ENTER_PLAN_MODE);
        disallowed.add(TaskToolNames.EXIT_PLAN_MODE);
        disallowed.add(org.wwz.ai.domain.agent.runtime.tool.common.planmode.AskUserQuestionTool.NAME);
        // skip_memory：子代理禁止写长期记忆工具（及深度 Provider 写工具）
        disallowed.add(MemoryTool.TOOL_NAME);
        disallowed.addAll(LtmMemoryGuard.MEMORY_WRITE_TOOLS);
        // session_search 只读可保留；若希望子代理完全无 LTM 面，一并剥离
        disallowed.add(SessionSearchTool.TOOL_NAME);
        if (definition.getDisallowedTools() != null) {
            disallowed.addAll(definition.getDisallowedTools());
        }
        if (parentInPlanMode) {
            disallowed.addAll(PLAN_MODE_MUTATING);
        }

        boolean allowAll = definition.allowsAllTools();
        Set<String> allowed = definition.getAllowedTools() == null
                ? Set.of()
                : definition.getAllowedTools();

        if (parentTools.getToolMap() != null) {
            for (Map.Entry<String, BaseTool> entry : parentTools.getToolMap().entrySet()) {
                String name = entry.getKey();
                if (disallowed.contains(name)) {
                    continue;
                }
                if (!allowAll && !allowed.contains(name)) {
                    continue;
                }
                child.addTool(entry.getValue());
            }
        }

        if (parentTools.getMcpToolMap() != null) {
            for (McpToolInfo info : parentTools.getMcpToolMap().values()) {
                if (info == null || info.getName() == null) {
                    continue;
                }
                if (disallowed.contains(info.getName())) {
                    continue;
                }
                if (!allowAll && !allowed.contains(info.getName())) {
                    continue;
                }
                child.addMcpTool(info);
            }
        }
        return child;
    }
}
