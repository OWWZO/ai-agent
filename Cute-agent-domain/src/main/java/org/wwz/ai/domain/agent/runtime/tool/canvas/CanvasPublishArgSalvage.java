package org.wwz.ai.domain.agent.runtime.tool.canvas;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.tool.common.canvas.CanvasToolNames;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 在 {@code canvas_publish} 执行前抢救被截断或格式损坏的 html_path 参数。
 * <p>canvas_publish 不再接受 inline HTML；抢救结果没有完整路径时直接失败。</p>
 */
public final class CanvasPublishArgSalvage {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true)
            .configure(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true);

    private static final Pattern TITLE_PATTERN = Pattern.compile(
            "\"title\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"", Pattern.DOTALL);
    private static final Pattern HTML_PATH_PATTERN = Pattern.compile(
            "\"html_path\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)(?:\"|$)", Pattern.DOTALL);

    private CanvasPublishArgSalvage() {
    }

    public static boolean isCanvasPublish(String toolName) {
        return CanvasToolNames.CANVAS_PUBLISH.equals(toolName);
    }

    /** Parse valid JSON normally; otherwise recover only the supported path fields. */
    public static Map<String, Object> parseOrSalvage(String rawArguments) {
        String raw = StringUtils.defaultString(rawArguments).trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            Object parsed = MAPPER.readValue(raw, Object.class);
            if (parsed instanceof Map<?, ?> map) {
                Map<String, Object> out = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() != null) {
                        out.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
                return out;
            }
        } catch (Exception ignore) {
            // fall through to the path-only salvage parser
        }
        return salvage(raw);
    }

    public static Map<String, Object> salvage(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String htmlPath = extractQuoted(HTML_PATH_PATTERN, raw);
        if (StringUtils.isBlank(htmlPath)) {
            return null;
        }

        Map<String, Object> out = new LinkedHashMap<>();
        String title = extractQuoted(TITLE_PATTERN, raw);
        if (StringUtils.isNotBlank(title)) {
            out.put("title", unescapeJsonString(title));
        } else {
            out.put("title", "Canvas");
        }
        out.put("html_path", unescapeJsonString(htmlPath));
        out.put("__salvaged", Boolean.TRUE);
        return out;
    }

    private static String extractQuoted(Pattern pattern, String raw) {
        Matcher matcher = pattern.matcher(raw);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String unescapeJsonString(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder result = new StringBuilder(value.length());
        boolean escaped = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (escaped) {
                switch (current) {
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case 'b' -> result.append('\b');
                    case 'f' -> result.append('\f');
                    case '"' -> result.append('"');
                    case '\\' -> result.append('\\');
                    case '/' -> result.append('/');
                    case 'u' -> {
                        if (index + 4 < value.length()) {
                            try {
                                result.append((char) Integer.parseInt(value.substring(index + 1, index + 5), 16));
                                index += 4;
                            } catch (NumberFormatException e) {
                                result.append('u');
                            }
                        } else {
                            result.append('u');
                        }
                    }
                    default -> result.append(current);
                }
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else {
                result.append(current);
            }
        }
        if (escaped) {
            result.append('\\');
        }
        return result.toString();
    }
}
