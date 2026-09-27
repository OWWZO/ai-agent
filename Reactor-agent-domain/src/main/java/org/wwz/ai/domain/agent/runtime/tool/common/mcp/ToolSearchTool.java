package org.wwz.ai.domain.agent.runtime.tool.common.mcp;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.deferred.DeferredToolCatalog;
import org.wwz.ai.domain.agent.runtime.tool.deferred.DeferredToolEntry;
import org.wwz.ai.domain.agent.runtime.tool.mcp.runtime.DeferredToolCall;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 搜索延迟本地/MCP 工具目录。不激活、不改 tools[]。
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
        DeferredToolCatalog catalog = resolveCatalog();
        int total = catalog == null ? 0 : catalog.size();
        StringBuilder desc = new StringBuilder();
        desc.append("Search ").append(total)
                .append(" additional local/MCP tools that are loaded on demand. ")
                .append("Returns name/source/description only (no parameter schema). ")
                .append("If you already see the exact name below, skip ToolSearch and call ToolDescribe. ")
                .append("Invoke deferred tools only via ToolCall. ")
                .append("query is keywords or select:name1,name2. max_results default 5.");
        if (catalog != null && catalog.size() > 0) {
            String listing = catalog.formatListingForToolSearchDescription();
            if (StringUtils.isNotBlank(listing)) {
                desc.append("\n\nEvery deferred capability is listed below. If a tool name appears here, ")
                        .append("do NOT claim it is unavailable — load it with ToolDescribe ")
                        .append("(skip ToolSearch when you already see the exact name).\n\n")
                        .append(listing);
            }
        }
        return desc.toString();
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("type", "string");
        query.put("description", "Keywords describing the capability, or select:name1,name2");
        properties.put("query", query);

        Map<String, Object> maxResults = new LinkedHashMap<>();
        maxResults.put("type", "integer");
        maxResults.put("description", "Maximum number of results to return. Default 5.");
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
            DeferredToolCatalog catalog = resolveCatalog();
            Map<String, Object> args = DeferredToolCall.coerceMap(input);
            String query = String.valueOf(args.getOrDefault("query", "")).trim();
            if ("null".equals(query)) {
                query = "";
            }
            int maxResults = parseMaxResults(args.get("max_results"));
            if (catalog == null || catalog.size() == 0) {
                Map<String, Object> empty = new LinkedHashMap<>();
                empty.put("query", query);
                empty.put("total_available", 0);
                empty.put("matches", List.of());
                empty.put("message", "No deferred local/MCP tools in catalog");
                return ToolResultPayload.okData(McpToolNames.TOOL_SEARCH, empty);
            }

            List<DeferredToolEntry> found = catalog.search(query, maxResults);
            List<Map<String, Object>> matchRows = new ArrayList<>();
            for (DeferredToolEntry tool : found) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", tool.getName());
                row.put("source", tool.isLocal() ? "local" : "mcp");
                row.put("source_name", tool.getSourceName());
                String description = StringUtils.defaultString(tool.getDescription());
                if (description.length() > 400) {
                    description = description.substring(0, 400);
                }
                row.put("description", description);
                matchRows.add(row);
            }

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("query", query);
            fields.put("total_available", catalog.size());
            fields.put("matches", matchRows);
            if (matchRows.isEmpty() && catalog.size() > 0) {
                fields.put("available_sources", catalog.availableSources());
                fields.put("hint", "No lexical match was found, but the sources above are connected "
                        + "and their tools remain available. Retry ToolSearch with the service name "
                        + "plus a concrete action, or ToolDescribe if you already know the exact name.");
            }
            return ToolResultPayload.okData(McpToolNames.TOOL_SEARCH, fields);
        } catch (Exception e) {
            log.warn("ToolSearch failed", e);
            String msg = "ToolSearch 失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            return ToolResultPayload.failureFrom(msg, null);
        }
    }

    private DeferredToolCatalog resolveCatalog() {
        if (agentContext == null || agentContext.getToolCollection() == null) {
            return null;
        }
        return agentContext.getToolCollection().getDeferredToolCatalog();
    }

    private static int parseMaxResults(Object maxRaw) {
        if (maxRaw instanceof Number number) {
            return number.intValue();
        }
        if (maxRaw != null && StringUtils.isNotBlank(String.valueOf(maxRaw))) {
            try {
                return Integer.parseInt(String.valueOf(maxRaw).trim());
            } catch (NumberFormatException ignored) {
                return 5;
            }
        }
        return 5;
    }
}
