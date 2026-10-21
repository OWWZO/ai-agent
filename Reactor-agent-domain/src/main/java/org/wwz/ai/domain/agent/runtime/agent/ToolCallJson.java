package org.wwz.ai.domain.agent.runtime.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

/**
 * 工具调用链路共用的 JSON 归一。
 * <p>
 * 入参可能来自模型、MCP 或内部拼装，形态不保证是合法 JSON；账本和 SSE 卡片都要求稳定文本，
 * 因此统一在这里退化为 {@code "{}"}，避免每个调用点各写一套 try/catch。
 */
final class ToolCallJson {

    static final ObjectMapper MAPPER = new ObjectMapper();

    private ToolCallJson() {
    }

    /** 归一为合法 JSON 文本；空值或非法 JSON 退化为 "{}"。 */
    static String normalize(String payload) {
        if (StringUtils.isBlank(payload)) {
            return "{}";
        }
        try {
            return MAPPER.readTree(payload).toString();
        } catch (Exception ignore) {
            return "{}";
        }
    }

    /** 归一后解析成通用对象；失败返回 null（SSE 卡片允许没有入参）。 */
    static Object parseOrNull(String payload) {
        try {
            return MAPPER.readValue(normalize(payload), Object.class);
        } catch (Exception ignore) {
            return null;
        }
    }

    /** 序列化为 JSON 文本；失败退化为 "{}"。 */
    static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception ignore) {
            return "{}";
        }
    }
}
