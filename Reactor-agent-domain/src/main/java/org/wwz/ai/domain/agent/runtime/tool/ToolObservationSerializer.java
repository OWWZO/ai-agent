package org.wwz.ai.domain.agent.runtime.tool;

import com.alibaba.fastjson.JSON;
import org.apache.commons.lang3.StringUtils;

/**
 * 对齐 LeAgent {@code ResultProcessor.serialize_for_llm}：
 * 把工具结果压成主智能体 transcript 里的 observation 字符串。
 *
 * <ul>
 *   <li>失败 + dict detail → {@code {"tool_ok":false,"error":...,"detail":...}}</li>
 *   <li>失败 + 仅 error → {@code Error: ...}</li>
 *   <li>成功 + String data → 原样字符串</li>
 *   <li>成功 + 其他 data → {@code json.dumps(data)}</li>
 * </ul>
 *
 * 不负责 artifactKey / {@code $$$}；那由 {@code ToolArtifactFormatter} 在截断后追加。
 */
public final class ToolObservationSerializer {

    /** 与 LeAgent {@code _LLM_TOOL_STRING_CAP} 一致。 */
    public static final int DEFAULT_LLM_TOOL_STRING_CAP = 96_000;

    /** 与 LeAgent {@code _TRUNCATION_NOTICE} 语义对齐（措辞略短）。 */
    public static final String TRUNCATION_NOTICE =
            "\n...[output truncated for context cap; narrow the query or use a smaller path]...";

    private ToolObservationSerializer() {
    }

    /**
     * 成功路径：String 原样，其余 JSON 序列化。
     */
    public static String serializeSuccess(Object data) {
        return serializeSuccess(data, DEFAULT_LLM_TOOL_STRING_CAP);
    }

    public static String serializeSuccess(Object data, int cap) {
        if (data == null) {
            return truncateForLlm("null", cap);
        }
        if (data instanceof String text) {
            return truncateForLlm(text, cap);
        }
        try {
            return truncateForLlm(JSON.toJSONString(data), cap);
        } catch (Exception ignore) {
            return truncateForLlm(String.valueOf(data), cap);
        }
    }

    /**
     * 失败路径：有结构化 detail 时输出 tool_ok JSON，否则 Error 前缀。
     */
    public static String serializeFailure(String error, Object detail) {
        return serializeFailure(error, detail, DEFAULT_LLM_TOOL_STRING_CAP);
    }

    public static String serializeFailure(String error, Object detail, int cap) {
        String errorText = StringUtils.defaultIfBlank(error, "Unknown error");
        if (detail instanceof java.util.Map<?, ?> map && !map.isEmpty()) {
            java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("tool_ok", Boolean.FALSE);
            payload.put("error", errorText);
            payload.put("detail", detail);
            try {
                return truncateForLlm(JSON.toJSONString(payload), cap);
            } catch (Exception ignore) {
                return truncateForLlm("Error: " + errorText, cap);
            }
        }
        if (detail != null && !(detail instanceof String)) {
            java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("tool_ok", Boolean.FALSE);
            payload.put("error", errorText);
            payload.put("detail", detail);
            try {
                return truncateForLlm(JSON.toJSONString(payload), cap);
            } catch (Exception ignore) {
                return truncateForLlm("Error: " + errorText, cap);
            }
        }
        return truncateForLlm("Error: " + errorText, cap);
    }

    /**
     * 从 {@link ToolResultPayload} 生成 observation（仅在 llmObservation 未预填时使用）。
     */
    public static String serializePayload(ToolResultPayload payload) {
        return serializePayload(payload, DEFAULT_LLM_TOOL_STRING_CAP);
    }

    public static String serializePayload(ToolResultPayload payload, int cap) {
        if (payload == null) {
            return truncateForLlm("Error: Unknown error", cap);
        }
        if (Boolean.TRUE.equals(payload.getFailed())) {
            Object detail = payload.getLlmData();
            if (detail == null && StringUtils.isNotBlank(payload.getToolResult())
                    && !StringUtils.equals(payload.getToolResult(), payload.getErrorMsg())) {
                detail = payload.getToolResult();
            }
            return serializeFailure(
                    StringUtils.defaultIfBlank(payload.getErrorMsg(), payload.getToolResult()),
                    detail,
                    cap
            );
        }
        if (payload.getLlmData() != null) {
            return serializeSuccess(payload.getLlmData(), cap);
        }
        if (StringUtils.isNotBlank(payload.getToolResult())) {
            return serializeSuccess(payload.getToolResult(), cap);
        }
        return truncateForLlm("", cap);
    }

    public static String truncateForLlm(String value) {
        return truncateForLlm(value, DEFAULT_LLM_TOOL_STRING_CAP);
    }

    public static String truncateForLlm(String value, int cap) {
        String text = value == null ? "" : value;
        if (cap <= 0 || text.length() <= cap) {
            return text;
        }
        int noticeLen = TRUNCATION_NOTICE.length();
        if (cap <= noticeLen) {
            return text.substring(0, cap);
        }
        return text.substring(0, cap - noticeLen) + TRUNCATION_NOTICE;
    }
}
