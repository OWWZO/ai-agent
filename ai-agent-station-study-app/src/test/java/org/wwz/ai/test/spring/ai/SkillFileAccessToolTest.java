package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.wwz.ai.domain.agent.runtime.tool.common.skill.GlobTool;
import org.wwz.ai.domain.agent.runtime.tool.common.skill.GrepTool;
import org.wwz.ai.domain.agent.runtime.tool.common.skill.ListDirectoryTool;
import org.wwz.ai.domain.agent.runtime.tool.common.skill.ReadTool;
import org.wwz.ai.domain.agent.runtime.tool.skill.DefaultSkillRegistry;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillMarkdownParser;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillPathGuard;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeOptions;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillScriptDiscoverer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * skill 文件访问工具测试。
 */
public class SkillFileAccessToolTest {

    private Path skillRoot;
    private Path metricsFile;
    private Path scriptsDirectory;

    @Before
    public void setUp() throws Exception {
        skillRoot = new ClassPathResource("skills/sql-analysis").getFile().toPath().toAbsolutePath().normalize();
        metricsFile = skillRoot.resolve("references/metrics.md");
        scriptsDirectory = skillRoot.resolve("scripts");
    }

    @Test
    public void shouldReadFileAndTruncateContent() {
        ReadTool readTool = new ReadTool(createRegistry(80), createOptions(80));

        String result = String.valueOf(readTool.execute(Map.of(
                "path", metricsFile.toString(),
                "start_line", 1,
                "line_count", 20
        )));

        Assert.assertTrue(result.contains("路径："));
        Assert.assertTrue(result.contains("[已截断"));
    }

    @Test
    public void shouldListDirectoryWithinSkillPath() {
        ListDirectoryTool listDirectoryTool = new ListDirectoryTool(createRegistry(500), createOptions(500));

        String result = String.valueOf(listDirectoryTool.execute(Map.of(
                "path", scriptsDirectory.toString(),
                "max_depth", 2
        )));

        Assert.assertTrue(result.contains("summarize.py"));
    }

    @Test
    public void shouldGlobMarkdownFilesWithinSkillPath() {
        GlobTool globTool = new GlobTool(createRegistry(500), createOptions(500));

        String result = String.valueOf(globTool.execute(Map.of(
                "path", skillRoot.toString(),
                "pattern", "references/**/*.md"
        )));

        Assert.assertTrue(result.contains("references/metrics.md"));
    }

    @Test
    public void shouldGrepKeywordWithinSkillPath() {
        GrepTool grepTool = new GrepTool(createRegistry(500), createOptions(500));

        String result = String.valueOf(grepTool.execute(Map.of(
                "path", skillRoot.toString(),
                "pattern", "gross_margin"
        )));

        Assert.assertTrue(result.contains("metrics.md"));
        Assert.assertTrue(result.contains("gross_margin"));
    }

    @Test
    public void shouldRejectPathsOutsideRegisteredSkillDirectories() throws Exception {
        Path outsideFile = Files.createTempFile("skill-tool-outside", ".md");
        Files.writeString(outsideFile, "outside", java.nio.charset.StandardCharsets.UTF_8);
        Path outsideDirectory = outsideFile.getParent();

        DefaultSkillRegistry skillRegistry = createRegistry(500);
        SkillRuntimeOptions options = createOptions(500);

        ReadTool readTool = new ReadTool(skillRegistry, options);
        ListDirectoryTool listDirectoryTool = new ListDirectoryTool(skillRegistry, options);
        GlobTool globTool = new GlobTool(skillRegistry, options);
        GrepTool grepTool = new GrepTool(skillRegistry, options);

        Assert.assertTrue(String.valueOf(readTool.execute(Map.of("path", outsideFile.toString())))
                .contains("outside registered skill directories"));
        Assert.assertTrue(String.valueOf(listDirectoryTool.execute(Map.of("path", outsideDirectory.toString())))
                .contains("outside registered skill directories"));
        Assert.assertTrue(String.valueOf(globTool.execute(Map.of(
                "path", outsideDirectory.toString(),
                "pattern", "**/*.md"
        ))).contains("outside registered skill directories"));
        Assert.assertTrue(String.valueOf(grepTool.execute(Map.of(
                "path", outsideFile.toString(),
                "pattern", "outside"
        ))).contains("outside registered skill directories"));
    }

    private DefaultSkillRegistry createRegistry(int maxReadChars) {
        SkillPathGuard skillPathGuard = new SkillPathGuard();
        DefaultSkillRegistry registry = new DefaultSkillRegistry(
                createOptions(maxReadChars),
                new SkillMarkdownParser(),
                new SkillScriptDiscoverer(skillPathGuard),
                skillPathGuard
        );
        registry.refresh();
        return registry;
    }

    private SkillRuntimeOptions createOptions(int maxReadChars) {
        return SkillRuntimeOptions.builder()
                .enabled(true)
                .directories(List.of(skillRoot.getParent().toString()))
                .maxReadChars(maxReadChars)
                .maxListEntries(50)
                .maxGlobResults(20)
                .maxGrepMatches(20)
                .build();
    }
}
