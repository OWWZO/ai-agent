package org.wwz.ai.domain.agent.runtime.llm;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.runtime.dto.tool.McpToolInfo;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.mcp.runtime.McpRegistry;
import org.wwz.ai.domain.agent.runtime.tool.mcp.runtime.RegistryBackedToolCallback;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一组装 Reactor Agent 在 Spring AI 下可声明给模型的工具回调。
 */
@Component
public class LlmToolCallbackProvider {

    @Resource
    private McpRegistry mcpRegistry;

    /**
     * 基于当前 ToolCollection 构建 ToolCallback 列表。
     */
    public List<ToolCallback> buildToolCallbacks(ToolCollection tools) {
        List<ToolCallback> callbacks = new ArrayList<>();
        if (tools == null) {
            return callbacks;
        }

        for (BaseTool tool : tools.getToolMap().values()) {
            callbacks.add(new BaseToolCallbackAdapter(tool));
        }
        for (McpToolInfo toolInfo : tools.getMcpToolMap().values()) {
            callbacks.add(new RegistryBackedToolCallback(mcpRegistry, toolInfo));
        }
        return callbacks;
    }
}
