package org.wwz.ai.application.catalog.mcp;

/**
 * MCP 配置管理查询命令。
 */
public record McpConfigQueryCommand(
        String mcpId,
        String mcpName,
        String transportType,
        Integer status,
        Integer pageNum,
        Integer pageSize) {
}
