package org.wwz.ai.domain.agent.runtime.tool.common.mcp;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.mcp.runtime.DeferredToolCall;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 延迟本地/MCP 工具的 invoke 入口。生产主路径由 pipeline 解包；此处仅防漏解包。
 */
@Slf4j
@Data
public class ToolCallTool implements BaseTool {

    private AgentContext agentContext;

    @Override
    public String getName() {
        return McpToolNames.TOOL_CALL;
    }

    @Override
    public String getDescription() {
        return "Invoke a deferred local or MCP tool by name with the given arguments. "
                + "Argument shape matches the tool's schema (see ToolDescribe). "
                + "Do not call deferred tool names as direct function calls.";
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> name = new LinkedHashMap<>();
        name.put("type", "string");
        name.put("description", "Exact tool name to invoke.");
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("type", "object");
        arguments.put("description", "Arguments for the tool, matching its schema.");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("name", name);
        properties.put("arguments", arguments);
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("name", "arguments"));
        return parameters;
    }

    @Override
    public Object execute(Object input) {
        try {
            ToolCollection collection = agentContext == null ? null : agentContext.getToolCollection();
            DeferredToolCall.Result result = DeferredToolCall.resolve(collection, input);
            if (!result.ok()) {
                return result.errorPayload();
            }
            if (collection == null) {
                return ToolResultPayload.failure("ToolCall 失败：无 ToolCollection",
                        "ToolCall 失败：无 ToolCollection", null, "no collection");
            }
            return collection.executeResolved(result.name(), result.arguments());
        } catch (Exception e) {
            log.warn("ToolCall failed", e);
            String msg = "ToolCall 失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            return ToolResultPayload.failureFrom(msg, null);
        }
    }
}
