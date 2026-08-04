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
 * 在 {@code canvas_publish} 执行前抢救被截断或格式损坏的工具参数。
 *
 * <p>正常输入优先走 Jackson 的结构化解析；只有解析失败时才提取关键字段。
 * 抢救路径允许 HTML 字符串未闭合，但必须至少得到 html 或 html_path，避免把
 * 不完整的无效参数伪装成成功调用。</p>
 */
public final class CanvasPublishArgSalvage {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true)
            .configure(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true);

    private static final Pattern TITLE_PATTERN = Pattern.compile(
            "\"title\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"", Pattern.DOTALL);
    private static final Pattern MODE_PATTERN = Pattern.compile(
            "\"mode\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern HTML_PATH_PATTERN = Pattern.compile(
            "\"html_path\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern FILENAME_PATTERN = Pattern.compile(
            "\"filename\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern HTML_KEY_PATTERN = Pattern.compile("\"html\"\\s*:\\s*\"");

    private CanvasPublishArgSalvage() {
    }

    public static boolean isCanvasPublish(String toolName) {
        return CanvasToolNames.CANVAS_PUBLISH.equals(toolName);
    }

    /**
     * Parse args; on failure try salvage. Returns null only when nothing usable.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseOrSalvage(String rawArguments) {
        String raw = StringUtils.defaultString(rawArguments).trim();
        if (raw.isEmpty()) {
            return null;
        }
        // 完整 JSON 保留原始字段，抢救逻辑只作为损坏输入的降级路径。
        try {
            Object parsed = MAPPER.readValue(raw, Object.class);
            if (parsed instanceof Map<?, ?> map) {
                Map<String, Object> out = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    if (e.getKey() != null) {
                        out.put(String.valueOf(e.getKey()), e.getValue());
                    }
                }
                return out;
            }
        } catch (Exception ignore) {
            // fall through to salvage
        }
        return salvage(raw);
    }

    public static Map<String, Object> salvage(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        // 标量字段用正则提取，HTML 单独按转义状态扫描，避免内容中的引号误截断。
        String title = extractQuoted(TITLE_PATTERN, raw);
        String mode = extractQuoted(MODE_PATTERN, raw);
        String htmlPath = extractQuoted(HTML_PATH_PATTERN, raw);
        String filename = extractQuoted(FILENAME_PATTERN, raw);
        String html = extractHtmlValue(raw);

        if (StringUtils.isNotBlank(title)) {
            out.put("title", unescapeJsonString(title));
        }
        if (StringUtils.isNotBlank(mode)) {
            out.put("mode", unescapeJsonString(mode));
        } else {
            out.put("mode", "html");
        }
        if (StringUtils.isNotBlank(htmlPath)) {
            out.put("html_path", unescapeJsonString(htmlPath));
        }
        if (StringUtils.isNotBlank(filename)) {
            out.put("filename", unescapeJsonString(filename));
        }
        if (StringUtils.isNotBlank(html)) {
            out.put("html", html);
        }
        // 没有可发布的内容时返回 null，让上层按普通参数错误处理。
        if (out.isEmpty() || (!out.containsKey("html") && !out.containsKey("html_path"))) {
            return null;
        }
        if (!out.containsKey("title")) {
            out.put("title", "Canvas");
        }
        out.put("__salvaged", Boolean.TRUE);
        return out;
    }

    private static String extractQuoted(Pattern pattern, String raw) {
        Matcher m = pattern.matcher(raw);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    /**
     * Extract html string value even when the outer JSON was truncated mid-string.
     */
    private static String extractHtmlValue(String raw) {
        Matcher m = HTML_KEY_PATTERN.matcher(raw);
        if (!m.find()) {
            return null;
        }
        int i = m.end();
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        // 允许扫描到输入末尾；工具调用被截断时，末尾没有闭合引号也仍可发布已有片段。
        while (i < raw.length()) {
            char c = raw.charAt(i);
            if (escaped) {
                sb.append(c);
                escaped = false;
                i++;
                continue;
            }
            if (c == '\\') {
                sb.append(c);
                escaped = true;
                i++;
                continue;
            }
            if (c == '"') {
                break;
            }
            sb.append(c);
            i++;
        }
        String html = unescapeJsonString(sb.toString());
        if (StringUtils.isBlank(html)) {
            return null;
        }
        // Truncation may leave unclosed tags; still publish what we have.
        return html;
    }

    private static String unescapeJsonString(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(value.length());
        boolean escaped = false;
        // 这里只处理 JSON 常见转义，未知转义保留其字符，尽量不破坏模型生成的 HTML。
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaped) {
                switch (c) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'u' -> {
                        if (i + 4 < value.length()) {
                            try {
                                int code = Integer.parseInt(value.substring(i + 1, i + 5), 16);
                                sb.append((char) code);
                                i += 4;
                            } catch (NumberFormatException e) {
                                sb.append('u');
                            }
                        } else {
                            sb.append('u');
                        }
                    }
                    default -> sb.append(c);
                }
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
