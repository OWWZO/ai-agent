package org.wwz.ai.infrastructure.mcp.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.wwz.ai.domain.agent.runtime.tool.mcp.model.McpToolInfo;
import org.wwz.ai.domain.agent.runtime.llm.ToolDefinitionCache;
import org.wwz.ai.domain.agent.runtime.tool.mcp.port.McpToolExecutor;

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
    private final McpToolExecutor mcpToolExecutor;

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
                org.wwz.ai.domain.agent.runtime.llm.LlmToolDefinition definition =
                        ToolDefinitionCache.getOrCreateFromRawSchemaString(
                                toolInfo.getName(),
                                StringUtils.defaultString(toolInfo.getDesc()),
                                toolInfo.getParameters());
                cachedDefinition = DefaultToolDefinition.builder()
                        .name(definition.getName())
                        .description(definition.getDescription())
                        .inputSchema(definition.getInputSchema())
                        .build();
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
            return mcpToolExecutor.callTool(toolInfo.getMcpId(), toolInfo.resolveWireName(), toolInput);
        } catch (RuntimeException e) {
            log.error("Registry ToolCallback 调用失败: mcpId={}, toolName={}, wireName={}, reason={}",
                    toolInfo.getMcpId(), toolInfo.getName(), toolInfo.resolveWireName(), e.getMessage(), e);
            throw e;
        }
    }
}
