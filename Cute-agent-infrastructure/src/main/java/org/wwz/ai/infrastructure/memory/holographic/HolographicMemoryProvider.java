package org.wwz.ai.infrastructure.memory.holographic;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.memory.ltm.LtmOwner;
import org.wwz.ai.domain.agent.memory.ltm.MemoryProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 轻量本地深度记忆 Provider（可离线事实库 + 关键词召回）。
 * 非完整 HRR 实现；后续可替换为 SQLite/HRR 增强版。
 */
public class HolographicMemoryProvider implements MemoryProvider {

    private final Map<String, List<String>> factsByOwner = new ConcurrentHashMap<>();
    private volatile String sessionId = "";
    private volatile LtmOwner owner;

    @Override
    public String name() {
        return "holographic";
    }

    @Override
    public boolean isExternal() {
        return true;
    }

    @Override
    public void initialize(String sessionId, LtmOwner owner, Map<String, Object> context) {
        this.sessionId = sessionId == null ? "" : sessionId;
        this.owner = owner;
    }

    @Override
    public String systemPromptBlock() {
        return "## Holographic Memory\nLocal deep-memory provider active. Use fact_store tools or rely on prefetch.";
    }

    @Override
    public String prefetch(String query, String sessionId) {
        if (owner == null || StringUtils.isBlank(query)) {
            return "";
        }
        String q = query.toLowerCase(Locale.ROOT);
        List<String> facts = factsByOwner.getOrDefault(ownerKey(), List.of());
        List<String> hits = new ArrayList<>();
        for (String fact : facts) {
            if (fact != null && fact.toLowerCase(Locale.ROOT).contains(q) && hits.size() < 5) {
                hits.add("- " + fact);
            }
        }
        if (hits.isEmpty() && !facts.isEmpty()) {
            // 无精确命中时返回最近 3 条
            int from = Math.max(0, facts.size() - 3);
            for (int i = from; i < facts.size(); i++) {
                hits.add("- " + facts.get(i));
            }
        }
        if (hits.isEmpty()) {
            return "";
        }
        return "## Holographic Memory\n" + String.join("\n", hits);
    }

    @Override
    public void syncTurn(String userContent, String assistantContent, String sessionId, List<Map<String, Object>> messages) {
        // best-effort：从用户句中抽取极简「我喜欢/偏好」类短事实
        if (owner == null || StringUtils.isBlank(userContent)) {
            return;
        }
        String text = userContent.trim();
        if (text.length() > 12 && text.length() < 200
                && (text.contains("喜欢") || text.contains("偏好") || text.toLowerCase(Locale.ROOT).contains("prefer"))) {
            remember(text);
        }
    }

    @Override
    public void onMemoryWrite(String action, String target, String content, Map<String, Object> metadata) {
        if ("add".equalsIgnoreCase(action) || "replace".equalsIgnoreCase(action)) {
            if (StringUtils.isNotBlank(content)) {
                remember(content.trim());
            }
        }
    }

    @Override
    public void onSessionSwitch(String newSessionId, String parentSessionId, boolean reset, boolean rewound) {
        this.sessionId = newSessionId == null ? "" : newSessionId;
        // 用户级事实不清空；reset 仅清会话绑定
    }

    @Override
    public void onSessionEnd(List<Map<String, Object>> messages) {
        // no-op for lightweight provider
    }

    @Override
    public List<Map<String, Object>> getToolSchemas() {
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("type", "string");
        action.put("enum", List.of("add", "list", "search"));
        action.put("description", "add | list | search");
        props.put("action", action);
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("type", "string");
        content.put("description", "Fact content for add");
        props.put("content", content);
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("type", "string");
        query.put("description", "Search query");
        props.put("query", query);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", props);
        params.put("required", List.of("action"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("name", "fact_store");
        schema.put("description", "Holographic local fact store: add/list/search deep memory facts");
        schema.put("parameters", params);
        return List.of(schema);
    }

    @Override
    public String handleToolCall(String toolName, Map<String, Object> args) {
        if (!"fact_store".equals(toolName)) {
            return "{\"success\":false,\"message\":\"unknown tool\"}";
        }
        String action = String.valueOf(args.getOrDefault("action", "")).toLowerCase(Locale.ROOT);
        return switch (action) {
            case "add" -> {
                String content = String.valueOf(args.getOrDefault("content", "")).trim();
                if (content.isEmpty()) {
                    yield "{\"success\":false,\"message\":\"content required\"}";
                }
                remember(content);
                yield "{\"success\":true,\"message\":\"fact added\"}";
            }
            case "list" -> {
                List<String> facts = factsByOwner.getOrDefault(ownerKey(), List.of());
                yield "{\"success\":true,\"facts\":" + toJsonArray(facts) + "}";
            }
            case "search" -> {
                String q = String.valueOf(args.getOrDefault("query", "")).trim();
                yield "{\"success\":true,\"result\":" + jsonString(prefetch(q, sessionId)) + "}";
            }
            default -> "{\"success\":false,\"message\":\"unknown action\"}";
        };
    }

    private void remember(String content) {
        if (owner == null) {
            return;
        }
        factsByOwner.computeIfAbsent(ownerKey(), k -> new CopyOnWriteArrayList<>());
        List<String> list = factsByOwner.get(ownerKey());
        if (!list.contains(content)) {
            list.add(content);
        }
    }

    private String ownerKey() {
        return owner == null ? "anon" : owner.getType().name() + "|" + owner.getId();
    }

    private static String toJsonArray(List<String> facts) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < facts.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(jsonString(facts.get(i)));
        }
        sb.append(']');
        return sb.toString();
    }

    private static String jsonString(String s) {
        if (s == null) {
            return "\"\"";
        }
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
