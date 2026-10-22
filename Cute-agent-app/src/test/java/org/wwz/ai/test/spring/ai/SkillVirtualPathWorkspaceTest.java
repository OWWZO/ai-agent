package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeOptions;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillVirtualPaths;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspacePathGuard;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceRuntimeOptions;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceService;
import org.wwz.ai.domain.agent.runtime.tool.skill.DefaultSkillRegistry;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillMarkdownParser;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillPathGuard;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillScriptDiscoverer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class SkillVirtualPathWorkspaceTest {

    @Test
    public void workspaceWriteViaSkillsPrefixHitsLibrary() throws Exception {
        Path lib = Files.createTempDirectory("skill-lib-");
        Path sessionWs = Files.createTempDirectory("session-ws-");
        try {
            SkillRuntimeOptions options = SkillRuntimeOptions.builder()
                    .enabled(true)
                    .directories(List.of(lib.toString()))
                    .build();
            SkillPathGuard guard = new SkillPathGuard();
            DefaultSkillRegistry registry = new DefaultSkillRegistry(
                    options, new SkillMarkdownParser(), new SkillScriptDiscoverer(guard), guard);
            registry.refresh();
            SkillVirtualPaths virtualPaths = new SkillVirtualPaths(options);
            WorkspaceService workspaceService = new WorkspaceService(
                    WorkspaceRuntimeOptions.builder().enabled(true).build(),
                    new WorkspacePathGuard(),
                    registry,
                    virtualPaths);

            Path resolved = workspaceService.resolveWritablePath(sessionWs, "skills/new-skill/SKILL.md");
            Assert.assertTrue(resolved.startsWith(lib));
            Files.createDirectories(resolved.getParent());
            Files.writeString(resolved, "---\nname: new-skill\n---\n\nhi\n", StandardCharsets.UTF_8);
            Assert.assertTrue(Files.isRegularFile(lib.resolve("new-skill/SKILL.md")));
            // 会话工作区不应出现 skills 实体
            Assert.assertFalse(Files.exists(sessionWs.resolve("skills")));
        } finally {
            deleteRecursively(lib);
            deleteRecursively(sessionWs);
        }
    }

    private static void deleteRecursively(Path root) throws Exception {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                }
            });
        }
    }
}
