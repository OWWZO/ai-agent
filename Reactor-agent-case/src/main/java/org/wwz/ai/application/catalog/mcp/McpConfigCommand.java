package org.wwz.ai.application.catalog.mcp;

/**
 * MCP 配置管理写命令。
 */
public record McpConfigCommand(
        Long id,
        String mcpId,
        String mcpName,
        String transportType,
        String transportConfig,
        Integer requestTimeout,
        Integer status) {
}
