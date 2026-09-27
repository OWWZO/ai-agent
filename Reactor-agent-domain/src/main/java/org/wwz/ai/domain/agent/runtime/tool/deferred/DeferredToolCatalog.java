package org.wwz.ai.domain.agent.runtime.tool.deferred;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.dto.tool.McpToolInfo;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.util.ToolSchemaNormalizer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 一次 ToolCollection 内不可变的延迟工具目录。
 *
 * <p>本地 BaseTool 与 MCP 工具在这里使用同一套搜索、listing 和来源语义。
 * 搜索不会改变 eager tool map，也不会维护 activated 状态。</p>
 */
public final class DeferredToolCatalog {

    private static final Pattern SENTENCE_END = Pattern.compile("[.!?\\n]");
    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 50;

    private final Map<String, DeferredToolEntry> allByName;
    private final Map<String, List<String>> tokensByName;

    public DeferredToolCatalog(Collection<DeferredToolEntry> entries) {
        Map<String, DeferredToolEntry> byName = new LinkedHashMap<>();
        Map<String, List<String>> tokens = new LinkedHashMap<>();
        if (entries != null) {
            for (DeferredToolEntry entry : entries) {
                if (entry == null || StringUtils.isBlank(entry.getName())) {
                    continue;
                }
                byName.put(entry.getName(), entry);
                tokens.put(entry.getName(), DeferredToolBm25.tokenize(buildSearchText(entry)));
            }
        }
        this.allByName = Collections.unmodifiableMap(byName);
        this.tokensByName = Collections.unmodifiableMap(tokens);
    }

    public static DeferredToolCatalog empty() {
        return new DeferredToolCatalog(List.of());
    }

    public static DeferredToolEntry localEntry(BaseTool tool, String sourceName) {
        return DeferredToolEntry.local(
                tool,
                sourceName,
                ToolSchemaNormalizer.normalizeSchema(tool.toParams(), tool.getName()),
                "");
    }

    public static DeferredToolEntry mcpEntry(McpToolInfo toolInfo) {
        Map<String, Object> parameters = ToolSchemaNormalizer.normalizeSchemaAsMap(
                toolInfo.getParameters(), toolInfo.getName());
        String sourceName = StringUtils.defaultIfBlank(toolInfo.getServerKey(), toolInfo.getMcpId());
        return DeferredToolEntry.mcp(toolInfo, sourceName, parameters, toolInfo.getSearchHint());
    }

    public int size() {
        return allByName.size();
    }

    public DeferredToolEntry get(String name) {
        return allByName.get(name);
    }

    public boolean contains(String name) {
        return name != null && allByName.containsKey(name);
    }

    public List<DeferredToolEntry> listAll() {
        return List.copyOf(allByName.values());
    }

    public List<DeferredToolEntry> search(String query, int maxResults) {
        int limit = maxResults <= 0 ? DEFAULT_LIMIT : Math.min(maxResults, MAX_LIMIT);
        if (allByName.isEmpty()) {
            return List.of();
        }
        String q = StringUtils.defaultString(query).trim();
        if (q.isEmpty()) {
            return List.of();
        }
        if (q.regionMatches(true, 0, "select:", 0, "select:".length())) {
            return selectExact(q.substring("select:".length()), limit);
        }
        List<String> queryTokens = DeferredToolBm25.tokenize(q);
        if (queryTokens.isEmpty()) {
            return substringFallback(q, limit);
        }
        List<List<String>> docs = new ArrayList<>(tokensByName.values());
        Map<String, Integer> df = DeferredToolBm25.documentFrequency(docs);
        double avgDl = DeferredToolBm25.averageLength(docs);
        int nDocs = docs.size();
        List<Scored> scored = new ArrayList<>();
        for (DeferredToolEntry entry : allByName.values()) {
            List<String> docTokens = tokensByName.getOrDefault(entry.getName(), List.of());
            double score = DeferredToolBm25.score(queryTokens, docTokens, avgDl, df, nDocs);
            if (score > 0) {
                scored.add(new Scored(entry, score));
            }
        }
        if (scored.isEmpty()) {
            return substringFallback(q, limit);
        }
        scored.sort((a, b) -> {
            int scoreCompare = Double.compare(b.score, a.score);
            return scoreCompare != 0
                    ? scoreCompare
                    : String.CASE_INSENSITIVE_ORDER.compare(a.entry.getName(), b.entry.getName());
        });
        List<DeferredToolEntry> matches = new ArrayList<>();
        for (int i = 0; i < scored.size() && matches.size() < limit; i++) {
            matches.add(scored.get(i).entry);
        }
        return matches;
    }

    public List<Map<String, Object>> availableSources() {
        Map<String, Integer> counts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (DeferredToolEntry entry : allByName.values()) {
            counts.merge(entry.getSourceName(), 1, Integer::sum);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, Integer> source : counts.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", source.getKey());
            row.put("tool_count", source.getValue());
            rows.add(row);
        }
        return rows;
    }

