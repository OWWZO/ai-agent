package org.wwz.ai.domain.agent.reactor.service.replay;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一构建工具原生 output_json。
 */
public final class ToolOutputJsonBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ToolOutputJsonBuilder() {
    }

    /**
     * 纯文本 fallback 结果。
     */
    public static String buildPlainTextResult(String text) {
        return writeJson(Map.of(
                "schemaVersion", 1,
                "resultType", "plain_text",
                "data", Map.of("text", StringUtils.defaultString(text))
        ));
    }

    /**
     * 错误 fallback 结果。
     */
    public static String buildErrorResult(String message, String errorMsg) {
        return writeJson(Map.of(
                "schemaVersion", 1,
                "resultType", "error",
                "data", Map.of(
                        "message", StringUtils.defaultString(message),
                        "errorMsg", StringUtils.defaultString(errorMsg)
                )
        ));
    }

    /**
     * rich tool 原生结果。
     * 若调用方未设置 schemaVersion，则默认补 1。
     */
    public static String buildToolNativeResult(Object data) {
        LinkedHashMap<String, Object> wrapper = new LinkedHashMap<>();
        if (data != null) {
            wrapper.putAll(MAPPER.convertValue(data, new TypeReference<LinkedHashMap<String, Object>>() {
            }));
        }
        wrapper.putIfAbsent("schemaVersion", 1);
        return writeJson(wrapper);
    }

    private static String writeJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("构建 output_json 失败", e);
        }
    }
}
