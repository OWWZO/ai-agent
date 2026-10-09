package org.wwz.ai.domain.agent.catalog.model;

/**
 * 对外目录使用的 MCP 安全投影，只包含可展示字段。
 */
public record CatalogMcp(
        String mcpId,
        String mcpName,
        String transportType,
        Integer status) {
}
