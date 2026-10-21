package org.wwz.ai.domain.agent.runtime.tool.common.docgen;

import org.wwz.ai.domain.agent.runtime.tool.common.doc.AbstractDocRemoteTool;

import java.util.List;
import java.util.Map;

/**
 * Document generation tools (HTTP → reactor-tool /v1/tool/*).
 * <p>
 * 生成侧与读取侧共用 {@link AbstractDocRemoteTool}；这里只保留生成特有的
 * 超时、data 字段集合和 rendered 截断。
 */
public abstract class AbstractDocGenTool extends AbstractDocRemoteTool {

    private static final long TIMEOUT_SECONDS = 240L;
    /** 单个字段进 llmData 前的本地上限，避免 observation 被长 JSON 字段占满。 */
    private static final int FIELD_LIMIT = 3000;
    private static final int RENDERED_LIMIT = 2000;

    @Override
    protected long timeoutSeconds() {
        return TIMEOUT_SECONDS;
    }

    @Override
    protected String fileEventCommand() {
        return "生成文档";
    }

    @Override
    protected String fileDescriptionSuffix() {
        return "生成文件";
    }

    @Override
    protected void extraLlmData(com.alibaba.fastjson.JSONObject json, Map<String, Object> data) {
        putIfPresent(data, json, "stats");
        putIfPresent(data, json, "message");
        putIfPresent(data, json, "warnings");
        putIfPresent(data, json, "lint_warnings");
        for (String key : List.of(
                "templates", "template", "themes", "theme", "payload", "resolved",
                "colors", "usage", "saved", "deleted", "name", "kind",
                "path", "variables", "chart_type", "format"
        )) {
            putIfPresent(data, json, key);
        }
        // rendered 可能很大：保留截断摘要，完整交付物看 produced_files / artifact
        String rendered = json.getString("rendered");
        if (org.apache.commons.lang3.StringUtils.isNotBlank(rendered)) {
            data.put("rendered", rendered.length() > RENDERED_LIMIT
                    ? rendered.substring(0, RENDERED_LIMIT) + "...[truncated]"
                    : rendered);
        }
    }

    private static void putIfPresent(Map<String, Object> data,
                                     com.alibaba.fastjson.JSONObject json,
                                     String key) {
        if (json == null || !json.containsKey(key) || json.get(key) == null) {
            return;
        }
        Object value = json.get(key);
        if (value instanceof String text && text.length() > FIELD_LIMIT) {
            data.put(key, text.substring(0, FIELD_LIMIT) + "...[truncated]");
            return;
        }
        data.put(key, value);
    }
}
