package org.wwz.ai.domain.agent.reactor.agent.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 工具 Schema 规范化工具。
 * 用于兼容上游返回的空对象 Schema，避免 OpenAI Function Calling 因缺少 properties/required 报错。
 */
@Slf4j
public final class ToolSchemaNormalizer {

    private ToolSchemaNormalizer() {
    }

    /**
     * 规范化 JSON 字符串格式的工具 Schema。
     */
    public static String normalizeSchema(String rawSchema, String toolName) {
        Map<String, Object> normalized = normalizeSchemaAsMap(rawSchema, toolName);
        return JSON.toJSONString(normalized);
    }

    /**
     * 规范化 Map 结构的工具 Schema。
     */
    public static Map<String, Object> normalizeSchema(Map<String, Object> rawSchema, String toolName) {
        String before = JSON.toJSONString(rawSchema);
        Map<String, Object> normalized = deepCopy(rawSchema);
        sanitizeSchemaNode(normalized, true);
        String after = JSON.toJSONString(normalized);

        if (!Objects.equals(before, after)) {
            log.warn("检测到不完整 object schema，已自动补齐 properties/required: toolName={}",
                    StringUtils.defaultIfBlank(toolName, "unknown"));
        }

        return normalized;
    }

    /**
     * 规范化 JSON 字符串格式的工具 Schema，并返回 Map 结构。
     */
    public static Map<String, Object> normalizeSchemaAsMap(String rawSchema, String toolName) {
        return normalizeSchema(parseSchema(rawSchema), toolName);
    }

    /**
     * 解析 JSON 字符串，解析失败时回退到标准空对象 Schema。
     */
    private static Map<String, Object> parseSchema(String rawSchema) {
        if (StringUtils.isBlank(rawSchema) || "{}".equals(StringUtils.trim(rawSchema))) {
            return createEmptyObjectSchema();
        }

        try {
            Map<String, Object> parsed = JSON.parseObject(rawSchema, new TypeReference<LinkedHashMap<String, Object>>() {
            });
            return parsed == null ? createEmptyObjectSchema() : parsed;
        } catch (Exception e) {
            log.warn("工具 Schema JSON 解析失败，已回退为空对象 Schema: reason={}", e.getMessage());
            return createEmptyObjectSchema();
        }
    }

    /**
     * 深拷贝 Schema，避免污染调用方传入的数据。
     */
    private static Map<String, Object> deepCopy(Map<String, Object> rawSchema) {
        if (rawSchema == null || rawSchema.isEmpty()) {
            return createEmptyObjectSchema();
        }

        try {
            Map<String, Object> copied = JSON.parseObject(JSON.toJSONString(rawSchema),
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    });
            return copied == null ? createEmptyObjectSchema() : copied;
        } catch (Exception e) {
            log.warn("工具 Schema 深拷贝失败，已回退为空对象 Schema: reason={}", e.getMessage());
            return createEmptyObjectSchema();
        }
    }

    /**
     * 递归修复 Schema 节点。
     */
    @SuppressWarnings("unchecked")
    private static void sanitizeSchemaNode(Object node, boolean root) {
        if (node instanceof Map<?, ?> mapNode) {
            Map<String, Object> schemaMap = (Map<String, Object>) mapNode;
            schemaMap.remove("$schema");
            schemaMap.remove("additionalProperties");

            if (root && schemaMap.isEmpty()) {
                resetToEmptyObjectSchema(schemaMap);
            } else {
                Object type = schemaMap.get("type");
                boolean objectSchema = "object".equals(type)
                        || (type == null && (root || schemaMap.containsKey("properties") || schemaMap.containsKey("required")));

                if (objectSchema) {
                    schemaMap.put("type", "object");

                    Object properties = schemaMap.get("properties");
                    if (!(properties instanceof Map<?, ?>)) {
                        schemaMap.put("properties", new LinkedHashMap<String, Object>());
                    }

                    Object required = schemaMap.get("required");
                    if (!(required instanceof List<?>)) {
                        schemaMap.put("required", new ArrayList<String>());
                    }
                }
            }

            for (Object value : schemaMap.values()) {
                sanitizeSchemaNode(value, false);
            }
            return;
        }

        if (node instanceof List<?> listNode) {
            for (Object value : listNode) {
                sanitizeSchemaNode(value, false);
            }
        }
    }

    /**
     * 创建标准空对象 Schema。
     */
    private static Map<String, Object> createEmptyObjectSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        resetToEmptyObjectSchema(schema);
        return schema;
    }

    /**
     * 将当前对象重置为标准空对象 Schema。
     */
    private static void resetToEmptyObjectSchema(Map<String, Object> schema) {
        schema.clear();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<String, Object>());
        schema.put("required", new ArrayList<String>());
    }
}
