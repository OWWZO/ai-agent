package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.tool.skill.DefaultSkillRegistry;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillDescriptor;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillMarkdownParser;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillPathGuard;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeOptions;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillScriptDiscoverer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 扫描发现：递归开关、忽略目录剪枝、support 目录剪枝，以及 TTL + 签名缓存。
 */
public class SkillRecursiveScanTest {

    private Path root;

    @Before
    public void setUp() throws IOException {
        root = Files.createTempDirectory("skill-scan-");
        writeSkill(root.resolve("plain-skill"), "plain-skill");
        writeSkill(root.resolve("category-a").resolve("nested-skill"), "nested-skill");
        writeSkill(root.resolve("category-a").resolve(".git").resolve("fake-skill"), "fake-skill");
        writeSkill(root.resolve("node_modules").resolve("pkg"), "pkg-skill");
        writeSkill(root.resolve("category-a").resolve("nested-skill").resolve("references").resolve("archived"),
                "archived-skill");
    }

    private static void writeSkill(Path skillDir, String name) throws IOException {
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"),
                "---\nname: " + name + "\ndescription: " + name + " demo\n---\n\n# " + name + "\n",
                StandardCharsets.UTF_8);
    }

    private DefaultSkillRegistry registry(boolean recursive, int ttlSeconds) {
        SkillPathGuard guard = new SkillPathGuard();
        return new DefaultSkillRegistry(
                SkillRuntimeOptions.builder()
                        .enabled(true)
                        .directories(List.of(root.toString()))
                        .recursiveScan(recursive)
                        .scanCacheTtlSeconds(ttlSeconds)
                        .build(),
                new SkillMarkdownParser(),
                new SkillScriptDiscoverer(guard),
                guard);
    }

    private static Set<String> names(DefaultSkillRegistry registry) {
        return registry.list().stream().map(SkillDescriptor::getName).collect(Collectors.toSet());
    }

    @Test
    public void shouldOnlyScanOneLevelByDefault() {
        DefaultSkillRegistry registry = registry(false, 0);
        registry.refresh();

        Assert.assertEquals(Set.of("plain-skill"), names(registry));
    }

    @Test
    public void shouldScanNestedDirectoriesWhenRecursive() {
        DefaultSkillRegistry registry = registry(true, 0);
        registry.refresh();

        Assert.assertEquals(Set.of("plain-skill", "nested-skill"), names(registry));
    }

    @Test
    public void shouldPruneExcludedAndSupportDirectories() {
        DefaultSkillRegistry registry = registry(true, 0);
        registry.refresh();

        Set<String> found = names(registry);
        Assert.assertFalse("应跳过 .git", found.contains("fake-skill"));
        Assert.assertFalse("应跳过 node_modules", found.contains("pkg-skill"));
        Assert.assertFalse("应跳过 references 下的归档 skill", found.contains("archived-skill"));
    }

    @Test
    public void shouldReuseCachedSnapshotWithinTtl() {
        DefaultSkillRegistry registry = registry(false, 60);
        registry.refresh();
        String versionAfterFirst = registry.version();

        registry.refreshIfStale();

        Assert.assertEquals("TTL 内且签名未变时应命中缓存", versionAfterFirst, registry.version());
    }

    @Test
    public void shouldForceRescanOnRefresh() {
        DefaultSkillRegistry registry = registry(false, 60);
        registry.refresh();
        String versionAfterFirst = registry.version();

        registry.refresh();

        Assert.assertNotEquals(versionAfterFirst, registry.version());
    }

    @Test
    public void shouldBypassCacheWhenTtlIsZero() {
        DefaultSkillRegistry registry = registry(false, 0);
        registry.refresh();
        String versionAfterFirst = registry.version();

        registry.refreshIfStale();

        Assert.assertNotEquals("ttl<=0 表示每次强制扫描", versionAfterFirst, registry.version());
    }

    @Test
    public void shouldPickUpNewSkillOnStaleRefresh() throws IOException {
        DefaultSkillRegistry registry = registry(false, 60);
        registry.refresh();
        Assert.assertEquals(Set.of("plain-skill"), names(registry));

        writeSkill(root.resolve("second-skill"), "second-skill");
        registry.refreshIfStale();

        Assert.assertEquals(Set.of("plain-skill", "second-skill"), names(registry));
    }
}
