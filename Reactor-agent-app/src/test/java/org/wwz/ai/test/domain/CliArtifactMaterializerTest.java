package org.wwz.ai.test.domain;

import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.wwz.ai.domain.agent.adapter.port.FileArtifactPort;
import org.wwz.ai.domain.agent.adapter.port.cli.CliArtifact;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.dto.FileRequest;
import org.wwz.ai.domain.agent.runtime.dto.FileResponse;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.tool.cli.CliArtifactMaterialization;
import org.wwz.ai.domain.agent.runtime.tool.cli.CliArtifactMaterializeCommand;
import org.wwz.ai.domain.agent.runtime.tool.cli.CliArtifactMaterializer;
import org.wwz.ai.domain.agent.runtime.tool.cli.OpenCliArgv;
import org.wwz.ai.test.domain.support.ReactorRuntimeTestSupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CliArtifactMaterializerTest {

    private Path workspace;
    private Path outside;
    private FileArtifactPort fileArtifactPort;
    private Printer printer;

    @Before
    public void setUp() throws IOException {
        workspace = Files.createTempDirectory("cli-artifact-ws");
        outside = Files.createTempDirectory("cli-artifact-outside");
        fileArtifactPort = Mockito.mock(FileArtifactPort.class);
        printer = Mockito.mock(Printer.class);
        Mockito.when(fileArtifactPort.materializeLocalFile(Mockito.anyString(), Mockito.any(FileRequest.class)))
                .thenAnswer(invocation -> {
                    FileRequest request = invocation.getArgument(1);
                    return FileResponse.builder()
                            .ossUrl("http://fs.test/download/" + request.getFileName())
                            .domainUrl("http://fs.test/preview/" + request.getFileName())
                            .fileSize(1024)
                            .fileName(request.getFileName())
                            .build();
                });
    }

    @After
    public void tearDown() {
        deleteRecursively(workspace);
        deleteRecursively(outside);
    }

    @Test
    public void shouldRegisterReportedFileWithServiceUrls() throws IOException {
        writeFile(workspace.resolve("note-1_1.jpg"), "image-bytes");
        AgentContext context = context();

        CliArtifactMaterialization result = materialize(context,
                List.of(CliArtifact.builder().path("note-1_1.jpg").size(11L).mime("image/jpeg").build()),
                null, null);

        Assert.assertEquals(1, result.getArtifacts().size());
        Assert.assertTrue(result.getFailures().isEmpty());
        var artifact = result.getArtifacts().get(0);
        Assert.assertEquals("note-1_1.jpg", artifact.getRelativePath());
        Assert.assertEquals("note-1_1.jpg", artifact.getFileName());
        Assert.assertEquals("http://fs.test/download/note-1_1.jpg", artifact.getDownloadUrl());
        Assert.assertEquals("http://fs.test/preview/note-1_1.jpg", artifact.getPreviewUrl());
        Assert.assertEquals(Long.valueOf(1024L), artifact.getFileSize());
        // registry 绑定：产物必须挂到当前 toolCallId
        Assert.assertEquals(1, context.getArtifactBindingsByToolCallId("call-1").size());
        Assert.assertEquals("http://fs.test/download/note-1_1.jpg",
                context.getArtifactBindingsByToolCallId("call-1").get(0).getFile().getOssUrl());
    }

    @Test
    public void shouldKeepNestedRelativePath() throws IOException {
        Path nested = workspace.resolve("xiaohongshu-downloads").resolve("note-1");
        Files.createDirectories(nested);
        writeFile(nested.resolve("note-1_1.jpg"), "bytes");
        AgentContext context = context();

        CliArtifactMaterialization result = materialize(context,
                List.of(CliArtifact.builder().path("xiaohongshu-downloads/note-1/note-1_1.jpg").build()),
                null, null);

        Assert.assertEquals(1, result.getArtifacts().size());
        Assert.assertEquals("xiaohongshu-downloads/note-1/note-1_1.jpg",
                result.getArtifacts().get(0).getRelativePath());
    }

    @Test
    public void shouldRejectAbsolutePathOutsideWorkspace() throws IOException {
        Path escaped = outside.resolve("escape.txt");
        writeFile(escaped, "x");
        AgentContext context = context();

        CliArtifactMaterialization result = materialize(context,
                List.of(CliArtifact.builder().path(escaped.toString()).build()),
                null, null);

        Assert.assertTrue(result.getArtifacts().isEmpty());
        Assert.assertEquals(1, result.getFailures().size());
        Assert.assertEquals("outside_workspace", result.getFailures().get(0).getReason());
        Assert.assertTrue(context.getVisibleArtifactBindings().isEmpty());
    }

    @Test
    public void shouldRejectParentTraversal() throws IOException {
        Path escaped = workspace.getParent().resolve("cli-artifact-escape.txt");
        writeFile(escaped, "x");
        AgentContext context = context();
        try {
            CliArtifactMaterialization result = materialize(context,
                    List.of(CliArtifact.builder().path("../cli-artifact-escape.txt").build()),
                    null, null);

            Assert.assertTrue(result.getArtifacts().isEmpty());
            Assert.assertEquals("outside_workspace", result.getFailures().get(0).getReason());
            Assert.assertTrue(context.getVisibleArtifactBindings().isEmpty());
        } finally {
            Files.deleteIfExists(escaped);
        }
    }

    @Test
    public void shouldReturnStructuredFailureWhenFileMissing() {
        AgentContext context = context();

        CliArtifactMaterialization result = materialize(context,
                List.of(CliArtifact.builder().path("missing.png").build()),
                null, null);

        Assert.assertTrue(result.getArtifacts().isEmpty());
        Assert.assertEquals("file_not_found", result.getFailures().get(0).getReason());
    }

    @Test
    public void shouldNotBindRegistryWhenFileServiceFails() throws Exception {
        writeFile(workspace.resolve("broken.jpg"), "x");
        Mockito.reset(fileArtifactPort);
        Mockito.when(fileArtifactPort.materializeLocalFile(Mockito.anyString(), Mockito.any(FileRequest.class)))
                .thenThrow(new IOException("file service down"));
        AgentContext context = context();

        CliArtifactMaterialization result = materialize(context,
                List.of(CliArtifact.builder().path("broken.jpg").build()),
                null, null);

        Assert.assertTrue(result.getArtifacts().isEmpty());
        Assert.assertEquals("artifact_register_failed", result.getFailures().get(0).getReason());
        Assert.assertTrue(context.getVisibleArtifactBindings().isEmpty());
    }

    @Test
    public void shouldKeepSuccessfulFilesWhenOneFails() throws IOException {
        writeFile(workspace.resolve("ok-1.jpg"), "a");
        writeFile(workspace.resolve("bad-2.jpg"), "b");
        Mockito.reset(fileArtifactPort);
        Mockito.when(fileArtifactPort.materializeLocalFile(Mockito.anyString(), Mockito.any(FileRequest.class)))
                .thenAnswer(invocation -> {
                    FileRequest request = invocation.getArgument(1);
                    if (StringUtils.contains(request.getFileName(), "bad")) {
                        return null;
                    }
                    return FileResponse.builder()
                            .ossUrl("http://fs.test/download/" + request.getFileName())
                            .domainUrl("http://fs.test/preview/" + request.getFileName())
                            .fileSize(1)
                            .build();
                });
        AgentContext context = context();

        CliArtifactMaterialization result = materialize(context, List.of(
                CliArtifact.builder().path("ok-1.jpg").build(),
                CliArtifact.builder().path("bad-2.jpg").build()), null, null);

        Assert.assertEquals(1, result.getArtifacts().size());
        Assert.assertEquals("ok-1.jpg", result.getArtifacts().get(0).getRelativePath());
        Assert.assertEquals(1, result.getFailures().size());
        Assert.assertEquals("artifact_register_failed", result.getFailures().get(0).getReason());
    }

    @Test
    public void shouldEnforceFileCountAndSizeLimits() throws IOException {
        writeFile(workspace.resolve("a.jpg"), "a");
        writeFile(workspace.resolve("b.jpg"), "b");
        writeFile(workspace.resolve("c.jpg"), "c");
        AgentContext context = context();

        CliArtifactMaterialization limited = CliArtifactMaterializer.materialize(
                CliArtifactMaterializeCommand.builder()
                        .agentContext(context)
                        .workspaceRoot(workspace.toString())
                        .reportedArtifacts(List.of(
                                CliArtifact.builder().path("a.jpg").build(),
                                CliArtifact.builder().path("b.jpg").build(),
                                CliArtifact.builder().path("c.jpg").build()))
                        .commandLabel("cli 产物")
                        .maxFiles(2)
                        .build());

        Assert.assertEquals(2, limited.getArtifacts().size());
        Assert.assertEquals(1, limited.getFailures().size());
        Assert.assertEquals("artifact_limit_exceeded", limited.getFailures().get(0).getReason());

        writeFile(workspace.resolve("big.bin"), "0123456789");
        AgentContext sizeContext = context();
        CliArtifactMaterialization oversized = CliArtifactMaterializer.materialize(
                CliArtifactMaterializeCommand.builder()
                        .agentContext(sizeContext)
                        .workspaceRoot(workspace.toString())
                        .reportedArtifacts(List.of(CliArtifact.builder().path("big.bin").build()))
                        .commandLabel("cli 产物")
                        .maxFileBytes(4L)
                        .build());
        Assert.assertTrue(oversized.getArtifacts().isEmpty());
        Assert.assertEquals("file_too_large", oversized.getFailures().get(0).getReason());
    }

    @Test
    public void shouldDiffOnlyDeclaredOutputRoot() throws IOException {
        // 快照必须在命令写文件之前取：这里模拟"执行前 workspace 是空的"
        Set<String> before = CliArtifactMaterializer.snapshotWorkspace(workspace.toString());
        writeFile(workspace.resolve("unrelated.txt"), "x");
        Path outputRoot = workspace.resolve("xhs");
        Files.createDirectories(outputRoot);
        writeFile(outputRoot.resolve("note.jpg"), "y");

        AgentContext wholeWorkspaceContext = context();
        CliArtifactMaterialization wholeWorkspace = CliArtifactMaterializer.materialize(
                CliArtifactMaterializeCommand.builder()
                        .agentContext(wholeWorkspaceContext)
                        .workspaceRoot(workspace.toString())
                        .commandLabel("cli 产物")
                        .baselineFiles(before)
                        .build());
        Assert.assertEquals(2, wholeWorkspace.getArtifacts().size());

        AgentContext scopedContext = context();
        CliArtifactMaterialization scoped = CliArtifactMaterializer.materialize(
                CliArtifactMaterializeCommand.builder()
                        .agentContext(scopedContext)
                        .workspaceRoot(workspace.toString())
                        .commandLabel("cli 产物")
                        .baselineFiles(before)
                        .outputRoot("xhs")
                        .build());
        Assert.assertEquals(1, scoped.getArtifacts().size());
        Assert.assertEquals("xhs/note.jpg", scoped.getArtifacts().get(0).getRelativePath());
    }

    @Test
    public void shouldDiffWorkspaceWhenCliReportsNothing() throws IOException {
        Set<String> before = CliArtifactMaterializer.snapshotWorkspace(workspace.toString());
        writeFile(workspace.resolve("fresh.png"), "new");
        AgentContext context = context();

        CliArtifactMaterialization result = materialize(context, List.of(), before, null);

        Assert.assertEquals(1, result.getArtifacts().size());
        Assert.assertEquals("fresh.png", result.getArtifacts().get(0).getRelativePath());
    }

    @Test
    public void shouldEmitSingleFileEventWithoutAbsolutePath() throws IOException {
        writeFile(workspace.resolve("note-1_1.jpg"), "image");
        AgentContext context = context();

        CliArtifactMaterialization result = materialize(context,
                List.of(CliArtifact.builder().path("note-1_1.jpg").build()), null, null);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(printer).send(Mockito.eq("file"), captor.capture(), Mockito.isNull());
        @SuppressWarnings("unchecked")
        Map<String, Object> event = (Map<String, Object>) captor.getValue();
        Assert.assertEquals("cli 产物", event.get("command"));
        Assert.assertEquals("session-1", event.get("sessionId"));
        Assert.assertEquals("call-1", event.get("toolCallId"));
        Assert.assertEquals("host_cli", event.get("toolName"));
        @SuppressWarnings("unchecked")
        List<org.wwz.ai.domain.agent.runtime.dto.CodeInterpreterResponse.FileInfo> fileInfo =
                (List<org.wwz.ai.domain.agent.runtime.dto.CodeInterpreterResponse.FileInfo>) event.get("fileInfo");
        Assert.assertEquals(1, fileInfo.size());
        Assert.assertEquals("note-1_1.jpg", fileInfo.get(0).getFileName());
        Assert.assertEquals("note-1_1.jpg", fileInfo.get(0).getRelativePath());
        Assert.assertEquals("http://fs.test/download/note-1_1.jpg", fileInfo.get(0).getOssUrl());
        Assert.assertEquals("http://fs.test/preview/note-1_1.jpg", fileInfo.get(0).getDomainUrl());
        Assert.assertFalse(String.valueOf(event).contains(workspace.toString()));
        Assert.assertFalse(String.valueOf(result.artifactMaps()).contains(workspace.toString()));
    }

    @Test
    public void shouldParseDeclaredOutputRootFromArgv() {
        Assert.assertEquals("./xhs", OpenCliArgv.outputRoot(
                List.of("xiaohongshu", "download", "https://x", "--output", "./xhs")));
        Assert.assertEquals("./xhs", OpenCliArgv.outputRoot(
                List.of("xiaohongshu", "download", "https://x", "--output=./xhs")));
        Assert.assertEquals("downloads/note", OpenCliArgv.outputRoot(
                List.of("bilibili", "download", "--output-dir", "downloads/note")));
        Assert.assertNull(OpenCliArgv.outputRoot(List.of("browser", "click", "12")));
        Assert.assertNull(OpenCliArgv.outputRoot(List.of("xiaohongshu", "feed", "--limit", "10")));
    }

    private CliArtifactMaterialization materialize(AgentContext context,
                                                   List<CliArtifact> reported,
                                                   Set<String> baseline,
                                                   String outputRoot) {
        return CliArtifactMaterializer.materialize(CliArtifactMaterializeCommand.builder()
                .agentContext(context)
                .workspaceRoot(workspace.toString())
                .reportedArtifacts(reported)
                .commandLabel("cli 产物")
                .baselineFiles(baseline)
                .outputRoot(outputRoot)
                .build());
    }

    private AgentContext context() {
        ReactorConfig reactorConfig = ReactorRuntimeTestSupport.reactorConfigWithFileService("http://127.0.0.1:1601");
        AgentContext context = AgentContext.builder()
                .requestId("req-1")
                .sessionId("session-1")
                .workspaceRoot(workspace.toString())
                .printer(printer)
                .runtimeDependencies(ReactorRuntimeTestSupport.runtimeDependencies(reactorConfig)
                        .toBuilder()
                        .fileArtifactPort(fileArtifactPort)
                        .build())
                .build();
        context.bindCurrentToolArtifactSource(ToolArtifactSource.builder()
                .sessionId("session-1")
                .requestId("req-1")
                .toolCallId("call-1")
                .toolName("host_cli")
                .build());
        return context;
    }

    private static void writeFile(Path path, String content) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }

    private static void deleteRecursively(Path root) {
        try (var walk = Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignore) {
                    // 临时目录清理失败不影响断言
                }
            });
        } catch (IOException ignore) {
            // ignore
        }
    }
}
