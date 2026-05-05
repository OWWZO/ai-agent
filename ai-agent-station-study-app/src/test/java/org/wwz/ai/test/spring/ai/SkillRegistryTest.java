package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.wwz.ai.domain.agent.runtime.tool.skill.DefaultSkillRegistry;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillDefinition;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillLoadException;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillMarkdownParser;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillPathGuard;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeOptions;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillScriptDiscoverer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Skill 注册中心相关测试。
 */
public class SkillRegistryTest {

    @Test
    public void shouldLoadValidSkillFromFixtureDirectory() throws Exception {
        DefaultSkillRegistry skillRegistry = createRegistry(true, fixtureSkillsRoot().toString());

        skillRegistry.refresh();

        Assert.assertEquals(1, skillRegistry.listSkills().size());
        SkillDefinition skillDefinition = skillRegistry.getRequiredSkill("sql-analysis");
        Assert.assertEquals("sql-analysis", skillDefinition.getName());
        Assert.assertTrue(skillDefinition.getBasePath().isAbsolute());
        Assert.assertTrue(skillDefinition.getScripts().containsKey("summarize"));
        Assert.assertEquals("python", skillDefinition.getScripts().get("summarize").getRuntime());
    }

    @Test
    public void shouldSkipSkillWithoutFrontMatter() throws Exception {
        Path rootDirectory = Files.createTempDirectory("skill-registry-missing-frontmatter");
        Path brokenSkillDirectory = rootDirectory.resolve("broken-skill");
        Files.createDirectories(brokenSkillDirectory);
        Files.writeString(brokenSkillDirectory.resolve("SKILL.md"), "# Broken Skill", StandardCharsets.UTF_8);

        DefaultSkillRegistry skillRegistry = createRegistry(true, rootDirectory.toString());
        skillRegistry.refresh();

        Assert.assertTrue(skillRegistry.listSkills().isEmpty());
    }

    @Test
    public void shouldHandleEmptyDirectoryGracefully() throws Exception {
        Path rootDirectory = Files.createTempDirectory("skill-registry-empty");
        DefaultSkillRegistry skillRegistry = createRegistry(true, rootDirectory.toString());

        skillRegistry.refresh();

        Assert.assertTrue(skillRegistry.listSkills().isEmpty());
        Assert.assertTrue(skillRegistry.isEnabled());
    }

    @Test(expected = SkillLoadException.class)
    public void shouldFailWhenSkillNamesDuplicate() throws Exception {
        Path rootDirectory = Files.createTempDirectory("skill-registry-duplicate");
        createSkillDirectory(rootDirectory.resolve("skill-a"), "duplicate-skill", "说明 A");
        createSkillDirectory(rootDirectory.resolve("skill-b"), "duplicate-skill", "说明 B");

        DefaultSkillRegistry skillRegistry = createRegistry(true, rootDirectory.toString());
        skillRegistry.refresh();
    }

    private DefaultSkillRegistry createRegistry(boolean enabled, String... directories) {
        SkillPathGuard skillPathGuard = new SkillPathGuard();
        return new DefaultSkillRegistry(
                SkillRuntimeOptions.builder()
                        .enabled(enabled)
                        .directories(List.of(directories))
                        .build(),
                new SkillMarkdownParser(),
                new SkillScriptDiscoverer(skillPathGuard),
                skillPathGuard
        );
    }

    private Path fixtureSkillsRoot() throws Exception {
        return new ClassPathResource("skills").getFile().toPath();
    }

    private void createSkillDirectory(Path skillDirectory, String skillName, String description) throws Exception {
        Files.createDirectories(skillDirectory);
        String markdown = """
                ---
                name: %s
                description: %s
                ---

                # Demo Skill
                """.formatted(skillName, description);
        Files.writeString(skillDirectory.resolve("SKILL.md"), markdown, StandardCharsets.UTF_8);
    }
}
