package org.wwz.ai.infrastructure.adapter.port.catalog;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.catalog.model.McpRuntimeReloadResult;
import org.wwz.ai.domain.agent.catalog.port.IMcpRuntimeReloadPort;
import org.wwz.ai.infrastructure.mcp.registry.McpRegistry;

/** MCP 注册中心刷新适配器。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpRuntimeReloadAdapter implements IMcpRuntimeReloadPort {
    private final ObjectProvider<McpRegistry> registryProvider;

    public McpRuntimeReloadResult reloadEnabledMcps() {
        McpRegistry registry = registryProvider.getIfAvailable();
        if (registry == null) {
            log.warn("MCP runtime reload skipped because the registry is unavailable");
            return McpRuntimeReloadResult.failed("MCP runtime unavailable");
        }
        try {
            registry.reload();
            return McpRuntimeReloadResult.succeeded();
        } catch (Exception e) {
            log.warn("MCP runtime reload failed after configuration change", e);
            return McpRuntimeReloadResult.failed("MCP runtime reload failed");
        }
    }
}
