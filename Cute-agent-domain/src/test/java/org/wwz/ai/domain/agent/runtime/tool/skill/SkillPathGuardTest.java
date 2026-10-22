package org.wwz.ai.domain.agent.runtime.tool.skill;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class SkillPathGuardTest {

    private final SkillPathGuard guard = new SkillPathGuard();

    @Test
    public void shouldRejectTraversalAndAbsolutePaths() {
        Assert.assertTrue(guard.hasTraversal("../etc/passwd"));
        Assert.assertTrue(guard.hasTraversal("a/../../b"));
        Assert.assertTrue(guard.hasTraversal("/etc/passwd"));
        Assert.assertTrue(guard.hasTraversal("~/secret"));
        Assert.assertTrue(guard.hasTraversal("C:/windows/system32"));
        Assert.assertTrue(guard.hasTraversal("C:windows"));
        Assert.assertTrue(guard.hasTraversal("//server/share"));
        Assert.assertTrue(guard.hasTraversal("\\\\server\\share"));
    }

    @Test
    public void shouldRejectBlankOrNull() {
        Assert.assertTrue(guard.hasTraversal(null));
        Assert.assertTrue(guard.hasTraversal("   "));
    }

    @Test
    public void shouldAcceptNormalRelativePaths() {
        Assert.assertFalse(guard.hasTraversal("references/metrics.md"));
        Assert.assertFalse(guard.hasTraversal("scripts/run.py"));
        Assert.assertFalse(guard.hasTraversal("SKILL.md"));
    }

    @Test
    public void shouldReturnCandidateWhenInsideRoot() throws IOException {
        Path root = Files.createTempDirectory("guard-root-");
        Path candidate = root.resolve("references/metrics.md");

        Assert.assertEquals(candidate.toAbsolutePath().normalize(), guard.ensureUnderRoot(root, candidate));
    }

    @Test
    public void shouldThrowWhenCandidateOutsideRoot() throws IOException {
        Path root = Files.createTempDirectory("guard-root-");
        Path outside = Files.createTempDirectory("guard-outside-").resolve("x.md");

        Assert.assertThrows(SkillLoadException.class, () -> guard.ensureUnderRoot(root, outside));
    }

    @Test
    public void shouldThrowWhenLexicalCheckFails() throws IOException {
        Path root = Files.createTempDirectory("guard-root-");

        Assert.assertThrows(SkillLoadException.class,
                () -> guard.ensureUnderRoot(root, root.resolve("../../etc/passwd")));
    }
}
