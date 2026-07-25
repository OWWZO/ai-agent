package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.wwz.ai.domain.agent.adapter.port.FileArtifactPort;
import org.wwz.ai.domain.agent.reactor.model.dto.FileInformation;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspacePathGuard;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceRuntimeOptions;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceService;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceSessionFileMaterializer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 会话附件落盘到 workspace 的测试。
 */
public class WorkspaceSessionFileMaterializerTest {

    private Path workspaceRoot;
    private FileArtifactPort fileArtifactPort;
    private WorkspaceSessionFileMaterializer materializer;
    private AgentContext agentContext;

    @Before
    public void setUp() throws Exception {
        workspaceRoot = Files.createTempDirectory("reactor-workspace-materialize");
        WorkspaceRuntimeOptions options = WorkspaceRuntimeOptions.builder()
                .enabled(true)
                .rootTemplate(workspaceRoot.toString())
                .build();
        WorkspaceService workspaceService = new WorkspaceService(options, new WorkspacePathGuard(), null);
        fileArtifactPort = Mockito.mock(FileArtifactPort.class);
        materializer = new WorkspaceSessionFileMaterializer(workspaceService, fileArtifactPort);

        List<File> productFiles = new ArrayList<>();
        productFiles.add(File.builder()
                .fileName("notes.md")
                .ossUrl("https://file.example.com/notes.md")
                .isInternalFile(false)
                .build());

        agentContext = AgentContext.builder()
                .requestId("req-mat-001")
                .sessionId("session-mat-001")
                .workspaceRoot(workspaceRoot.toString())
                .productFiles(productFiles)
                .build();
    }

    @Test
    public void shouldMaterializeSessionFilesIntoWorkspace() throws Exception {
        Mockito.when(fileArtifactPort.readText("https://file.example.com/notes.md", 60L))
                .thenReturn("# notes\nhello workspace");

        List<String> written = materializer.materialize(agentContext, List.of(
                FileInformation.builder()
                        .fileName("notes.md")
                        .ossUrl("https://file.example.com/notes.md")
                        .build()
        ));

        Assert.assertEquals(List.of("notes.md"), written);
        Path target = workspaceRoot.resolve("notes.md");
        Assert.assertTrue(Files.isRegularFile(target));
        Assert.assertTrue(Files.readString(target, StandardCharsets.UTF_8).contains("hello workspace"));
        Assert.assertTrue(agentContext.getProductFiles().get(0).getDescription().contains("workspace:notes.md"));
    }

    @Test
    public void shouldSkipWhenNoUrlAndRejectPathTraversalName() throws Exception {
        List<String> written = materializer.materialize(agentContext, List.of(
                FileInformation.builder().fileName("../secret.txt").build()
        ));
        Assert.assertTrue(written.isEmpty());
        Assert.assertFalse(Files.exists(workspaceRoot.resolve("secret.txt")));
    }
}
