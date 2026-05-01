package org.wwz.ai.test.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.reactor.service.replay.ToolOutputJsonBuilder;

import java.util.List;
import java.util.Map;

/**
 * ToolOutputJsonBuilder 契约测试。
 * 先锁定 output_json 只表达工具事实，不允许混入前端展示字段。
 */
public class ToolOutputJsonBuilderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void shouldBuildPlainTextOutputJsonWithoutFrontendFields() throws Exception {
        String json = ToolOutputJsonBuilder.buildPlainTextResult("第1行\n第2行");
        JsonNode root = MAPPER.readTree(json);

        Assert.assertEquals(1, root.get("schemaVersion").asInt());
        Assert.assertEquals("plain_text", root.get("resultType").asText());
        Assert.assertEquals("第1行\n第2行", root.get("data").get("text").asText());
        Assert.assertNull(root.get("taskId"));
        Assert.assertNull(root.get("renderKind"));
        Assert.assertNull(root.get("eventData"));
    }

    @Test
    public void shouldBuildErrorOutputJsonWithoutFrontendFields() throws Exception {
        String json = ToolOutputJsonBuilder.buildErrorResult("Tool missing_tool Error.", "Tool returned null");
        JsonNode root = MAPPER.readTree(json);

        Assert.assertEquals(1, root.get("schemaVersion").asInt());
        Assert.assertEquals("error", root.get("resultType").asText());
        Assert.assertEquals("Tool missing_tool Error.", root.get("data").get("message").asText());
        Assert.assertEquals("Tool returned null", root.get("data").get("errorMsg").asText());
        Assert.assertNull(root.get("messageOrder"));
        Assert.assertNull(root.get("taskOrder"));
    }

    @Test
    public void shouldBuildToolNativeResultWithSchemaVersion() throws Exception {
        String json = ToolOutputJsonBuilder.buildToolNativeResult(Map.of(
                "command", "get",
                "contentStorageMode", "artifact_only",
                "fileInfo", List.of(Map.of("fileName", "风险日报.md"))
        ));
        JsonNode root = MAPPER.readTree(json);

        Assert.assertEquals(1, root.get("schemaVersion").asInt());
        Assert.assertEquals("get", root.get("command").asText());
        Assert.assertEquals("artifact_only", root.get("contentStorageMode").asText());
        Assert.assertEquals("风险日报.md", root.get("fileInfo").get(0).get("fileName").asText());
        Assert.assertNull(root.get("taskId"));
        Assert.assertNull(root.get("renderKind"));
    }
}
