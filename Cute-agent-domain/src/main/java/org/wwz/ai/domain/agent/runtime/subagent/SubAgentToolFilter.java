package org.wwz.ai.domain.agent.runtime.subagent;

import org.wwz.ai.domain.agent.memory.ltm.LtmMemoryGuard;
import org.wwz.ai.domain.agent.runtime.tool.mcp.model.McpToolInfo;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.common.AgentDispatchTool;
import org.wwz.ai.domain.agent.runtime.tool.common.SessionSearchTool;
import org.wwz.ai.domain.agent.runtime.tool.common.mcp.McpToolNames;
import org.wwz.ai.domain.agent.runtime.tool.common.mcp.ToolCallTool;
import org.wwz.ai.domain.agent.runtime.tool.common.mcp.ToolDescribeTool;
import org.wwz.ai.domain.agent.runtime.tool.common.mcp.ToolSearchTool;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.TaskToolNames;
import org.wwz.ai.domain.agent.runtime.tool.deferred.DeferredToolCatalog;
import org.wwz.ai.domain.agent.runtime.tool.deferred.DeferredToolEntry;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 子 Agent 工具池过滤。
 *
 * <p>通用子 Agent 继承父 Agent 的完整有效视图；领域子 Agent 在 custom
 * 模式下按自己的 allowedTools/deferredTools 重新划分 eager 与 catalog。</p>
 */
public final class SubAgentToolFilter {

    private SubAgentToolFilter() {
    }

    public static ToolCollection filter(ToolCollection parentTools, SubAgentDefinition definition) {
        return filter(parentTools, definition, false);
    }

    /**
     * @param parentInPlanMode 保留参数以兼容旧调用；plan mode 不再剥离子 Agent 写工具
     */
    @SuppressWarnings("unused")
    public static ToolCollection filter(ToolCollection parentTools,
                                        SubAgentDefinition definition,
                                        boolean parentInPlanMode) {
        ToolCollection child = new ToolCollection();
        if (parentTools == null || definition == null) {
            return child;
        }
        child.setMcpToolExecutor(parentTools.getMcpToolExecutor());
        child.restoreTaskScopedState(parentTools.snapshotTaskScopedState());

        Set<String> disallowed = buildDisallowed(definition);
        if (definition.usesCustomToolPolicy()) {
            copyCustomView(parentTools, child, definition, disallowed);
        } else {
            copyInheritedView(parentTools, child, definition, disallowed);
        }
        alignBridgeTools(child, parentTools, disallowed);
        return child;
    }

    private static Set<String> buildDisallowed(SubAgentDefinition definition) {
        Set<String> disallowed = new HashSet<>();
        disallowed.add(AgentDispatchTool.NAME);
        disallowed.add(TaskToolNames.TASK_STOP);
        disallowed.add(TaskToolNames.TASK_OUTPUT);
        disallowed.add(TaskToolNames.SEND_MESSAGE);
        disallowed.add(TaskToolNames.ENTER_PLAN_MODE);
        disallowed.add(TaskToolNames.EXIT_PLAN_MODE);
        disallowed.add(org.wwz.ai.domain.agent.runtime.tool.common.planmode.AskUserQuestionTool.NAME);
        disallowed.add(org.wwz.ai.domain.agent.runtime.tool.common.planmode.RequestDesktopControlTool.NAME);
        disallowed.addAll(LtmMemoryGuard.MEMORY_WRITE_TOOLS);
        disallowed.add(SessionSearchTool.TOOL_NAME);
        if (definition.getDisallowedTools() != null) {
            disallowed.addAll(definition.getDisallowedTools());
        }
        return disallowed;
    }

