package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.adapter.port.FileArtifactPort;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.dto.FileRequest;
import org.wwz.ai.domain.agent.runtime.dto.FileResponse;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspacePathGuard;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceRuntimeOptions;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceService;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceWriteTool;
import org.wwz.ai.test.domain.support.ReactorRuntimeTestSupport;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * workspace_write 本地写入 + 远端同步。
 */
public class WorkspaceWriteSyncTest {

    private Path workspaceRoot;
    private FileArtifactPort fileArtifactPort;
    private Printer printer;
    private WorkspaceWriteTool writeTool;
    private AgentContext agentContext;

    @Before
    public void setUp() throws Exception {
        workspaceRoot = Files.createTempDirectory("reactor-workspace-write-sync");
        WorkspaceRuntimeOptions options = WorkspaceRuntimeOptions.builder()
                .enabled(true)
                .rootTemplate(workspaceRoot.toString())
                .maxWriteChars(100000)
                .build();
        WorkspaceService workspaceService = new WorkspaceService(options, new WorkspacePathGuard(), null);
        writeTool = new WorkspaceWriteTool(workspaceService, options);

        fileArtifactPort = Mockito.mock(FileArtifactPort.class);
        printer = Mockito.mock(Printer.class);

        ReactorConfig reactorConfig = new ReactorConfig();
        ReflectionTestUtils.setField(reactorConfig, "codeInterpreterUrl", "http://127.0.0.1:1601");

        ReactorRuntimeDependencies base = ReactorRuntimeTestSupport.runtimeDependencies(reactorConfig);
        ReactorRuntimeDependencies deps = base.toBuilder()
                .fileArtifactPort(fileArtifactPort)
                .build();

        agentContext = AgentContext.builder()
                .requestId("req-write-001")
                .sessionId("session-write-001")
                .workspaceRoot(workspaceRoot.toString())
                .runtimeDependencies(deps)
                .printer(printer)
                .build();
        agentContext.bindCurrentToolArtifactSource(ToolArtifactSource.builder()
                .sessionId("session-write-001")
                .requestId("req-write-001")
                .toolCallId("call-write-1")
                .toolName("workspace_write")
                .build());
        writeTool.setAgentContext(agentContext);

        Mockito.when(fileArtifactPort.upload(Mockito.eq("http://127.0.0.1:1601"), Mockito.any(FileRequest.class)))
                .thenReturn(FileResponse.builder()
                        .fileName("report.md")
                        .ossUrl("https://file.example.com/report.md")
                        .domainUrl("https://file.example.com/preview/report.md")
                        .fileSize(12)
                        .build());
    }

    @Test
    public void shouldWriteLocallyAndSyncToFileService() throws Exception {
        String result = String.valueOf(writeTool.execute(Map.of(
                "path", "out/report.md",
                "content", "hello sync"
        )));

        Assert.assertTrue(result.contains("已写入文件"));
        Assert.assertTrue(result.contains("已同步文件服务"));
        Assert.assertTrue(Files.isRegularFile(workspaceRoot.resolve("out/report.md")));
        Assert.assertEquals("hello sync", Files.readString(workspaceRoot.resolve("out/report.md"), StandardCharsets.UTF_8));

        ArgumentCaptor<FileRequest> captor = ArgumentCaptor.forClass(FileRequest.class);
        Mockito.verify(fileArtifactPort).upload(Mockito.eq("http://127.0.0.1:1601"), captor.capture());
        Assert.assertEquals("report.md", captor.getValue().getFileName());
        Assert.assertEquals("hello sync", captor.getValue().getContent());
        Mockito.verify(printer).send(Mockito.eq("file"), Mockito.any(), Mockito.isNull());
        Assert.assertFalse(agentContext.getVisibleArtifactFiles().isEmpty());
    }
}
