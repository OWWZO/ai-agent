package org.wwz.ai.domain.agent.runtime.tool.mcp.runtime;

import org.apache.commons.lang3.StringUtils;

/**
 * MCP 工具命名策略（对齐 cc-haha：mcp__{server}__{tool}）。
 * 模型侧使用 FQ 名；tools/call 仍使用服务端原始工具名。
 */
public final class McpToolNamePolicy {

    public static final String PREFIX = "mcp__";
    public static final String SEPARATOR = "__";

    private McpToolNamePolicy() {
    }

    /**
     * 规范化 server / tool 片段：非 [a-zA-Z0-9_-] 替换为下划线，截断到 64。
     */
    public static String normalizeSegment(String raw) {
        if (StringUtils.isBlank(raw)) {
            return "unknown";
        }
        String normalized = raw.trim().replaceAll("[^a-zA-Z0-9_-]", "_");
        if (normalized.isEmpty()) {
            return "unknown";
        }
        return normalized.length() > 64 ? normalized.substring(0, 64) : normalized;
    }

    /**
     * 构建模型可见的 FQ 工具名。
     */
    public static String buildQualifiedName(String serverKey, String originalToolName) {
        return PREFIX
                + normalizeSegment(serverKey)
                + SEPARATOR
                + normalizeSegment(originalToolName);
    }

    public static boolean isQualifiedName(String name) {
        if (StringUtils.isBlank(name) || !name.startsWith(PREFIX)) {
            return false;
        }
        String body = name.substring(PREFIX.length());
        int idx = body.indexOf(SEPARATOR);
        return idx > 0 && idx < body.length() - SEPARATOR.length();
    }

    /**
     * 从 FQ 名解析 server 段；非 FQ 返回 null。
     */
    public static String parseServerKey(String qualifiedName) {
        if (!isQualifiedName(qualifiedName)) {
            return null;
        }
        String body = qualifiedName.substring(PREFIX.length());
        int idx = body.indexOf(SEPARATOR);
        return body.substring(0, idx);
    }
}
