package org.wwz.ai.domain.agent.runtime.tool.skill;

import org.junit.Assert;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SkillFrontMatterWriterTest {

    private static Map<String, Object> fields(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }

    private static SkillFrontMatter roundTrip(Map<String, Object> fields) {
        String document = SkillFrontMatterWriter.renderDocument(fields, "body");
        SkillFrontMatter parsed = SkillFrontMatterParser.parse(document);
        Assert.assertTrue("渲染结果必须能被重新解析：" + document, parsed.hasFrontMatter());
        Assert.assertTrue("重新解析不应降级：" + parsed.warnings(), parsed.warnings().isEmpty());
        return parsed;
    }

    /**
     * 核心回归：含半角冒号 + 空格的值必须被自动加引号，否则重新解析会报
     * mapping values are not allowed here。
     */
    @Test
    public void shouldQuoteValueContainingColon() {
        String value = "Detects and fixes patterns including: inflated symbolism";
        SkillFrontMatter parsed = roundTrip(fields("name", "demo", "description", value));

        Assert.assertEquals(value, parsed.fields().get("description"));
        Assert.assertTrue(SkillFrontMatterWriter.render(fields("name", "demo", "description", value))
                .contains("'"));
    }

    @Test
    public void shouldQuoteValueContainingHashAfterSpace() {
        String value = "see the guide #section-2 for details";
        SkillFrontMatter parsed = roundTrip(fields("name", "demo", "description", value));

        Assert.assertEquals(value, parsed.fields().get("description"));
    }

    @Test
    public void shouldQuoteValueStartingWithSpecialCharacter() {
        for (String value : List.of("- dash", "% percent", "@ at", "* star", "& amp", "! bang")) {
            SkillFrontMatter parsed = roundTrip(fields("name", "demo", "description", value));
            Assert.assertEquals(value, parsed.fields().get("description"));
        }
    }

    @Test
    public void shouldQuoteValueWithQuotesInside() {
        String value = "uses \"rule of three\" and 'parallelism'";
        SkillFrontMatter parsed = roundTrip(fields("name", "demo", "description", value));

        Assert.assertEquals(value, parsed.fields().get("description"));
    }

    @Test
    public void shouldNotWrapLongSingleLineValue() {
        String value = "x".repeat(600);
        String rendered = SkillFrontMatterWriter.render(fields("name", "demo", "description", value));

        // --- / name / description / --- 共 4 行；折行会超过 4 行
        long lineCount = rendered.lines().count();
        Assert.assertEquals(4L, lineCount);
    }

    @Test
    public void shouldKeepUnicodeUnescaped() {
        String value = "去除文本中的 AI 写作痕迹";
        String rendered = SkillFrontMatterWriter.render(fields("name", "demo", "description", value));

        Assert.assertTrue(rendered.contains(value));
        Assert.assertFalse(rendered.contains("\\u"));
    }

    @Test
    public void shouldPreserveListAndNestedValues() {
        Map<String, Object> source = fields(
                "name", "demo",
                "description", "a demo skill",
                "allowed-tools", List.of("Read", "Write"),
                "metadata", Map.of("hermes", Map.of("tags", List.of("alpha"))));

        SkillFrontMatter parsed = roundTrip(source);

        Assert.assertEquals(List.of("Read", "Write"), parsed.fields().get("allowed-tools"));
        Assert.assertTrue(parsed.fields().get("metadata") instanceof Map<?, ?>);
    }

    @Test
    public void shouldSkipNullValues() {
        Map<String, Object> source = fields("name", "demo", "description", "a demo skill", "version", null);
        SkillFrontMatter parsed = roundTrip(source);

        Assert.assertFalse(parsed.fields().containsKey("version"));
    }

    @Test
    public void shouldRenderEmptyFrontMatterBlock() {
        String rendered = SkillFrontMatterWriter.render(Map.of());

        Assert.assertEquals("---\n---\n", rendered);
    }

    @Test
    public void shouldBeIdempotentAcrossTwoRoundTrips() {
        Map<String, Object> source = fields(
                "name", "humanizer",
                "description", "Detects and fixes patterns including: inflated symbolism",
                "allowed-tools", List.of("Read", "Write"));

        String first = SkillFrontMatterWriter.renderDocument(source, "body");
        SkillFrontMatter parsedOnce = SkillFrontMatterParser.parse(first);
        String second = SkillFrontMatterWriter.renderDocument(parsedOnce.fields(), parsedOnce.body());

        Assert.assertEquals(first, second);
    }
}
