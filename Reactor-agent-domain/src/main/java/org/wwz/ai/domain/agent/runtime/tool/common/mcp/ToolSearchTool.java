package org.wwz.ai.domain.agent.runtime.tool.common.mcp;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.dto.tool.McpToolInfo;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.mcp.runtime.DeferredMcpCatalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 搜索并激活延迟加载的 MCP 工具。
 * <p>
 * query 支持关键词或 {@code select:toolA,toolB}；命中后写入当前 ToolCollection，
 * 下一轮 askTool 即可携带完整 schema。
 */
@Slf4j
@Data
public class ToolSearchTool implements BaseTool {

    private AgentContext agentContext;

    @Override
    public String getName() {
        return McpToolNames.TOOL_SEARCH;
    }

    @Override
    public String getDescription() {
        return "搜索并加载延迟注册的 MCP 工具完整 schema。"
                + " 可用工具名见 system 中 <available-deferred-tools>（仅名、无 schema）。"
                + " query 可为关键词，或 select:mcp__server__tool_a,mcp__server__tool_b。"
                + " 必须先 ToolSearch 再调用对应 MCP 工具。"
                + " max_results 默认 5。";
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("type", "string");
        query.put("description", "关键词，或 select:name1,name2");
        properties.put("query", query);

        Map<String, Object> maxResults = new LinkedHashMap<>();
        maxResults.put("type", "integer");
        maxResults.put("description", "最多返回条数，默认 5");
        properties.put("max_results", maxResults);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("query"));
        return parameters;
    }

    @Override
    public Object execute(Object input) {
        try {
            if (agentContext == null) {
                return ToolResultPayload.failure("ToolSearch 失败：无 AgentContext", "ToolSearch 失败：无 AgentContext", null, "no context");
            }
            DeferredMcpCatalog catalog = agentContext.getDeferredMcpCatalog();
            if (catalog == null || catalog.size() == 0) {
                Map<String, Object> empty = new LinkedHashMap<>();
                empty.put("matches", List.of());
                empty.put("query", "");
                empty.put("total_deferred_tools", 0);
                empty.put("message", "No deferred MCP tools in catalog");
                return ToolResultPayload.okData(McpToolNames.TOOL_SEARCH, empty);
            }

            Map<String, Object> args = coerceMap(input);
            String query = String.valueOf(args.getOrDefault("query", "")).trim();
            int maxResults = 5;
            Object maxRaw = args.get("max_results");
            if (maxRaw instanceof Number number) {
                maxResults = number.intValue();
            } else if (maxRaw != null && StringUtils.isNotBlank(String.valueOf(maxRaw))) {
                try {
                    maxResults = Integer.parseInt(String.valueOf(maxRaw).trim());
                } catch (NumberFormatException ignored) {
                    // keep default
                }
            }

            List<McpToolInfo> found = catalog.search(query, maxResults);
            List<McpToolInfo> activated = catalog.activate(found.stream().map(McpToolInfo::getName).toList());
            attachToCollections(activated);

            List<Map<String, Object>> matchRows = new ArrayList<>();
            for (McpToolInfo tool : found) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", tool.getName());
                row.put("original_name", tool.getOriginalName());
                row.put("server", tool.getServerKey());
                row.put("description", tool.getDesc());
                row.put("parameters", tool.getParameters());
                row.put("activated", true);
                matchRows.add(row);
            }

            int deferredLeft = 0;
            for (McpToolInfo tool : catalog.listAll()) {
                if (!catalog.isActivated(tool.getName())) {
                    deferredLeft++;
                }
            }

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("matches", matchRows);
            fields.put("query", query);
            fields.put("activated_count", activated.size());
            fields.put("total_catalog_tools", catalog.size());
            fields.put("remaining_deferred_tools", deferredLeft);
            fields.put("message", matchRows.isEmpty()
                    ? "No matching deferred tools"
                    : "Activated " + activated.size() + " tool(s); call them by name on the next turn");
            return ToolResultPayload.okData(McpToolNames.TOOL_SEARCH, fields);
        } catch (Exception e) {
            log.warn("ToolSearch failed", e);
            String msg = "ToolSearch 失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            return ToolResultPayload.failureFrom(msg, null);
        }
    }

    private void attachToCollections(List<McpToolInfo> tools) {
        if (tools == null || tools.isEmpty() || agentContext == null) {
            return;
        }
        attachOne(agentContext.getToolCollection(), tools);
        attachOne(agentContext.getSubAgentToolCollection(), tools);
    }

    private static void attachOne(ToolCollection collection, List<McpToolInfo> tools) {
        if (collection == null) {
            return;
        }
        for (McpToolInfo tool : tools) {
            collection.addMcpTool(tool);
        }
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
            Map<String, Object> single = new LinkedHashMap<>();
            single.put("query", str);
            return single;
        }
        return Collections.emptyMap();
    }
}
