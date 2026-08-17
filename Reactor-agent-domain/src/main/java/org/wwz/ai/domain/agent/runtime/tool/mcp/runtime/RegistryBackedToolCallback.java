package org.wwz.ai.domain.agent.runtime.tool.mcp.runtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.wwz.ai.domain.agent.runtime.dto.tool.McpToolInfo;
import org.wwz.ai.domain.agent.runtime.llm.ToolDefinitionCache;

/**
 * 基于 McpRegistry 的 ToolCallback 实现。
 * 只缓存工具元信息，实际调用统一回到 registry，由 registry 按传输协议选择最合适的执行策略。
 */
@Slf4j
@RequiredArgsConstructor
public class RegistryBackedToolCallback implements ToolCallback {

    /**
     * MCP 统一注册中心。
     */
    private final McpRegistry mcpRegistry;

    /**
     * 工具元信息快照。
     */
    private final McpToolInfo toolInfo;

    private volatile ToolDefinition cachedDefinition;

    @Override
    public ToolDefinition getToolDefinition() {
        ToolDefinition local = cachedDefinition;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (cachedDefinition == null) {
                cachedDefinition = ToolDefinitionCache.getOrCreateFromRawSchemaString(
                        toolInfo.getName(),
                        StringUtils.defaultString(toolInfo.getDesc()),
                        toolInfo.getParameters()
                );
            }
            return cachedDefinition;
        }
    }

    @Override
    public String call(String toolInput) {
        return execute(toolInput);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return execute(toolInput);
    }

    /**
     * 将 Spring AI 的工具调用统一路由到 registry。
     */
    private String execute(String toolInput) {
        try {
            // ToolDefinition 使用 FQ 名；call 使用服务端原始名。
            return mcpRegistry.executeTool(toolInfo.getMcpId(), toolInfo.resolveWireName(), toolInput);
        } catch (RuntimeException e) {
            log.error("Registry ToolCallback 调用失败: mcpId={}, toolName={}, wireName={}, reason={}",
                    toolInfo.getMcpId(), toolInfo.getName(), toolInfo.resolveWireName(), e.getMessage(), e);
            throw e;
        }
    }
}
