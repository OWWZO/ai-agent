package org.wwz.ai.domain.agent.runtime.tool.canvas;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.tool.common.canvas.CanvasToolNames;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recover truncated / malformed emit_ui_tree tool-call JSON.
 */
public final class EmitUiTreeArgSalvage {

    private static final Pattern TREE_OBJECT_PATTERN = Pattern.compile(
            "\"tree\"\\s*:\\s*(\\{.*)", Pattern.DOTALL);

    private EmitUiTreeArgSalvage() {
    }

    public static boolean isEmitUiTree(String toolName) {
        return CanvasToolNames.EMIT_UI_TREE.equals(toolName);
    }

    public static Map<String, Object> parseOrSalvage(String rawArguments) {
        String raw = StringUtils.defaultString(rawArguments).trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            Object parsed = JSON.parse(raw);
            if (parsed instanceof JSONObject obj) {
                return toMap(obj);
            }
        } catch (Exception ignore) {
            // fall through
        }
        return salvage(raw);
    }

    public static Map<String, Object> salvage(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        // Try closing truncated braces/brackets.
        String closed = closeTruncatedJsonObject(raw);
        if (closed != null) {
            try {
                Object parsed = JSON.parse(closed);
                if (parsed instanceof JSONObject obj) {
                    Map<String, Object> map = toMap(obj);
                    if (map.containsKey("tree") || map.containsKey("root") || map.containsKey("kind")) {
                        map.put("__salvaged", Boolean.TRUE);
                        return normalizeTreeArg(map);
                    }
                }
            } catch (Exception ignore) {
                // continue
            }
        }

        Matcher m = TREE_OBJECT_PATTERN.matcher(raw);
        if (m.find()) {
            String treeRaw = m.group(1);
            String closedTree = closeTruncatedJsonObject(treeRaw);
            if (closedTree != null) {
                try {
                    Object tree = JSON.parse(closedTree);
                    if (tree instanceof Map<?, ?> || tree instanceof JSONObject) {
                        Map<String, Object> out = new LinkedHashMap<>();
                        out.put("tree", tree instanceof JSONObject jo ? toMap(jo) : tree);
                        out.put("__salvaged", Boolean.TRUE);
                        return out;
                    }
                } catch (Exception ignore) {
                    // fall through
                }
            }
        }

        // Bare root truncated object
        if (raw.contains("\"kind\"") || raw.contains("\"root\"")) {
            String closedBare = closeTruncatedJsonObject(raw.startsWith("{") ? raw : "{" + raw);
            if (closedBare != null) {
                try {
                    Object parsed = JSON.parse(closedBare);
                    if (parsed instanceof JSONObject obj) {
                        Map<String, Object> map = toMap(obj);
                        map.put("__salvaged", Boolean.TRUE);
                        return normalizeTreeArg(map);
                    }
                } catch (Exception ignore) {
                    // give up
                }
            }
        }
        return null;
    }

    private static Map<String, Object> normalizeTreeArg(Map<String, Object> map) {
        if (map.containsKey("tree")) {
            return map;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tree", map);
        if (map.containsKey("__salvaged")) {
            out.put("__salvaged", map.get("__salvaged"));
        }
        return out;
    }

    private static Map<String, Object> toMap(JSONObject obj) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : obj.keySet()) {
            out.put(key, obj.get(key));
        }
        return out;
    }

    /**
     * Close truncated JSON object by balancing braces/brackets and closing open string.
     */
    static String closeTruncatedJsonObject(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        String s = text.trim();
        if (!s.startsWith("{") && !s.startsWith("[")) {
            return null;
        }
        StringBuilder sb = new StringBuilder(s);
        boolean inString = false;
        boolean escaped = false;
        int braces = 0;
        int brackets = 0;
        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                braces++;
            } else if (c == '}') {
                braces--;
            } else if (c == '[') {
                brackets++;
            } else if (c == ']') {
                brackets--;
            }
        }
        if (inString) {
            sb.append('"');
        }
        while (brackets > 0) {
            sb.append(']');
            brackets--;
        }
        while (braces > 0) {
            sb.append('}');
            braces--;
        }
        return sb.toString();
    }
}
