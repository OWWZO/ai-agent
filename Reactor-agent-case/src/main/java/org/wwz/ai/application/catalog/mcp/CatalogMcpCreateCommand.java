package org.wwz.ai.application.catalog.mcp;

/**
 * 普通目录入口创建 MCP 的应用命令。
 */
public record CatalogMcpCreateCommand(
        String mcpId,
        String mcpName,
        String transportType,
        String transportConfig,
        Integer requestTimeout,
        Integer status) {
}
