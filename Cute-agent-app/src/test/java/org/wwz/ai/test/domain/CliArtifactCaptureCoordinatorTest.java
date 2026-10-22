package org.wwz.ai.test.domain;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.tool.cli.CliArtifactCaptureCoordinator;
import org.wwz.ai.domain.agent.runtime.tool.cli.CliArtifactCaptureLease;
import org.wwz.ai.domain.agent.runtime.tool.cli.CliArtifactCapturePlan;
import org.wwz.ai.domain.agent.runtime.tool.cli.OpenCliArgv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class CliArtifactCaptureCoordinatorTest {

    private Path workspace;

    @Before
    public void setUp() throws IOException {
        workspace = Files.createTempDirectory("cli-capture-plan");
    }

    @After
    public void tearDown() throws IOException {
        if (workspace == null) {
            return;
        }
        try (var walk = Files.walk(workspace)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignore) {
                    // temporary directory cleanup is best effort
                }
            });
        }
    }

    @Test
    public void shouldInjectUniqueOutputRootForDownloadWithoutOutput() {
        List<String> args = List.of("node", "main.js", "xiaohongshu", "download", "<signed-note-url>");

        CliArtifactCapturePlan first = CliArtifactCaptureCoordinator.plan(
                "browser", args, args, workspace.toString(), "call-1", "request-1");
        CliArtifactCapturePlan second = CliArtifactCaptureCoordinator.plan(
                "browser", args, args, workspace.toString(), "call-1", "request-1");

        Assert.assertTrue(first.isArtifactCommand());
        Assert.assertTrue(first.isValid());
        Assert.assertTrue(first.isOutputRootInjected());
        Assert.assertTrue(first.getCaptureRoot().matches("\\.cute-artifacts/browser/[A-Za-z0-9_-]+"));
        Assert.assertNotEquals(first.getCaptureRoot(), second.getCaptureRoot());
        Assert.assertEquals("--output", first.getRewrittenArgs().get(first.getRewrittenArgs().size() - 2));
        Assert.assertEquals(first.getCaptureRoot(), first.getRewrittenArgs().get(first.getRewrittenArgs().size() - 1));
        Assert.assertFalse(first.getRewrittenArgs().toString().contains(workspace.toString()));
    }

    @Test
    public void shouldNormalizeAndReplaceEverySupportedOutputOption() {
        Assert.assertEquals("downloads/note", OpenCliArgv.normalizeOutputRoot("./downloads\\note"));
        for (String option : List.of("--output", "--output-dir", "--out", "--dir")) {
            List<String> rewritten = OpenCliArgv.rewriteOutputRoot(
                    List.of("xiaohongshu", "download", "<signed-note-url>", option, "./old"), "new/root");
            Assert.assertEquals(option, rewritten.get(3));
            Assert.assertEquals("new/root", rewritten.get(4));
        }
        Assert.assertEquals("new/root", OpenCliArgv.rewriteOutputRoot(
                List.of("xiaohongshu", "download", "<signed-note-url>", "--output=./old"), "new/root").get(3)
                .substring("--output=".length()));
    }

    @Test
    public void shouldRejectUnsafeOutputRootsBeforeExecution() {
        for (String unsafe : List.of("../escape", "foo/../../escape", "/tmp/output", "C:\\tmp\\output", "\\\\server\\share")) {
            CliArtifactCapturePlan plan = CliArtifactCaptureCoordinator.plan(
                    "browser",
                    List.of("xiaohongshu", "download", "<signed-note-url>", "--output", unsafe),
                    List.of("xiaohongshu", "download", "<signed-note-url>", "--output", unsafe),
                    workspace.toString(), "call-unsafe", "request-unsafe");
            Assert.assertFalse(unsafe, plan.isValid());
            Assert.assertEquals(CliArtifactCaptureCoordinator.OUTPUT_ROOT_INVALID, plan.getErrorCode());
        }
    }

    @Test
    public void shouldSerializeSameRootButAllowDifferentRoots() throws Exception {
        List<String> sameArgs = List.of("xiaohongshu", "download", "<signed-note-url>", "--output", "./shared");
        CliArtifactCapturePlan firstPlan = CliArtifactCaptureCoordinator.plan(
                "browser", sameArgs, sameArgs, workspace.toString(), "call-a", "request-a");
        CliArtifactCapturePlan secondPlan = CliArtifactCaptureCoordinator.plan(
                "browser", sameArgs, sameArgs, workspace.toString(), "call-b", "request-b");
        List<String> otherArgs = List.of("xiaohongshu", "download", "<signed-note-url>", "--output", "./other");
        CliArtifactCapturePlan otherPlan = CliArtifactCaptureCoordinator.plan(
                "browser", otherArgs, otherArgs, workspace.toString(), "call-c", "request-c");

        CliArtifactCaptureLease first = CliArtifactCaptureCoordinator.acquire(firstPlan);
        CountDownLatch secondAcquired = new CountDownLatch(1);
        Thread waiting = new Thread(() -> {
            try (CliArtifactCaptureLease ignored = CliArtifactCaptureCoordinator.acquire(secondPlan)) {
                secondAcquired.countDown();
            }
        });
        waiting.start();
        Assert.assertFalse(secondAcquired.await(150, TimeUnit.MILLISECONDS));

        try (CliArtifactCaptureLease ignored = CliArtifactCaptureCoordinator.acquire(otherPlan)) {
            Assert.assertTrue(Files.isDirectory(Path.of(otherPlan.getCaptureRootAbsolute())));
        } finally {
            first.close();
        }
        waiting.join(2_000L);
        Assert.assertTrue(secondAcquired.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void shouldNotClassifyBrowserPrimitivesAsDownloads() {
        CliArtifactCapturePlan plan = CliArtifactCaptureCoordinator.plan(
                "agent_browser",
                List.of("main.js", "browser", "state"),
                List.of("main.js", "browser", "state"),
                workspace.toString(), "call-read", "request-read");

        Assert.assertTrue(plan.isValid());
        Assert.assertFalse(plan.isArtifactCommand());
        Assert.assertEquals(List.of("main.js", "browser", "state"), plan.getRewrittenArgs());
    }
}
