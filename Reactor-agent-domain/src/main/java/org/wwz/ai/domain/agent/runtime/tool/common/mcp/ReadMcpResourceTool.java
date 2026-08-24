package org.wwz.ai.domain.agent.runtime.tool.common.mcp;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.mcp.runtime.McpToolExecutor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 读取 MCP resource。
 */
@Slf4j
@Data
public class ReadMcpResourceTool implements BaseTool {

    private AgentContext agentContext;

    @Override
    public String getName() {
        return McpToolNames.READ_MCP_RESOURCE;
    }

    @Override
    public String getDescription() {
        return "读取指定 MCP 服务器上的 resource 内容。"
                + " 需要 server（mcpId 或 serverKey）与 uri。"
                + " 文本直接返回；二进制 blob 以摘要形式返回（不内嵌完整 base64）。";
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> server = new LinkedHashMap<>();
        server.put("type", "string");
        server.put("description", "mcpId 或 serverKey");
        properties.put("server", server);

        Map<String, Object> uri = new LinkedHashMap<>();
        uri.put("type", "string");
        uri.put("description", "resource URI");
        properties.put("uri", uri);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", java.util.List.of("server", "uri"));
        return parameters;
    }

    @Override
    public Object execute(Object input) {
        try {
            McpToolExecutor executor = resolveExecutor();
            if (executor == null) {
                return ToolResultPayload.failure("ReadMcpResource 失败：McpToolExecutor 不可用",
                        "ReadMcpResource 失败：McpToolExecutor 不可用", null, "no executor");
            }
            Map<String, Object> args = coerceMap(input);
            String server = stringArg(args, "server");
            String uri = stringArg(args, "uri");
            if (StringUtils.isBlank(server) || StringUtils.isBlank(uri)) {
                return ToolResultPayload.failure("ReadMcpResource 失败：server 与 uri 必填",
                        "ReadMcpResource 失败：server 与 uri 必填", null, "invalid args");
            }
            String content = executor.readResource(server, uri);
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("server", server);
            fields.put("uri", uri);
            fields.put("content", content);
            return ToolResultPayload.okData(McpToolNames.READ_MCP_RESOURCE, fields);
        } catch (Exception e) {
            log.warn("ReadMcpResource failed", e);
            String msg = "ReadMcpResource 失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
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
