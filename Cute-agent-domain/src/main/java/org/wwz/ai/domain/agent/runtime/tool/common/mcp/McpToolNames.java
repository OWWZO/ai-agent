package org.wwz.ai.domain.agent.runtime.tool.common.mcp;

import java.util.Set;

/**
 * MCP 元工具名称。
 */
public final class McpToolNames {

    public static final String TOOL_SEARCH = "ToolSearch";
    public static final String TOOL_DESCRIBE = "ToolDescribe";
    public static final String TOOL_CALL = "ToolCall";
    public static final String LIST_MCP_RESOURCES = "ListMcpResources";
    public static final String READ_MCP_RESOURCE = "ReadMcpResource";

    public static final Set<String> BRIDGE_NAMES = Set.of(TOOL_SEARCH, TOOL_DESCRIBE, TOOL_CALL);

    private McpToolNames() {
    }

    public static boolean isBridge(String name) {
        return name != null && BRIDGE_NAMES.contains(name);
    }
}
