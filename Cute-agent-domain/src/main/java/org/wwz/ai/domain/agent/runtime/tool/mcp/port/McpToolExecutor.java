package org.wwz.ai.domain.agent.runtime.tool.mcp.port;

import org.wwz.ai.domain.agent.runtime.tool.mcp.model.McpResourceInfo;
import org.wwz.ai.domain.agent.runtime.tool.mcp.model.McpToolInfo;

import java.util.List;

/**
 * MCP 外部能力端口。
 *
 * <p>该端口只暴露领域侧需要的发现、调用、资源和刷新语义，不泄漏 MCP SDK、
 * Spring AI callback、WebClient 或客户端生命周期对象。</p>
 */
public interface McpToolExecutor {

    List<McpToolInfo> listGlobalEnabledTools();

    List<McpToolInfo> listToolsByMcpIds(List<String> mcpIds);

    List<McpResourceInfo> listGlobalEnabledResources();

    List<McpResourceInfo> listResourcesByMcpIds(List<String> mcpIds);

    List<McpResourceInfo> listResources(String serverOrMcpId);

    boolean hasAnyResources();

    String readResource(String serverOrMcpId, String uri);

    String callTool(String mcpId, String toolName, Object args);

    void reload();

    /** 当前全局启用 MCP 工具发现的兼容别名。 */
    default List<McpToolInfo> discoverConfiguredTools() {
        return listGlobalEnabledTools();
    }

    /** 指定 MCP 工具发现的兼容别名。 */
    default List<McpToolInfo> discoverTools(List<String> mcpIds) {
        return listToolsByMcpIds(mcpIds);
    }

    /** 全局 resources 的兼容别名。 */
    default List<McpResourceInfo> listGlobalResources() {
        return listGlobalEnabledResources();
    }

    /** 以领域工具描述调用 MCP 工具的兼容入口。 */
    default String callTool(McpToolInfo toolInfo, Object args) {
        if (toolInfo == null || toolInfo.getName() == null || toolInfo.getName().isBlank()) {
            return "ToolUnknown Error.";
        }
        String mcpId = toolInfo.getMcpId() == null || toolInfo.getMcpId().isBlank()
                ? toolInfo.getServerKey()
                : toolInfo.getMcpId();
        return callTool(mcpId, toolInfo.resolveWireName(), args);
    }

    /** 历史调用方使用的执行命名兼容入口。 */
    default String executeTool(McpToolInfo toolInfo, Object args) {
        return callTool(toolInfo, args);
    }
}
