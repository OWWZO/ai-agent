package org.wwz.ai.domain.agent.runtime.tool.common.mcp;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.deferred.DeferredToolCatalog;
import org.wwz.ai.domain.agent.runtime.tool.deferred.DeferredToolEntry;
import org.wwz.ai.domain.agent.runtime.tool.mcp.runtime.DeferredToolCall;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 返回延迟本地/MCP 工具的完整 parameter schema。
 */
@Slf4j
@Data
public class ToolDescribeTool implements BaseTool {

    private AgentContext agentContext;

    @Override
    public String getName() {
        return McpToolNames.TOOL_DESCRIBE;
    }

    @Override
    public String getDescription() {
        return "Load the full JSON schema for one deferred local/MCP tool returned by ToolSearch. "
                + "Required before ToolCall if the tool's parameters are unknown.";
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> name = new LinkedHashMap<>();
        name.put("type", "string");
        name.put("description", "Exact tool name (as returned by ToolSearch).");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("name", name);
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("name"));
        return parameters;
    }

    @Override
    public Object execute(Object input) {
        try {
            Map<String, Object> args = DeferredToolCall.coerceMap(input);
            String name = String.valueOf(args.getOrDefault("name", "")).trim();
            if (name.isEmpty() || "null".equals(name)) {
                return error("name is required");
            }
            if (McpToolNames.isBridge(name)) {
                return error("'" + name + "' is a bridge tool, not a deferred tool.");
            }
            DeferredToolCatalog catalog = agentContext == null || agentContext.getToolCollection() == null
                    ? null
                    : agentContext.getToolCollection().getDeferredToolCatalog();
            if (catalog == null || !catalog.contains(name)) {
                return error("'" + name + "' is not a deferred tool in this collection. "
                        + "Re-run ToolSearch, or call it directly if it already appears in tools[].");
            }
            DeferredToolEntry tool = catalog.get(name);
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("name", tool.getName());
            fields.put("source", tool.isLocal() ? "local" : "mcp");
            fields.put("source_name", tool.getSourceName());
            fields.put("description", tool.getDescription());
            fields.put("parameters", tool.getParameters());
            return ToolResultPayload.okData(McpToolNames.TOOL_DESCRIBE, fields);
        } catch (Exception e) {
            log.warn("ToolDescribe failed", e);
            String msg = "ToolDescribe 失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            return ToolResultPayload.failureFrom(msg, null);
        }
    }

    private static ToolResultPayload error(String message) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("error", message);
        return ToolResultPayload.softFailData(McpToolNames.TOOL_DESCRIBE, fields);
    }
}
