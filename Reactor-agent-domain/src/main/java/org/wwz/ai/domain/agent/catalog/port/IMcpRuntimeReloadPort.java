package org.wwz.ai.domain.agent.catalog.port;

import org.wwz.ai.domain.agent.catalog.model.McpRuntimeReloadResult;

/**
 * MCP 配置写入后的运行时刷新端口。
 */
public interface IMcpRuntimeReloadPort {

    McpRuntimeReloadResult reloadEnabledMcps();
}
