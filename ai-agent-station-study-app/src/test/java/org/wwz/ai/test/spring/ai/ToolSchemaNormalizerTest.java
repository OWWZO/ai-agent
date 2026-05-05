package org.wwz.ai.test.spring.ai;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.wwz.ai.domain.agent.runtime.dto.tool.McpToolInfo;
import org.wwz.ai.domain.agent.runtime.tool.common.skill.GrepTool;
import org.wwz.ai.domain.agent.runtime.tool.common.skill.ReadTool;
import org.wwz.ai.domain.agent.runtime.tool.common.skill.ScriptRunnerTool;
import org.wwz.ai.domain.agent.runtime.tool.common.skill.SkillTool;
import org.wwz.ai.domain.agent.runtime.tool.mcp.runtime.RegistryBackedToolCallback;
import org.wwz.ai.domain.agent.runtime.util.ToolSchemaNormalizer;

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

    @Test
    public void test_validSkillToolSchemasShouldNotEmitIncompleteSchemaWarning() {
        Logger logger = (Logger) LoggerFactory.getLogger(ToolSchemaNormalizer.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        try {
            ToolSchemaNormalizer.normalizeSchema(new SkillTool(null).toParams(), "skill_tool");
            ToolSchemaNormalizer.normalizeSchema(new ReadTool(null, null).toParams(), "read_tool");
            ToolSchemaNormalizer.normalizeSchema(new GrepTool(null, null).toParams(), "grep_tool");
            ToolSchemaNormalizer.normalizeSchema(new ScriptRunnerTool(null, null, null).toParams(), "script_runner_tool");

            boolean hasIncompleteSchemaWarning = listAppender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .anyMatch(message -> message.contains("检测到不完整 object schema"));

            Assert.assertFalse("合法 schema 不应该触发不完整 object schema 告警", hasIncompleteSchemaWarning);
        } finally {
            logger.detachAppender(listAppender);
            listAppender.stop();
        }
    }

    private Map<String, Object> parseSchema(String schema) {
        return JSON.parseObject(schema, new TypeReference<LinkedHashMap<String, Object>>() {
        });
    }
}
