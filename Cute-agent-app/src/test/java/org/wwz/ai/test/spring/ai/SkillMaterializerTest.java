package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillMaterializer;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeLayout;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeOptions;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillVirtualPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class SkillMaterializerTest {

    @Test
    public void shouldMaterializeToSandboxAndSyncBackOnlySkills() throws Exception {
        Path lib = Files.createTempDirectory("skill-lib-");
        Path sandbox = Files.createTempDirectory("skill-sandbox-");
        try {
            Path skill = lib.resolve("demo");
            Files.createDirectories(skill.resolve("scripts"));
            Files.writeString(skill.resolve("SKILL.md"), "body", StandardCharsets.UTF_8);
            Files.writeString(skill.resolve("scripts/run.py"), "print(1)", StandardCharsets.UTF_8);

            SkillRuntimeOptions options = SkillRuntimeOptions.builder()
                    .enabled(true)
                    .directories(List.of(lib.toString()))
                    .build();
            SkillVirtualPaths virtualPaths = new SkillVirtualPaths(options);
            SkillMaterializer materializer = new SkillMaterializer(
                    virtualPaths, new SkillRuntimeLayout(options), options);

            List<String> names = materializer.materializeForSandbox(sandbox, null);
            Assert.assertEquals(List.of("demo"), names);
            Path sandboxScript = sandbox.resolve("skills/demo/scripts/run.py");
            Assert.assertTrue(Files.isRegularFile(sandboxScript));

            Files.writeString(sandboxScript, "print(2)\n", StandardCharsets.UTF_8);
            // 沙箱其它文件不应影响库
            Files.writeString(sandbox.resolve("noise.txt"), "x", StandardCharsets.UTF_8);

            List<String> synced = materializer.syncBackSkillsOnly(sandbox);
            Assert.assertEquals(List.of("demo"), synced);
            Assert.assertEquals("print(2)\n",
                    Files.readString(lib.resolve("demo/scripts/run.py"), StandardCharsets.UTF_8));
            Assert.assertFalse(Files.exists(lib.resolve("noise.txt")));

            materializer.cleanupSandboxSkills(sandbox);
            Assert.assertFalse(Files.exists(sandbox.resolve("skills")));
        } finally {
            deleteRecursively(lib);
            deleteRecursively(sandbox);
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
