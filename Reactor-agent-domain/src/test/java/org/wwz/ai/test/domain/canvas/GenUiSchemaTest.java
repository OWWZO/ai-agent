package org.wwz.ai.test.domain.canvas;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.tool.common.canvas.GenUiSchema;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

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

    @Test
    public void rejectDepthDuringNormalization() {
        Map<String, Object> root = node("Card");
        Map<String, Object> current = root;
        for (int i = 0; i < 10000; i++) {
            Map<String, Object> child = node("Card");
            current.put("children", List.of(child));
            current = child;
        }

        try {
            GenUiSchema.validateUiTree(root, 24, 20000);
            Assert.fail("should fail on depth");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("tree depth 25 exceeds max 24", e.getMessage());
        }
    }

    @Test
    public void rejectNodeBudgetDuringNormalization() {
        Map<String, Object> root = node("Card");
        root.put("children", List.of(node("Card"), node("Card")));

        try {
            GenUiSchema.validateUiTree(root, 24, 2);
            Assert.fail("should fail on node count");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("tree node count 3 exceeds max 2", e.getMessage());
        }
    }

    @Test
    public void rejectCyclicNodeReference() {
        Map<String, Object> root = node("Card");
        List<Object> children = new ArrayList<>();
        children.add(root);
        root.put("children", children);

        try {
            GenUiSchema.validateUiTree(root);
            Assert.fail("should fail on cycle");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("tree contains cyclic node reference", e.getMessage());
        }
    }

    private static Map<String, Object> node(String kind) {
        return new HashMap<>(Map.of("kind", kind));
    }
}
