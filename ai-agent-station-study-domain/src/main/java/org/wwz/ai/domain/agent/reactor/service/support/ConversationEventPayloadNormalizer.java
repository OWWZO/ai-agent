package org.wwz.ai.domain.agent.reactor.service.support;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一整理历史事件 payload，避免写入侧和读取侧各自维护一套 artifact 引用兜底规则。
 */
public final class ConversationEventPayloadNormalizer {

    private ConversationEventPayloadNormalizer() {
    }

    public static Object normalizePayload(Object payload) {
        if (!(payload instanceof Map)) {
            return payload;
        }
        return normalizePayload((Map<String, Object>) payload);
    }

    public static JSONObject normalizePayload(Map<String, Object> payloadMap) {
        JSONObject normalized = new JSONObject(new LinkedHashMap<>());
        if (payloadMap == null || payloadMap.isEmpty()) {
            return normalized;
        }

        normalized.putAll(payloadMap);

        JSONArray artifactRefs = extractArtifactRefs(normalized);
        if (!artifactRefs.isEmpty()) {
            normalized.put("artifactRefs", artifactRefs);
            pruneLegacyArtifactFields(normalized);
        }
        return normalized;
    }

    public static JSONObject normalizePayloadJson(String payloadJson) {
        if (!StringUtils.hasText(payloadJson)) {
            return new JSONObject(new LinkedHashMap<>());
        }
        try {
            Object payload = com.alibaba.fastjson.JSON.parse(payloadJson);
            Object normalizedPayload = normalizePayload(payload);
            if (normalizedPayload instanceof JSONObject) {
                return (JSONObject) normalizedPayload;
            }
            if (normalizedPayload instanceof Map<?, ?> normalizedMap) {
                return new JSONObject(new LinkedHashMap<>((Map<String, Object>) normalizedMap));
            }
        } catch (Exception ignored) {
            // 读取历史脏数据时退化为空对象，避免中断整轮上下文恢复。
        }
        return new JSONObject(new LinkedHashMap<>());
    }

    public static List<JSONObject> extractNormalizedArtifactRefs(JSONObject payload) {
        if (payload == null) {
            return List.of();
        }
        JSONArray refs = payload.getJSONArray("artifactRefs");
        if (refs == null || refs.isEmpty()) {
            return List.of();
        }
        List<JSONObject> normalizedRefs = new ArrayList<>();
        for (Object item : refs) {
            if (item instanceof JSONObject) {
                normalizedRefs.add((JSONObject) item);
            } else if (item instanceof Map<?, ?> itemMap) {
                normalizedRefs.add(new JSONObject(new LinkedHashMap<>((Map<String, Object>) itemMap)));
            }
        }
        return normalizedRefs;
    }

    public static boolean isReferenceOnly(JSONObject payload,
                                          String eventType,
                                          String eventSubType,
                                          String contentText) {
        if (payload != null && payload.getBooleanValue("referenceOnly")) {
            return true;
        }
        if ("deep_search".equalsIgnoreCase(eventType) && "report".equalsIgnoreCase(eventSubType)) {
            return true;
        }
        if (StringUtils.hasText(contentText) && contentText.length() >= 400) {
            return true;
        }
        if (payload == null) {
            return false;
        }
        return switch (safeLower(eventType)) {
            case "html", "markdown", "code", "ppt", "file", "browser", "data_analysis" ->
                    !extractNormalizedArtifactRefs(payload).isEmpty();
            default -> false;
        };
    }

    private static JSONArray extractArtifactRefs(Map<String, Object> payloadMap) {
        Object directRefs = payloadMap.get("artifactRefs");
        if (directRefs instanceof List && !((List<?>) directRefs).isEmpty()) {
            return normalizeArtifactRefs((List<?>) directRefs, valueToString(payloadMap.get("messageType")));
        }

        Map<String, Object> outerResultMap = asMap(payloadMap.get("resultMap"));
        Map<String, Object> nestedResultMap = outerResultMap == null ? null : asMap(outerResultMap.get("resultMap"));

        List<?> legacyRefs = firstNonEmptyList(
                nestedResultMap == null ? null : nestedResultMap.get("fileInfo"),
                nestedResultMap == null ? null : nestedResultMap.get("fileList"),
                outerResultMap == null ? null : outerResultMap.get("fileInfo"),
                outerResultMap == null ? null : outerResultMap.get("fileList"),
                payloadMap.get("fileInfo"),
                payloadMap.get("fileList"));
        if (legacyRefs == null || legacyRefs.isEmpty()) {
            return new JSONArray();
        }
        return normalizeArtifactRefs(legacyRefs, valueToString(payloadMap.get("messageType")));
    }

