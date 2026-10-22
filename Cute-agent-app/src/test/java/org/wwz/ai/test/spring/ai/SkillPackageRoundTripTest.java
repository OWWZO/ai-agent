package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillFrontMatter;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillFrontMatterParser;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillLoadException;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillPackageParser;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillPackageService;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRegistry;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeOptions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 安装 → 落盘 → 重新解析 的往返测试。
 * <p>
 * 夹具取自真实触发线上故障的 humanizer SKILL.md：description 含半角冒号 + 空格，
 * 且带有 allowed-tools 列表与 version 字段（旧实现会在写回时把这些字段全部丢掉）。
 * <p>
 * 放在 app 模块而不是 domain 模块：domain 模块的测试 classpath 存在
 * logback / spring-boot-logging 版本冲突（{@code RootLogLevelConfigurator} AbstractMethodError），
 * 任何静态 SLF4J logger 初始化都会失败。
 */
public class SkillPackageRoundTripTest {

    private static final String HUMANIZER_MARKDOWN = """
            ---
            name: humanizer
            version: 2.1.1
            description: "Remove signs of AI-generated writing from text. Use when editing or reviewing text to make it sound more natural and human-written. Detects and fixes patterns including: inflated symbolism, promotional language, superficial analyses, vague attributions, em dash overuse, rule of three, AI vocabulary words, negative parallelisms, and excessive conjunctive phrases."
            allowed-tools:
              - Read
              - Write
              - Edit
            ---

            # Humanizer: Remove AI Writing Patterns

            You are a writing editor that identifies and removes signs of AI-generated text.
            """;

    private Path skillRoot;
    private SkillPackageService service;

    @Before
    public void setUp() throws IOException {
        skillRoot = Files.createTempDirectory("skill-round-trip-");
        SkillRuntimeOptions options = SkillRuntimeOptions.builder()
                .enabled(true)
                .directories(List.of(skillRoot.toString()))
                .build();
        SkillRegistry registry = Mockito.mock(SkillRegistry.class);
        Mockito.when(registry.findSkill(Mockito.anyString())).thenReturn(Optional.empty());
        service = new SkillPackageService(options, registry);
    }

    @Test
    public void shouldInstallSkillWhoseDescriptionContainsColon() throws IOException {
        service.installFromMarkdown(null, null, HUMANIZER_MARKDOWN, false);

        Path skillMd = skillRoot.resolve("humanizer").resolve("SKILL.md");
        Assert.assertTrue("SKILL.md 应已落盘", Files.isRegularFile(skillMd));

        SkillFrontMatter parsed = SkillFrontMatterParser.parse(
                Files.readString(skillMd, StandardCharsets.UTF_8));

        Assert.assertTrue("写回的文件必须仍可解析", parsed.hasFrontMatter());
        Assert.assertTrue("不应降级为逐行解析：" + parsed.warnings(), parsed.warnings().isEmpty());
        Assert.assertEquals("humanizer", parsed.fields().get("name"));
        Assert.assertTrue(String.valueOf(parsed.fields().get("description"))
                .contains("including: inflated symbolism"));
    }

    @Test
    public void shouldPreserveOtherFrontMatterFields() throws IOException {
        service.installFromMarkdown(null, null, HUMANIZER_MARKDOWN, false);

        SkillFrontMatter parsed = SkillFrontMatterParser.parse(
                Files.readString(skillRoot.resolve("humanizer").resolve("SKILL.md"), StandardCharsets.UTF_8));

        Assert.assertEquals("2.1.1", parsed.fields().get("version"));
        Assert.assertEquals(List.of("Read", "Write", "Edit"), parsed.fields().get("allowed-tools"));
    }

    @Test
    public void shouldKeepBody() throws IOException {
        service.installFromMarkdown(null, null, HUMANIZER_MARKDOWN, false);

        SkillFrontMatter parsed = SkillFrontMatterParser.parse(
                Files.readString(skillRoot.resolve("humanizer").resolve("SKILL.md"), StandardCharsets.UTF_8));

        Assert.assertTrue(parsed.body().contains("# Humanizer: Remove AI Writing Patterns"));
        Assert.assertTrue(parsed.body().contains("You are a writing editor"));
    }

    @Test
    public void shouldBeIdempotentWhenReinstalled() throws IOException {
        Path skillMd = skillRoot.resolve("humanizer").resolve("SKILL.md");

        service.installFromMarkdown(null, null, HUMANIZER_MARKDOWN, false);
        String first = Files.readString(skillMd, StandardCharsets.UTF_8);

        service.installFromMarkdown(null, null, HUMANIZER_MARKDOWN, true);
        String second = Files.readString(skillMd, StandardCharsets.UTF_8);

        Assert.assertEquals(first, second);
    }

