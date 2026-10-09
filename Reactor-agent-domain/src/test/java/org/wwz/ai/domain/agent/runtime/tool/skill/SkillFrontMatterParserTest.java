package org.wwz.ai.domain.agent.runtime.tool.skill;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Map;

public class SkillFrontMatterParserTest {

    @Test
    public void shouldParseStandardFrontMatter() {
        SkillFrontMatter frontMatter = SkillFrontMatterParser.parse("""
                ---
                name: demo
                description: a demo skill
                ---

                # Title

                body text
                """);

        Assert.assertTrue(frontMatter.hasFrontMatter());
        Assert.assertEquals("demo", frontMatter.fields().get("name"));
        Assert.assertEquals("a demo skill", frontMatter.fields().get("description"));
        Assert.assertEquals("# Title\n\nbody text", frontMatter.body().strip());
        Assert.assertTrue(frontMatter.warnings().isEmpty());
    }

    @Test
    public void shouldStripSingleLeadingBom() {
        SkillFrontMatter frontMatter = SkillFrontMatterParser.parse(
                "\uFEFF---\nname: demo\ndescription: a demo skill\n---\n\nbody\n");

        Assert.assertTrue(frontMatter.hasFrontMatter());
        Assert.assertEquals("demo", frontMatter.fields().get("name"));
    }

    @Test
    public void shouldKeepBomInsideContentAsData() {
        SkillFrontMatter frontMatter = SkillFrontMatterParser.parse(
                "---\nname: demo\ndescription: a \uFEFFb\n---\n\nbody\n");

        Assert.assertEquals("a \uFEFFb", frontMatter.fields().get("description"));
    }

    @Test
    public void shouldHandleCrlf() {
        SkillFrontMatter frontMatter = SkillFrontMatterParser.parse(
                "---\r\nname: demo\r\ndescription: a demo skill\r\n---\r\n\r\nbody\r\n");

        Assert.assertTrue(frontMatter.hasFrontMatter());
        Assert.assertEquals("demo", frontMatter.fields().get("name"));
        Assert.assertEquals("body", frontMatter.body().strip());
    }

    @Test
    public void shouldReturnFullTextWhenNoFrontMatter() {
        SkillFrontMatter frontMatter = SkillFrontMatterParser.parse("# Only body\n\nhello\n");

        Assert.assertFalse(frontMatter.hasFrontMatter());
        Assert.assertTrue(frontMatter.fields().isEmpty());
        Assert.assertEquals("# Only body\n\nhello", frontMatter.body().strip());
    }

    @Test
    public void shouldReturnFullTextWhenFenceUnclosed() {
        SkillFrontMatter frontMatter = SkillFrontMatterParser.parse("---\nname: demo\nbody\n");

        Assert.assertFalse(frontMatter.hasFrontMatter());
        Assert.assertTrue(frontMatter.fields().isEmpty());
    }

    /**
     * 本次线上故障的原始形态：description 里含半角冒号 + 空格。
     * 加引号时是合法 YAML，必须原样解析成功且不剥引号。
     */
    @Test
    public void shouldParseQuotedDescriptionContainingColon() {
        String description = "Detects and fixes patterns including: inflated symbolism, promotional language";
        SkillFrontMatter frontMatter = SkillFrontMatterParser.parse(
                "---\nname: humanizer\ndescription: \"" + description + "\"\n---\n\nbody\n");

        Assert.assertTrue(frontMatter.hasFrontMatter());
        Assert.assertTrue(frontMatter.warnings().isEmpty());
        Assert.assertEquals(description, frontMatter.fields().get("description"));
    }

    @Test
    public void shouldParseBlockScalar() {
        SkillFrontMatter frontMatter = SkillFrontMatterParser.parse("""
                ---
                name: demo
                description: |
                  line one
                  line two
                ---

                body
                """);

        String description = String.valueOf(frontMatter.fields().get("description"));
        Assert.assertTrue(description.startsWith("line one"));
        Assert.assertTrue(description.contains("line two"));
    }

    @Test
    public void shouldParseListField() {
        SkillFrontMatter frontMatter = SkillFrontMatterParser.parse("""
                ---
                name: demo
                description: a demo skill
                allowed-tools:
                  - Read
                  - Write
                ---

                body
                """);

        Object raw = frontMatter.fields().get("allowed-tools");
        Assert.assertTrue(raw instanceof List<?>);
        Assert.assertEquals(List.of("Read", "Write"), raw);
    }

    @Test
    public void shouldParseNestedMetadata() {
        SkillFrontMatter frontMatter = SkillFrontMatterParser.parse("""
                ---
                name: demo
                description: a demo skill
                metadata:
                  hermes:
                    tags: [alpha, beta]
                ---

                body
                """);

        Object metadata = frontMatter.fields().get("metadata");
        Assert.assertTrue(metadata instanceof Map<?, ?>);
        Object hermes = ((Map<?, ?>) metadata).get("hermes");
        Assert.assertTrue(hermes instanceof Map<?, ?>);
        Assert.assertEquals(List.of("alpha", "beta"), ((Map<?, ?>) hermes).get("tags"));
    }

    /**
     * 畸形 YAML 不抛异常，降级为逐行解析，并留下 warning。
     */
    @Test
    public void shouldFallBackToLineParsingOnMalformedYaml() {
        SkillFrontMatter frontMatter = SkillFrontMatterParser.parse(
                "---\nname: demo\ndescription: a: b\n---\n\nbody\n");

        Assert.assertTrue(frontMatter.hasFrontMatter());
        Assert.assertFalse(frontMatter.warnings().isEmpty());
        Assert.assertEquals("demo", frontMatter.fields().get("name"));
        Assert.assertEquals("a: b", frontMatter.fields().get("description"));
    }

    @Test
    public void shouldFallBackWhenYamlTopLevelIsNotMap() {
        SkillFrontMatter frontMatter = SkillFrontMatterParser.parse("---\n- just\n- a list\n---\n\nbody\n");

        Assert.assertTrue(frontMatter.hasFrontMatter());
        Assert.assertFalse(frontMatter.warnings().isEmpty());
    }

    @Test
    public void shouldReturnEmptyForBlankInput() {
        SkillFrontMatter frontMatter = SkillFrontMatterParser.parse("");

        Assert.assertFalse(frontMatter.hasFrontMatter());
        Assert.assertTrue(frontMatter.fields().isEmpty());
        Assert.assertEquals("", frontMatter.body());
    }

    @Test
    public void shouldRejectNullInput() {
        Assert.assertThrows(IllegalArgumentException.class, () -> SkillFrontMatterParser.parse(null));
    }

    @Test
    public void shouldKeepRawText() {
        SkillFrontMatter frontMatter = SkillFrontMatterParser.parse(
                "---\nname: demo\ndescription: a demo skill\n---\n\nbody\n");

        Assert.assertEquals("name: demo\ndescription: a demo skill", frontMatter.rawText());
    }

    @Test
    public void shouldExposeTrimmedStringField() {
        SkillFrontMatter frontMatter = SkillFrontMatterParser.parse(
                "---\nname: demo\ndescription: a demo skill\n---\n\nbody\n");

        Assert.assertEquals("demo", frontMatter.stringField("name"));
        Assert.assertNull(frontMatter.stringField("missing"));
    }
}
