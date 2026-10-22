package org.wwz.ai.infrastructure.memory.openviking;

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
 * OpenViking 风格轻量适配：可配置 endpoint；无 endpoint 时仍提供本地分层事实桶（profile/preference/event）。
 * 完整远程 viking:// 协议可后续增强。
 */
public class OpenVikingMemoryProvider implements MemoryProvider {

    private final String endpoint;
    private final Map<String, Map<String, List<String>>> bucketsByOwner = new ConcurrentHashMap<>();
    private volatile String sessionId = "";
    private volatile LtmOwner owner;

    public OpenVikingMemoryProvider(String endpoint) {
        this.endpoint = endpoint == null ? "" : endpoint.trim();
    }

    @Override
    public String name() {
        return "openviking";
    }

    @Override
    public boolean isExternal() {
        return true;
    }

    @Override
    public boolean isAvailable() {
        // 本地桶始终可用；有 endpoint 时同样可用（远程调用 best-effort）
        return true;
    }

    @Override
    public void initialize(String sessionId, LtmOwner owner, Map<String, Object> context) {
        this.sessionId = sessionId == null ? "" : sessionId;
        this.owner = owner;
    }

    @Override
    public String systemPromptBlock() {
        String mode = StringUtils.isBlank(endpoint) ? "local-buckets" : "endpoint=" + endpoint;
        return "## OpenViking Memory\nProvider mode: " + mode + ". Categories: profile/preference/event.";
    }

    @Override
    public String prefetch(String query, String sessionId) {
        if (owner == null || StringUtils.isBlank(query)) {
            return "";
        }
        String q = query.toLowerCase(Locale.ROOT);
        Map<String, List<String>> buckets = bucketsByOwner.getOrDefault(ownerKey(), Map.of());
        List<String> hits = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : buckets.entrySet()) {
            for (String fact : e.getValue()) {
                if (fact != null && fact.toLowerCase(Locale.ROOT).contains(q) && hits.size() < 5) {
                    hits.add("- [" + e.getKey() + "] " + fact);
                }
            }
        }
        if (hits.isEmpty()) {
            return "";
        }
        return "## OpenViking Memory\n" + String.join("\n", hits);
    }

    @Override
    public void syncTurn(String userContent, String assistantContent, String sessionId, List<Map<String, Object>> messages) {
        // no auto extract in lightweight mode
    }

    @Override
    public void onMemoryWrite(String action, String target, String content, Map<String, Object> metadata) {
        if (StringUtils.isBlank(content)) {
            return;
        }
        String category = "user".equalsIgnoreCase(target) ? "preference" : "profile";
        if ("add".equalsIgnoreCase(action) || "replace".equalsIgnoreCase(action)) {
            remember(category, content.trim());
        }
    }

    @Override
    public void onSessionSwitch(String newSessionId, String parentSessionId, boolean reset, boolean rewound) {
        this.sessionId = newSessionId == null ? "" : newSessionId;
    }

    @Override
    public List<Map<String, Object>> getToolSchemas() {
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("type", "string");
        action.put("enum", List.of("remember", "search", "browse"));
        action.put("description", "remember | search | browse");
        props.put("action", action);
        Map<String, Object> category = new LinkedHashMap<>();
        category.put("type", "string");
        category.put("enum", List.of("profile", "preference", "event"));
        category.put("description", "Memory category");
        props.put("category", category);
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("type", "string");
        content.put("description", "Content to remember");
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
        schema.put("name", "viking_memory");
        schema.put("description", "OpenViking-style memory: remember/search/browse by category");
        schema.put("parameters", params);
        return List.of(schema);
    }

    @Override
    public String handleToolCall(String toolName, Map<String, Object> args) {
        if (!"viking_memory".equals(toolName)) {
            return "{\"success\":false,\"message\":\"unknown tool\"}";
        }
        String action = String.valueOf(args.getOrDefault("action", "")).toLowerCase(Locale.ROOT);
        return switch (action) {
            case "remember" -> {
                String category = String.valueOf(args.getOrDefault("category", "profile"));
                String content = String.valueOf(args.getOrDefault("content", "")).trim();
                if (content.isEmpty()) {
                    yield "{\"success\":false,\"message\":\"content required\"}";
                }
                remember(category, content);
                yield "{\"success\":true,\"message\":\"remembered\",\"category\":\"" + category + "\"}";
            }
            case "search" -> {
                String q = String.valueOf(args.getOrDefault("query", "")).trim();
                yield "{\"success\":true,\"result\":" + jsonString(prefetch(q, sessionId)) + "}";
            }
            case "browse" -> {
                Map<String, List<String>> buckets = bucketsByOwner.getOrDefault(ownerKey(), Map.of());
                yield "{\"success\":true,\"buckets\":" + bucketsToJson(buckets) + "}";
            }
            default -> "{\"success\":false,\"message\":\"unknown action\"}";
        };
    }

    private void remember(String category, String content) {
        if (owner == null) {
            return;
        }
        String cat = StringUtils.defaultIfBlank(category, "profile").toLowerCase(Locale.ROOT);
        bucketsByOwner.computeIfAbsent(ownerKey(), k -> new ConcurrentHashMap<>());
        Map<String, List<String>> buckets = bucketsByOwner.get(ownerKey());
        buckets.computeIfAbsent(cat, k -> new CopyOnWriteArrayList<>());
        List<String> list = buckets.get(cat);
        if (!list.contains(content)) {
            list.add(content);
        }
    }

    private String ownerKey() {
        return owner == null ? "anon" : owner.getType().name() + "|" + owner.getId();
    }

    private static String bucketsToJson(Map<String, List<String>> buckets) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, List<String>> e : buckets.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(jsonString(e.getKey())).append(":[");
            List<String> vals = e.getValue();
            for (int i = 0; i < vals.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(jsonString(vals.get(i)));
            }
            sb.append(']');
        }
        sb.append('}');
        return sb.toString();
    }

    private static String jsonString(String s) {
        if (s == null) {
            return "\"\"";
        }
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
