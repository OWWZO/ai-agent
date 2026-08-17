package org.wwz.ai.domain.agent.runtime.tool.mcp.runtime;

import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.runtime.dto.tool.McpResourceInfo;
import org.wwz.ai.domain.agent.runtime.dto.tool.McpToolInfo;

import java.util.Collections;
import java.util.List;

/**
 * MCP 工具统一执行器。
 * Reactor 侧继续维持原有调用方式，但底层统一走 McpRegistry 复用预热好的客户端和工具缓存。
 */
@Service
public class McpToolExecutor {

    @Resource
    private McpRegistry mcpRegistry;

    /**
     * 获取当前全局启用的 MCP 工具列表。
     */
    public List<McpToolInfo> discoverConfiguredTools() {
        return mcpRegistry.listGlobalEnabledTools();
    }

    /**
     * 兼容保留：根据 mcpId 列表获取工具列表。
     */
    public List<McpToolInfo> discoverTools(List<String> mcpIds) {
        if (mcpIds == null || mcpIds.isEmpty()) {
            return Collections.emptyList();
        }
        return mcpRegistry.listToolsByMcpIds(mcpIds);
    }

    /**
     * 全局 resources。
     */
    public List<McpResourceInfo> listGlobalResources() {
        return mcpRegistry.listGlobalEnabledResources();
    }

    /**
     * 按 server / mcpId 列出 resources。
     */
    public List<McpResourceInfo> listResources(String serverOrMcpId) {
        return mcpRegistry.listResources(serverOrMcpId);
    }

    /**
     * 读取 resource。
     */
    public String readResource(String serverOrMcpId, String uri) {
        return mcpRegistry.readResource(serverOrMcpId, uri);
    }

    /**
     * 是否存在可用 resources。
     */
    public boolean hasAnyResources() {
        return mcpRegistry.hasAnyResources();
    }

    /**
     * 执行单个 MCP 工具（wire 使用 originalName）。
     */
    public String executeTool(McpToolInfo toolInfo, Object args) {
        if (toolInfo == null || StringUtils.isBlank(toolInfo.getName())) {
            return "ToolUnknown Error.";
        }

        String mcpId = StringUtils.defaultIfBlank(toolInfo.getMcpId(), toolInfo.getServerKey());
        String wireName = toolInfo.resolveWireName();
        return mcpRegistry.executeTool(mcpId, wireName, args);
    }
}