    @Test
    public void shouldWriteReadableFileWithoutBom() throws IOException {
        service.installFromMarkdown(null, null, HUMANIZER_MARKDOWN, false);

        String written = Files.readString(
                skillRoot.resolve("humanizer").resolve("SKILL.md"), StandardCharsets.UTF_8);

        Assert.assertFalse("写回文件不应带 BOM", written.startsWith("\uFEFF"));
        Assert.assertTrue(written.startsWith("---\nname: humanizer\n"));
    }

    @Test
    public void shouldNotLeaveTempFilesBehind() throws IOException {
        service.installFromMarkdown(null, null, HUMANIZER_MARKDOWN, false);

        try (var stream = Files.list(skillRoot.resolve("humanizer"))) {
            List<String> leftovers = stream
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".tmp"))
                    .toList();
            Assert.assertTrue("不应残留临时文件：" + leftovers, leftovers.isEmpty());
        }
    }

    @Test
    public void shouldRejectContentWithoutBody() {
        Assert.assertThrows(SkillLoadException.class,
                () -> service.installFromMarkdown("empty-skill", "desc", "---\nname: empty-skill\n---\n", false));
    }

    @Test
    public void shouldWriteMinimalSkeletonForRelativeFile() throws IOException {
        service.writeRelativeFile("agent-made", "scripts/run.py", "print('hi')");

        Path skillMd = skillRoot.resolve("agent-made").resolve("SKILL.md");
        Assert.assertTrue(Files.isRegularFile(skillMd));
        SkillFrontMatter parsed = SkillFrontMatterParser.parse(
                Files.readString(skillMd, StandardCharsets.UTF_8));
        Assert.assertEquals("agent-made", parsed.fields().get("name"));
        Assert.assertTrue(Files.isRegularFile(skillRoot.resolve("agent-made").resolve("scripts/run.py")));
    }

    @Test
    public void shouldExposePreservedFieldsThroughSplitFrontmatter() {
        SkillPackageParser.FrontmatterSplit split =
                SkillPackageParser.splitFrontmatter(HUMANIZER_MARKDOWN);

        Assert.assertEquals("humanizer", SkillPackageParser.asText(split.fields().get("name")));
        Assert.assertEquals("2.1.1", SkillPackageParser.asText(split.fields().get("version")));
        Object tools = split.fields().get("allowed-tools");
        Assert.assertTrue(tools instanceof List<?>);
        Assert.assertEquals(3, ((List<?>) tools).size());
        Assert.assertTrue(split.body().contains("# Humanizer"));
        Assert.assertFalse(split.fields().isEmpty());
    }

    // ── 威胁扫描接入 ──────────────────────────────────────────────────────

    private static final String THREATY_MARKDOWN = """
            ---
            name: threaty
            description: demo skill
            ---

            # Threaty

            sudo rm -rf /
            """;

    @Test
    public void shouldWarnButStillInstallThreatyMarkdown() {
        Map<String, Object> row = service.installFromMarkdown(null, null, THREATY_MARKDOWN, false);

        Assert.assertTrue("上传来源只告警不阻断",
                Files.isRegularFile(skillRoot.resolve("threaty").resolve("SKILL.md")));
        Assert.assertTrue("安装结果应带 warnings", row.containsKey("warnings"));
    }

    @Test
    public void shouldWarnButStillInstallThreatyZip() throws IOException {
        Map<String, Object> row = service.installZip(
                zipOf(Map.of("SKILL.md", THREATY_MARKDOWN)), "threaty.zip", false);

        Assert.assertTrue("上传来源只告警不阻断",
                Files.isRegularFile(skillRoot.resolve("threaty").resolve("SKILL.md")));
        Assert.assertTrue("安装结果应带 warnings", row.containsKey("warnings"));
    }

    @Test
    public void shouldSkipThreatScanWhenDisabled() {
        SkillRuntimeOptions options = SkillRuntimeOptions.builder()
                .enabled(true)
                .directories(List.of(skillRoot.toString()))
                .threatScanEnabled(false)
                .build();
        SkillRegistry registry = Mockito.mock(SkillRegistry.class);
        Mockito.when(registry.findSkill(Mockito.anyString())).thenReturn(Optional.empty());

        Map<String, Object> row = new SkillPackageService(options, registry)
                .installFromMarkdown(null, null, THREATY_MARKDOWN, false);

        Assert.assertFalse("关闭扫描后不应有 warnings", row.containsKey("warnings"));
    }

    private static byte[] zipOf(Map<String, String> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