    private static void copyInheritedView(ToolCollection parentTools,
                                          ToolCollection child,
                                          SubAgentDefinition definition,
                                          Set<String> disallowed) {
        boolean allowAll = definition.allowsAllTools();
        Set<String> allowed = definition.getAllowedTools() == null
                ? Set.of()
                : definition.getAllowedTools();

        if (parentTools.getToolMap() != null) {
            for (Map.Entry<String, BaseTool> entry : parentTools.getToolMap().entrySet()) {
                if (isAllowed(entry.getKey(), allowAll, allowed, disallowed)) {
                    child.addTool(entry.getValue());
                }
            }
        }
        if (parentTools.getMcpToolMap() != null) {
            for (McpToolInfo info : parentTools.getMcpToolMap().values()) {
                if (info != null && isAllowed(info.getName(), allowAll, allowed, disallowed)) {
                    child.addMcpTool(info);
                }
            }
        }

        DeferredToolCatalog parentCatalog = parentTools.getDeferredToolCatalog();
        if (parentCatalog != null && parentCatalog.size() > 0) {
            DeferredToolCatalog childCatalog = parentCatalog.filter(entry ->
                    isAllowed(entry.getName(), allowAll || allowed.contains(McpToolNames.TOOL_CALL),
                            allowed, disallowed));
            if (childCatalog.size() > 0) {
                child.setDeferredToolCatalog(childCatalog);
            }
        }
    }

    private static void copyCustomView(ToolCollection parentTools,
                                       ToolCollection child,
                                       SubAgentDefinition definition,
                                       Set<String> disallowed) {
        boolean allowAll = definition.allowsAllTools();
        Set<String> allowed = definition.getAllowedTools() == null
                ? Set.of()
                : definition.getAllowedTools();
        Set<String> deferredNames = definition.getDeferredTools() == null
                ? Set.of()
                : definition.getDeferredTools();
        java.util.List<DeferredToolEntry> deferred = new java.util.ArrayList<>();

        if (parentTools.getToolMap() != null) {
            for (Map.Entry<String, BaseTool> entry : parentTools.getToolMap().entrySet()) {
                String name = entry.getKey();
                if (!isAllowed(name, allowAll, allowed, disallowed)) {
                    continue;
                }
                if (deferredNames.contains(name)) {
                    deferred.add(DeferredToolCatalog.localEntry(entry.getValue(), "reactor"));
                } else {
                    child.addTool(entry.getValue());
                }
            }
        }
        if (parentTools.getMcpToolMap() != null) {
            for (McpToolInfo info : parentTools.getMcpToolMap().values()) {
                if (info == null || !isAllowed(info.getName(), allowAll, allowed, disallowed)) {
                    continue;
                }
                if (deferredNames.contains(info.getName())) {
                    deferred.add(DeferredToolCatalog.mcpEntry(info));
                } else {
                    child.addMcpTool(info);
                }
            }
        }

        DeferredToolCatalog parentCatalog = parentTools.getDeferredToolCatalog();
        if (parentCatalog != null) {
            boolean retainParentCatalog = allowAll || allowed.contains(McpToolNames.TOOL_CALL);
            for (DeferredToolEntry entry : parentCatalog.listAll()) {
                if (!isAllowed(entry.getName(),
                        retainParentCatalog,
                        allowed,
                        disallowed)) {
                    continue;
                }
                if (retainParentCatalog || deferredNames.contains(entry.getName())) {
                    deferred.add(entry);
                } else if (entry.isLocal()) {
                    child.addTool(entry.getLocalTool());
                } else {
                    child.addMcpTool(entry.getMcpToolInfo());
                }
            }
        }
        if (!deferred.isEmpty()) {
            child.setDeferredToolCatalog(new DeferredToolCatalog(deferred));
        }
    }

    private static boolean isAllowed(String name,
                                     boolean allowAll,
                                     Set<String> allowed,
                                     Set<String> disallowed) {
        if (name == null || disallowed.contains(name)) {
            return false;
        }
        return allowAll || allowed.contains(name);
    }

    private static void alignBridgeTools(ToolCollection child,
                                         ToolCollection parentTools,
                                         Set<String> disallowed) {
        boolean keep = child.getDeferredToolCatalog() != null
                && child.getDeferredToolCatalog().size() > 0;
        for (String name : McpToolNames.BRIDGE_NAMES) {
            if (!keep || disallowed.contains(name)) {
                if (child.getToolMap() != null) {
                    child.getToolMap().remove(name);
                }
                continue;
            }
            if (child.getTool(name) != null) {
                continue;
            }
            BaseTool tool = parentTools.getTool(name);
            if (tool == null) {
                tool = switch (name) {
                    case McpToolNames.TOOL_SEARCH -> new ToolSearchTool();
                    case McpToolNames.TOOL_DESCRIBE -> new ToolDescribeTool();
                    case McpToolNames.TOOL_CALL -> new ToolCallTool();
                    default -> null;
                };
            }
            if (tool != null) {
                child.addTool(tool);
            }
        }
    }
}
