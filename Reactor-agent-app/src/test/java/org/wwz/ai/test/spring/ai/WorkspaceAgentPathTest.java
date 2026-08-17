package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeOptions;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillVirtualPaths;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspacePathGuard;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceRuntimeOptions;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class WorkspaceAgentPathTest {

    @Test
    public void shouldMapWorkspaceAndSkillsToAgentVisiblePaths() throws Exception {
        Path lib = Files.createTempDirectory("skill-lib-");
        Path ws = Files.createTempDirectory("session-ws-");
        try {
            Path skillFile = lib.resolve("demo/SKILL.md");
            Files.createDirectories(skillFile.getParent());
            Files.writeString(skillFile, "x");
            Path note = ws.resolve("out/note.txt");
            Files.createDirectories(note.getParent());
            Files.writeString(note, "y");

            SkillRuntimeOptions options = SkillRuntimeOptions.builder()
                    .enabled(true)
                    .directories(List.of(lib.toString()))
                    .build();
            WorkspaceService service = new WorkspaceService(
                    WorkspaceRuntimeOptions.builder().enabled(true).build(),
                    new WorkspacePathGuard(),
                    null,
                    new SkillVirtualPaths(options));

            Assert.assertEquals("skills/demo/SKILL.md", service.toAgentVisiblePath(ws, skillFile));
            Assert.assertEquals("out/note.txt", service.toAgentVisiblePath(ws, note));
            Assert.assertEquals(".", service.toAgentVisiblePath(ws, ws));
            Assert.assertEquals("skills", service.toAgentVisiblePath(ws, lib));

            String redacted = WorkspaceService.redactHostPaths(
                    "failed under C:\\Users\\WWZ\\AppData\\Local\\Temp\\x and /home/u/proj");
            Assert.assertFalse(redacted.contains("Users"));
            Assert.assertFalse(redacted.contains("/home/"));
            Assert.assertTrue(redacted.contains("<host-path>"));
        } finally {
            delete(lib);
            delete(ws);
        }
    }

    private static void delete(Path root) throws Exception {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
            });
        }
    }
}
