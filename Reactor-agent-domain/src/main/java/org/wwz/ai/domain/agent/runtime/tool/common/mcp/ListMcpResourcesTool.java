package org.wwz.ai.domain.agent.runtime.tool.common.mcp;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.dto.tool.McpResourceInfo;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.mcp.runtime.McpToolExecutor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 列出 MCP resources（对齐 cc-haha ListMcpResourcesTool）。
 */
@Slf4j
@Data
public class ListMcpResourcesTool implements BaseTool {

    private AgentContext agentContext;

    @Override
    public String getName() {
        return McpToolNames.LIST_MCP_RESOURCES;
    }

    @Override
    public String getDescription() {
        return "列出已连接 MCP 服务器上的 resources。"
                + " 可选 server 过滤（mcpId 或 serverKey）。"
                + " 读取内容请用 ReadMcpResource。";
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> server = new LinkedHashMap<>();
        server.put("type", "string");
        server.put("description", "可选：mcpId 或 serverKey，过滤单个 MCP 服务器");
        properties.put("server", server);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", Collections.emptyList());
        return parameters;
    }

    @Override
    public Object execute(Object input) {
        try {
            McpToolExecutor executor = resolveExecutor();
            if (executor == null) {
                return ToolResultPayload.failure("ListMcpResources 失败：McpToolExecutor 不可用",
                        "ListMcpResources 失败：McpToolExecutor 不可用", null, "no executor");
            }
            Map<String, Object> args = coerceMap(input);
            String server = stringArg(args, "server");
            List<McpResourceInfo> resources = StringUtils.isBlank(server)
                    ? executor.listGlobalResources()
                    : executor.listResources(server);

            List<Map<String, Object>> rows = new ArrayList<>();
            for (McpResourceInfo resource : resources) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("uri", resource.getUri());
                row.put("name", resource.getName());
                row.put("title", resource.getTitle());
                row.put("description", resource.getDescription());
                row.put("mimeType", resource.getMimeType());
                row.put("size", resource.getSize());
                row.put("server", resource.getServerKey());
                row.put("mcpId", resource.getMcpId());
                rows.add(row);
            }
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("resources", rows);
            fields.put("count", rows.size());
            return ToolResultPayload.okData(McpToolNames.LIST_MCP_RESOURCES, fields);
        } catch (Exception e) {
            log.warn("ListMcpResources failed", e);
            String msg = "ListMcpResources 失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            return ToolResultPayload.failureFrom(msg, null);
        }
    }

    private McpToolExecutor resolveExecutor() {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            return null;
        }
        return agentContext.getRuntimeDependencies().getOptionalMcpToolExecutor();
    }

    private static String stringArg(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> coerceMap(Object input) {
        if (input == null) {
            return Collections.emptyMap();
        }
        if (input instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> {
                if (k != null) {
                    result.put(String.valueOf(k), v);
                }
            });
            return result;
        }
        if (input instanceof String str && StringUtils.isNotBlank(str)) {
            try {
                Object parsed = com.alibaba.fastjson.JSON.parse(str);
                if (parsed instanceof Map<?, ?>) {
                    return coerceMap(parsed);
                }
            } catch (Exception ignored) {
                // fall through
            }
        }
        return Collections.emptyMap();
    }
}
