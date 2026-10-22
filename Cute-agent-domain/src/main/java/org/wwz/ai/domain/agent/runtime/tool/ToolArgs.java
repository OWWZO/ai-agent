package org.wwz.ai.domain.agent.runtime.tool;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具入参归一化。
 * <p>
 * 模型经 function calling 传来的 {@code input} 形态并不稳定：有时是 Map，有时是 JSON 字符串，
 * 有时是别的 POJO。这里统一收敛为只读语义的 {@code Map<String, Object>}，
 * 供各工具在 {@code execute} 开头一次性取值。
 */
public final class ToolArgs {

    private ToolArgs() {
    }

    /**
     * 宽松归一：接受 Map / JSON 字符串 / 任意可序列化对象，无法识别时返回空 map。
     * <p>
     * 返回的 map 键统一为 String；调用方不应修改它。
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Object input) {
        if (input instanceof Map<?, ?> map) {
            return stringKeyed(map);
        }
        if (input == null) {
            return Map.of();
        }
        if (input instanceof String text && StringUtils.isNotBlank(text)) {
            try {
                Object parsed = JSON.parse(text);
                if (parsed instanceof Map<?, ?> map) {
                    return stringKeyed(map);
                }
            } catch (Exception ignored) {
                // 非法 JSON 落到下面的兜底
            }
            return Map.of();
        }
        try {
            JSONObject obj = JSON.parseObject(JSON.toJSONString(input));
            return obj == null ? Map.of() : new LinkedHashMap<>(obj);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    /**
     * 严格归一：只接受 Map，其它一律返回空 map。
     * <p>
     * 适用于入参本来就必须是结构化对象的工具，避免把字符串误当参数表。
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> mapStrict(Object input) {
        if (input instanceof Map<?, ?> map) {
            return stringKeyed(map);
        }
        return Map.of();
    }

    /** 取值并 trim；缺失或空白返回空串。 */
    public static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    /** 取值并 trim；缺失或空白返回 null。 */
    public static String strOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static Map<String, Object> stringKeyed(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>(map.size());
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }
}
