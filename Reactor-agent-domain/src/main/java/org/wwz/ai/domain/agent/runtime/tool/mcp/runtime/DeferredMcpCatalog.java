package org.wwz.ai.domain.agent.runtime.tool.mcp.runtime;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.dto.tool.McpToolInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本 run 的 MCP 工具目录：全量元数据 vs 已激活集合。
 * 延迟加载时 LLM tools[] 只含已激活（及 alwaysLoad）工具。
 */
public class DeferredMcpCatalog {

    private final Map<String, McpToolInfo> allByName = new LinkedHashMap<>();
    private final Set<String> activated = ConcurrentHashMap.newKeySet();

    public DeferredMcpCatalog(Collection<McpToolInfo> tools) {
        if (tools == null) {
            return;
        }
        for (McpToolInfo tool : tools) {
            if (tool == null || StringUtils.isBlank(tool.getName())) {
                continue;
            }
            allByName.put(tool.getName(), tool);
            if (Boolean.TRUE.equals(tool.getAlwaysLoad())) {
                activated.add(tool.getName());
            }
        }
    }

    public List<McpToolInfo> listAll() {
        return List.copyOf(allByName.values());
    }

    public int size() {
        return allByName.size();
    }

    public McpToolInfo get(String name) {
        return allByName.get(name);
    }

    public boolean isActivated(String name) {
        return activated.contains(name);
    }

    public Set<String> activatedNames() {
        return Set.copyOf(activated);
    }

    /**
     * 未激活（仍需 ToolSearch）的工具名，字典序稳定。
     */
    public List<String> listDeferredNames() {
        List<String> names = new ArrayList<>();
        for (String name : allByName.keySet()) {
            if (!activated.contains(name)) {
                names.add(name);
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    /**
     * 仅暴露 deferred 工具名，不带 schema。
     * 无 deferred 时返回空串，避免污染 system。
     */
    public String formatAvailableDeferredToolsBlock() {
        List<String> names = listDeferredNames();
        if (names.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(Math.min(256 + names.size() * 40, 16_384));
        sb.append("Deferred MCP tools are listed by name only (no parameter schema). ");
        sb.append("Call ToolSearch with select:<name> or keywords to load full schema before invoking.\n");
        sb.append("<available-deferred-tools>\n");
        for (String name : names) {
            sb.append(name).append('\n');
        }
        sb.append("</available-deferred-tools>");
        return sb.toString();
    }

    /**
     * 供 SessionPromptFreeze 纳入 toolSig，避免 catalog 变化时复用旧 system。
     */
    public String deferredNamesSignature() {
        return String.join(",", listDeferredNames());
    }

    /**
     * 标记激活；返回本次新激活的工具（已存在则跳过）。
     */
    public List<McpToolInfo> activate(Collection<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        List<McpToolInfo> newly = new ArrayList<>();
        for (String name : names) {
            if (StringUtils.isBlank(name)) {
                continue;
            }
            McpToolInfo tool = allByName.get(name);
            if (tool == null) {
                continue;
            }
            if (activated.add(name)) {
                newly.add(tool);
            }
        }
        return newly;
    }

    public List<McpToolInfo> listActivated() {
        List<McpToolInfo> result = new ArrayList<>();
        for (String name : activated) {
            McpToolInfo tool = allByName.get(name);
            if (tool != null) {
                result.add(tool);
            }
        }
        return result;
    }

    /**
     * 关键词 / select:A,B 搜索 deferred（未激活）工具。
     */
    public List<McpToolInfo> search(String query, int maxResults) {
        int limit = maxResults <= 0 ? 5 : Math.min(maxResults, 50);
        if (allByName.isEmpty()) {
            return List.of();
        }
        String q = StringUtils.defaultString(query).trim();
        if (q.isEmpty()) {
            return listDeferredPreview(limit);
        }

        if (q.regionMatches(true, 0, "select:", 0, "select:".length())) {
            return selectExact(q.substring("select:".length()), limit);
        }

        List<String> required = new ArrayList<>();
        List<String> terms = new ArrayList<>();
        for (String token : q.split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            if (token.startsWith("+") && token.length() > 1) {
                required.add(token.substring(1).toLowerCase(Locale.ROOT));
            } else {
                terms.add(token.toLowerCase(Locale.ROOT));
            }
        }
        if (terms.isEmpty() && required.isEmpty()) {
            return listDeferredPreview(limit);
        }

        List<Scored> scored = new ArrayList<>();
        for (McpToolInfo tool : allByName.values()) {
            if (activated.contains(tool.getName())) {
                continue;
            }
            String hay = buildSearchText(tool);
            boolean missRequired = false;
            for (String req : required) {
                if (!hay.contains(req)) {
                    missRequired = true;
                    break;
                }
            }
            if (missRequired) {
                continue;
            }
            int score = 0;
            String nameLower = tool.getName().toLowerCase(Locale.ROOT);
            for (String term : terms) {
                if (nameLower.equals(term)) {
                    score += 100;
                } else if (nameLower.contains(term)) {
                    score += 40;
                } else if (hay.contains(term)) {
                    score += 10;
                }
            }
            if (terms.isEmpty() && !required.isEmpty()) {
                score = 1;
            }
            if (score > 0) {
                scored.add(new Scored(tool, score));
            }
        }
        scored.sort((a, b) -> Integer.compare(b.score, a.score));
        List<McpToolInfo> matches = new ArrayList<>();
        for (int i = 0; i < scored.size() && matches.size() < limit; i++) {
            matches.add(scored.get(i).tool);
        }
        return matches;
    }

    private List<McpToolInfo> selectExact(String rawList, int limit) {
        List<McpToolInfo> matches = new ArrayList<>();
        for (String part : rawList.split("[,\\s]+")) {
            if (StringUtils.isBlank(part) || matches.size() >= limit) {
                continue;
            }
            String name = part.trim();
            McpToolInfo tool = allByName.get(name);
            if (tool == null) {
                for (McpToolInfo candidate : allByName.values()) {
                    if (name.equalsIgnoreCase(candidate.getName())
                            || name.equalsIgnoreCase(candidate.getOriginalName())) {
                        tool = candidate;
                        break;
                    }
                }
            }
            if (tool != null && !matches.contains(tool)) {
                matches.add(tool);
            }
        }
        return matches;
    }

    private List<McpToolInfo> listDeferredPreview(int limit) {
        List<McpToolInfo> result = new ArrayList<>();
        for (McpToolInfo tool : allByName.values()) {
            if (activated.contains(tool.getName())) {
                continue;
            }
            result.add(tool);
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private static String buildSearchText(McpToolInfo tool) {
        StringBuilder sb = new StringBuilder();
        sb.append(StringUtils.defaultString(tool.getName())).append(' ');
        sb.append(StringUtils.defaultString(tool.getOriginalName())).append(' ');
        sb.append(StringUtils.defaultString(tool.getDesc())).append(' ');
        sb.append(StringUtils.defaultString(tool.getSearchHint())).append(' ');
        sb.append(StringUtils.defaultString(tool.getServerKey())).append(' ');
        sb.append(StringUtils.defaultString(tool.getMcpId()));
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private record Scored(McpToolInfo tool, int score) {
    }

    public Map<String, McpToolInfo> asUnmodifiableMap() {
        return Collections.unmodifiableMap(allByName);
    }
}
