package org.wwz.ai.application.catalog.mcp;

/**
 * MCP 创建默认值。
 * <p>
 * 管理端 CRUD 与普通目录创建是同一个能力的两条入口，默认值必须同源，
 * 否则同一份配置在两条路径下会写入不同的 transportType / requestTimeout / status。
 */
final class McpDefaults {

    static final String DEFAULT_TRANSPORT_TYPE = "streamable_http";
    static final int DEFAULT_REQUEST_TIMEOUT = 5;

    private McpDefaults() {
    }

    /** 只有显式 0 表示禁用，其余一律视为启用。 */
    static int normalizeStatus(Integer status) {
        return status != null && status == 0 ? 0 : 1;
    }
}
