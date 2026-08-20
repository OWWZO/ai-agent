package org.wwz.ai.test.domain.canvas;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.tool.common.canvas.GenUiSchema;

import java.util.List;
import java.util.Map;

public class GenUiSchemaTest {

    @Test
    public void validateBareRootTree() {
        Map<String, Object> tree = Map.of(
                "kind", "Card",
                "props", Map.of("title", "Hello"),
                "children", List.of(
                        Map.of("kind", "Stat", "props", Map.of("label", "world", "value", "1"))
                )
        );
        Map<String, Object> normalized = GenUiSchema.validateUiTree(tree);
        Assert.assertEquals("1", normalized.get("schemaVersion"));
        Assert.assertTrue(normalized.get("root") instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) normalized.get("root");
        Assert.assertEquals("Card", root.get("kind"));
        Assert.assertNotNull(root.get("nodeId"));
    }

    @Test
    public void rejectUnknownKind() {
        try {
            GenUiSchema.validateUiTree(Map.of("kind", "UnknownThing"));
            Assert.fail("should fail");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("unsupported kind"));
        }
    }

    @Test
    public void validatePatch() {
        Map<String, Object> payload = Map.of(
                "patches", List.of(
                        Map.of("op", "replace", "path", "/root/props/title", "value", "New")
                )
        );
        Map<String, Object> normalized = GenUiSchema.validateUiPatch(payload);
        Assert.assertEquals(1, ((List<?>) normalized.get("patches")).size());
    }
}
