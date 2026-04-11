package org.wwz.ai.test.spring.ai;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.wwz.ai.domain.agent.reactor.agent.dto.tool.McpToolInfo;
import org.wwz.ai.domain.agent.reactor.agent.tool.mcp.runtime.RegistryBackedToolCallback;
import org.wwz.ai.domain.agent.reactor.agent.util.ToolSchemaNormalizer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具 Schema 规范化测试
 */
public class ToolSchemaNormalizerTest {

    @Test
    public void test_normalizeEmptySchemaString() {
        Map<String, Object> normalized = parseSchema(ToolSchemaNormalizer.normalizeSchema((String) null, "check_login_status"));

        Assert.assertEquals("object", normalized.get("type"));
        Assert.assertTrue(normalized.get("properties") instanceof Map);
        Assert.assertTrue(((Map<?, ?>) normalized.get("properties")).isEmpty());
        Assert.assertTrue(normalized.get("required") instanceof List);
        Assert.assertTrue(((List<?>) normalized.get("required")).isEmpty());
    }

    @Test
    public void test_normalizeObjectSchemaWithMissingPropertiesAndRequired() {
        Map<String, Object> normalized = parseSchema(ToolSchemaNormalizer.normalizeSchema("{\"type\":\"object\"}", "check_login_status"));

        Assert.assertEquals("object", normalized.get("type"));
        Assert.assertTrue(normalized.get("properties") instanceof Map);
        Assert.assertTrue(((Map<?, ?>) normalized.get("properties")).isEmpty());
        Assert.assertTrue(normalized.get("required") instanceof List);
        Assert.assertTrue(((List<?>) normalized.get("required")).isEmpty());
    }

    @Test
    public void test_normalizeInvalidPropertiesAndRequiredTypes() {
        Map<String, Object> normalized = parseSchema(ToolSchemaNormalizer.normalizeSchema(
                "{\"type\":\"object\",\"properties\":\"bad\",\"required\":\"bad\"}",
                "check_login_status"));

        Assert.assertEquals("object", normalized.get("type"));
        Assert.assertTrue(normalized.get("properties") instanceof Map);
        Assert.assertTrue(((Map<?, ?>) normalized.get("properties")).isEmpty());
        Assert.assertTrue(normalized.get("required") instanceof List);
        Assert.assertTrue(((List<?>) normalized.get("required")).isEmpty());
    }

    @Test
    public void test_keepValidSchemaAndRemoveUnsupportedFields() {
        Map<String, Object> rawSchema = new LinkedHashMap<>();
        rawSchema.put("type", "object");
        rawSchema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        rawSchema.put("additionalProperties", false);
        rawSchema.put("properties", Map.of("query", Map.of("type", "string")));
        rawSchema.put("required", List.of("query"));

        Map<String, Object> normalized = ToolSchemaNormalizer.normalizeSchema(rawSchema, "deep_search");

        Assert.assertEquals("object", normalized.get("type"));
        Assert.assertFalse(normalized.containsKey("$schema"));
        Assert.assertFalse(normalized.containsKey("additionalProperties"));
        Assert.assertTrue(normalized.get("properties") instanceof Map);
        Assert.assertEquals("string", ((Map<?, ?>) ((Map<?, ?>) normalized.get("properties")).get("query")).get("type"));
        Assert.assertEquals(List.of("query"), normalized.get("required"));
    }

    @Test
    public void test_registryBackedToolCallbackUsesNormalizedSchema() {
        McpToolInfo toolInfo = McpToolInfo.builder()
                .mcpId("mcp-xhs")
                .name("check_login_status")
                .desc("检查小红书登录状态")
                .parameters("{\"type\":\"object\"}")
                .build();

        RegistryBackedToolCallback callback = new RegistryBackedToolCallback(null, toolInfo);
        ToolDefinition toolDefinition = callback.getToolDefinition();
        Map<String, Object> normalized = parseSchema(toolDefinition.inputSchema());

        Assert.assertEquals("object", normalized.get("type"));
        Assert.assertTrue(normalized.get("properties") instanceof Map);
        Assert.assertTrue(((Map<?, ?>) normalized.get("properties")).isEmpty());
        Assert.assertEquals(List.of(), normalized.get("required"));
    }

    private Map<String, Object> parseSchema(String schema) {
        return JSON.parseObject(schema, new TypeReference<LinkedHashMap<String, Object>>() {
        });
    }
}