    public String formatListingForToolSearchDescription() {
        if (allByName.isEmpty()) {
            return "";
        }
        Map<String, List<DeferredToolEntry>> groups = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (DeferredToolEntry entry : allByName.values()) {
            groups.computeIfAbsent(sourceLabel(entry), key -> new ArrayList<>()).add(entry);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Deferred tool catalog (call schemas via `ToolDescribe`, invoke via `ToolCall`):");
        for (Map.Entry<String, List<DeferredToolEntry>> group : groups.entrySet()) {
            List<DeferredToolEntry> entries = group.getValue();
            entries.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.getName(), b.getName()));
            sb.append('\n').append(group.getKey()).append(" tools (").append(entries.size()).append("):");
            for (DeferredToolEntry entry : entries) {
                String shortDesc = shortDesc(entry.getDescription());
                sb.append("\n- ").append(entry.getName());
                if (StringUtils.isNotBlank(shortDesc)) {
                    sb.append(": ").append(shortDesc);
                }
            }
        }
        return sb.toString();
    }

    public DeferredToolCatalog filter(Predicate<DeferredToolEntry> keep) {
        if (keep == null || allByName.isEmpty()) {
            return this;
        }
        List<DeferredToolEntry> kept = new ArrayList<>();
        for (DeferredToolEntry entry : allByName.values()) {
            if (keep.test(entry)) {
                kept.add(entry);
            }
        }
        if (kept.size() == allByName.size()) {
            return this;
        }
        return new DeferredToolCatalog(kept);
    }

    private List<DeferredToolEntry> selectExact(String rawList, int limit) {
        List<DeferredToolEntry> matches = new ArrayList<>();
        for (String part : rawList.split("[,\\s]+")) {
            if (StringUtils.isBlank(part) || matches.size() >= limit) {
                continue;
            }
            String name = part.trim();
            DeferredToolEntry entry = allByName.get(name);
            if (entry == null) {
                for (DeferredToolEntry candidate : allByName.values()) {
                    if (name.equalsIgnoreCase(candidate.getName())) {
                        entry = candidate;
                        break;
                    }
                }
            }
            if (entry != null && !matches.contains(entry)) {
                matches.add(entry);
            }
        }
        return matches;
    }

    private List<DeferredToolEntry> substringFallback(String query, int limit) {
        String needle = query.toLowerCase(Locale.ROOT);
        List<DeferredToolEntry> matches = new ArrayList<>();
        for (DeferredToolEntry entry : allByName.values()) {
            if (entry.getName().toLowerCase(Locale.ROOT).contains(needle)) {
                matches.add(entry);
                if (matches.size() >= limit) {
                    break;
                }
            }
        }
        return matches;
    }

    private static String buildSearchText(DeferredToolEntry entry) {
        StringBuilder sb = new StringBuilder();
        sb.append(splitName(entry.getName())).append(' ');
        sb.append(StringUtils.defaultString(entry.getDescription())).append(' ');
        sb.append(StringUtils.defaultString(entry.getSearchHint())).append(' ');
        sb.append(topLevelParamNames(entry.getParameters()));
        return sb.toString();
    }

    private static String splitName(String name) {
        return StringUtils.defaultString(name).replace("__", " ").replace('_', ' ')
                .replace('.', ' ').replace('-', ' ').replace(':', ' ');
    }

    private static String topLevelParamNames(Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "";
        }
        Object properties = parameters.get("properties");
        if (!(properties instanceof Map<?, ?> map) || map.isEmpty()) {
            return "";
        }
        return String.join(" ", map.keySet().stream().map(String::valueOf).toList());
    }

    private static String sourceLabel(DeferredToolEntry entry) {
        if (entry.getSource() == DeferredToolSource.LOCAL) {
            return "reactor local";
        }
        return StringUtils.defaultIfBlank(entry.getSourceName(), "other") + " MCP";
    }

    static String shortDesc(String description) {
        String text = String.join(" ", StringUtils.defaultString(description).split("\\s+")).trim();
        if (text.isEmpty()) {
            return "";
        }
        Matcher matcher = SENTENCE_END.matcher(text);
        if (matcher.find()) {
            int end = matcher.start();
            text = text.substring(0, end + (text.charAt(end) == '.' ? 1 : 0));
        }
        if (text.length() <= 60) {
            return text;
        }
        String clipped = text.substring(0, 60);
        int lastSpace = clipped.lastIndexOf(' ');
        if (lastSpace > 0) {
            clipped = clipped.substring(0, lastSpace);
        }
        return clipped.replaceAll("[,;: ]+$", "") + "...";
    }

    private record Scored(DeferredToolEntry entry, double score) {
    }
}
