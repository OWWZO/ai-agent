package org.wwz.ai.infrastructure.adapter.port;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.adapter.port.cli.CliArtifact;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * OpenCLI 产物候选协议解析。
 * <p>
 * opencli 下载型命令在 JSON 输出里额外报告本次写出的文件，形如：
 * <pre>{@code
 * {
 *   "artifacts": [
 *     { "relativePath": "xiaohongshu-downloads/note-1/note-1_1.jpg", "size": 183421, "mime": "image/jpeg" }
 *   ]
 * }
 * }</pre>
 * 约定：
 * <ul>
 *     <li>{@code relativePath} 相对 CLI 的 cwd（即 Agent workspace），也可写作 {@code path}；</li>
 *     <li>不允许绝对路径，不允许 {@code ..}；</li>
 *     <li>这里只做协议解析，是否落在 workspace 内由 domain 的物化模块校验；</li>
 *     <li>下载 URL 不由 opencli 生成，仍由 Cute 文件服务登记后返回。</li>
 * </ul>
 * 解析失败或字段缺失时返回空列表，绝不影响 CLI 主结果。
 */
public final class CliArtifactCandidateParser {

    /** 单次调用最多接受的候选数，防止异常输出把整个 workspace 变成产物。 */
    public static final int MAX_CANDIDATES = 50;

    private CliArtifactCandidateParser() {
    }

    public static List<CliArtifact> parse(String stdout) {
        if (StringUtils.isBlank(stdout)) {
            return List.of();
        }
        List<CliArtifact> artifacts = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Object payload : extractJsonValues(stdout)) {
            collectArtifacts(payload, artifacts, seen);
            if (artifacts.size() >= MAX_CANDIDATES) {
                break;
            }
        }
        return artifacts;
    }

    private static void collectArtifacts(Object value,
                                         List<CliArtifact> artifacts,
                                         Set<String> seen) {
        if (value instanceof JSONArray array) {
            for (Object item : array) {
                if (artifacts.size() >= MAX_CANDIDATES) {
                    return;
                }
                collectArtifacts(item, artifacts, seen);
            }
            return;
        }
        if (!(value instanceof JSONObject json)) {
            return;
        }
        Object artifactPayload = json.get("artifacts");
        if (artifactPayload != null) {
            collectArtifacts(artifactPayload, artifacts, seen);
        }
        CliArtifact directArtifact = looksLikeArtifact(json) ? toArtifact(json) : null;
        if (directArtifact != null && seen.add(directArtifact.getPath())) {
            artifacts.add(directArtifact);
        }
        for (String key : json.keySet()) {
            if ("artifacts".equals(key)) {
                continue;
            }
            Object nested = json.get(key);
            if (nested instanceof JSONObject || nested instanceof JSONArray) {
                collectArtifacts(nested, artifacts, seen);
                if (artifacts.size() >= MAX_CANDIDATES) {
                    return;
                }
            }
        }
    }

    private static CliArtifact toArtifact(Object item) {
        if (!(item instanceof JSONObject json)) {
            return null;
        }
        String path = firstText(json, "relativePath", "relative_path", "path");
        String normalizedPath = normalizeRelativePath(path);
        if (normalizedPath == null) {
            // 绝对路径和 .. 逃逸在协议层就直接丢弃，不交给下游登记
            return null;
        }
        Long size = null;
        Object rawSize = json.get("size");
        if (rawSize instanceof Number number) {
            size = number.longValue();
        } else if (rawSize instanceof String text && StringUtils.isNumeric(text)) {
            size = Long.parseLong(text);
        }
        String mime = firstText(json, "mime", "mimeType", "contentType");
        return CliArtifact.builder()
                .path(normalizedPath)
                .size(size)
                .mime(mime == null ? null : mime.toLowerCase(Locale.ROOT))
                .build();
    }

    private static String firstText(JSONObject json, String... keys) {
        for (String key : keys) {
            Object value = json.get(key);
            if (value instanceof String text && StringUtils.isNotBlank(text)) {
                return text.trim();
            }
        }
        return null;
    }

    private static List<Object> extractJsonValues(String stdout) {
        List<Object> values = new ArrayList<>();
        String trimmed = stdout.trim();
        for (int start = 0; start < trimmed.length(); start++) {
            char first = trimmed.charAt(start);
            if (first != '{' && first != '[') {
                continue;
            }
            int end = findJsonEnd(trimmed, start);
            if (end < 0) {
                continue;
            }
            try {
                Object parsed = JSON.parse(trimmed.substring(start, end + 1));
                if (parsed instanceof JSONObject || parsed instanceof JSONArray) {
                    values.add(parsed);
                }
            } catch (RuntimeException ignore) {
                // The next JSON-looking span may still contain the actual payload.
            }
        }
        return values;
    }

    private static int findJsonEnd(String text, int start) {
        List<Character> stack = new ArrayList<>();
        boolean quoted = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char current = text.charAt(i);
            if (quoted) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    quoted = false;
                }
                continue;
            }
            if (current == '"') {
                quoted = true;
                continue;
            }
            if (current == '{' || current == '[') {
                stack.add(current);
                continue;
            }
            if (current != '}' && current != ']') {
                continue;
            }
            if (stack.isEmpty() || !matches(stack.get(stack.size() - 1), current)) {
                return -1;
            }
            stack.remove(stack.size() - 1);
            if (stack.isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private static boolean matches(char open, char close) {
        return (open == '{' && close == '}') || (open == '[' && close == ']');
    }

    private static String normalizeRelativePath(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String normalized = raw.trim().replace('\\', '/');
        if (normalized.startsWith("/") || normalized.startsWith("//")
                || normalized.matches("^[A-Za-z]:.*")
                || normalized.contains("://")
                || normalized.matches("^[A-Za-z][A-Za-z0-9+.-]*:.*")) {
            return null;
        }
        StringBuilder result = new StringBuilder();
        for (String segment : normalized.split("/")) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                return null;
            }
            if (result.length() > 0) {
                result.append('/');
            }
            result.append(segment);
        }
        return result.length() == 0 ? null : result.toString();
    }

    private static boolean looksLikeArtifact(JSONObject json) {
        return json.containsKey("relativePath")
                || json.containsKey("relative_path")
                || (json.containsKey("path")
                && (json.containsKey("size")
                || json.containsKey("mime")
                || json.containsKey("mimeType")
                || json.containsKey("contentType")));
    }
}