    private static JSONArray normalizeArtifactRefs(List<?> rawRefs, String fallbackArtifactType) {
        JSONArray normalized = new JSONArray();
        for (Object rawRef : rawRefs) {
            JSONObject artifactRef = normalizeArtifactRef(rawRef, fallbackArtifactType);
            if (artifactRef != null) {
                normalized.add(artifactRef);
            }
        }
        return normalized;
    }

    private static JSONObject normalizeArtifactRef(Object rawRef, String fallbackArtifactType) {
        if (!(rawRef instanceof Map)) {
            return null;
        }

        Map<?, ?> refMap = (Map<?, ?>) rawRef;
        String previewUrl = firstNonBlank(
                refMap.get("previewUrl"),
                refMap.get("domainUrl"),
                refMap.get("url"),
                refMap.get("ossUrl"),
                refMap.get("downloadUrl"));
        String downloadUrl = firstNonBlank(
                refMap.get("downloadUrl"),
                refMap.get("ossUrl"),
                refMap.get("domainUrl"),
                refMap.get("url"));
        String resourceKey = firstNonBlank(
                refMap.get("resourceKey"),
                refMap.get("ossUrl"),
                refMap.get("downloadUrl"),
                refMap.get("domainUrl"),
                refMap.get("fileName"),
                refMap.get("name"));

        boolean missing = toBoolean(refMap.get("missing"))
                || (isBlank(previewUrl) && isBlank(downloadUrl));
        String missingReason = firstNonBlank(
                refMap.get("missingReason"),
                missing ? "引用资源不存在或已失效" : null);

        JSONObject normalized = new JSONObject(new LinkedHashMap<>());
        normalized.put("artifactType", firstNonBlank(refMap.get("artifactType"), refMap.get("type"), fallbackArtifactType));
        normalized.put("displayName", firstNonBlank(
                refMap.get("displayName"),
                refMap.get("fileName"),
                refMap.get("name"),
                resourceKey,
                "未命名文件"));
        normalized.put("resourceKey", resourceKey);
        normalized.put("downloadUrl", downloadUrl);
        normalized.put("previewUrl", previewUrl);
        normalized.put("fileSize", toLong(refMap.get("fileSize")));
        normalized.put("mimeType", firstNonBlank(refMap.get("mimeType")));
        normalized.put("missing", missing);
        normalized.put("missingReason", missingReason);
        return normalized;
    }

    private static void pruneLegacyArtifactFields(Map<String, Object> payloadMap) {
        removeArtifactFields(payloadMap);

        Map<String, Object> outerResultMap = asMap(payloadMap.get("resultMap"));
        if (outerResultMap != null) {
            removeArtifactFields(outerResultMap);
            Map<String, Object> nestedResultMap = asMap(outerResultMap.get("resultMap"));
            if (nestedResultMap != null) {
                removeArtifactFields(nestedResultMap);
            }
        }
    }

    private static void removeArtifactFields(Map<String, Object> targetMap) {
        targetMap.remove("fileInfo");
        targetMap.remove("fileList");
    }

    private static Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map)) {
            return null;
        }
        return (Map<String, Object>) value;
    }

    private static List<?> firstNonEmptyList(Object... candidates) {
        for (Object candidate : candidates) {
            if (candidate instanceof List && !((List<?>) candidate).isEmpty()) {
                return (List<?>) candidate;
            }
        }
        return null;
    }

    private static String firstNonBlank(Object... candidates) {
        for (Object candidate : candidates) {
            String value = valueToString(candidate);
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static String valueToString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        String text = valueToString(value);
        return "true".equalsIgnoreCase(text) || "1".equals(text);
    }

    private static Long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        String text = valueToString(value);
        if (text == null) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase();
    }
}
