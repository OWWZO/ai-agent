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
import org.wwz.ai.domain.agent.runtime.dto.CodeInterpreterResponse;
import org.wwz.ai.domain.agent.runtime.dto.FileRequest;
import org.wwz.ai.domain.agent.runtime.dto.FileResponse;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
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
 * workspace_write：本地写入 + 登记预览 URL（不 upload content）。
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
        WorkspaceService workspaceService = new WorkspaceService(options, new WorkspacePathGuard(), null, new org.wwz.ai.domain.agent.runtime.tool.skill.SkillVirtualPaths(org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeOptions.builder().enabled(false).build()));
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

        Mockito.when(fileArtifactPort.register(Mockito.eq("http://127.0.0.1:1601"), Mockito.any(FileRequest.class)))
                .thenReturn(FileResponse.builder()
                        .fileName("report.md")
                        .ossUrl("http://127.0.0.1:1601/v1/file_tool/download/session-write-001/report.md")
                        .domainUrl("http://127.0.0.1:1601/v1/file_tool/preview/session-write-001/report.md")
                        .fileSize(12)
                        .build());
    }

    @Test
    public void shouldWriteLocallyAndRegisterWithoutUpload() throws Exception {
        ToolResultPayload result = (ToolResultPayload) writeTool.execute(Map.of(
                "path", "out/report.md",
                "content", "hello sync"
        ));

        Assert.assertTrue(result.getLlmData() instanceof Map<?, ?>);
        Map<?, ?> resultData = (Map<?, ?>) result.getLlmData();
        Assert.assertEquals("out/report.md", resultData.get("path"));
        Assert.assertEquals(10, resultData.get("chars"));
        Assert.assertFalse(resultData.containsKey("registerNote"));
        Assert.assertTrue(Files.isRegularFile(workspaceRoot.resolve("out/report.md")));
        Assert.assertEquals("hello sync", Files.readString(workspaceRoot.resolve("out/report.md"), StandardCharsets.UTF_8));

        ArgumentCaptor<FileRequest> captor = ArgumentCaptor.forClass(FileRequest.class);
        Mockito.verify(fileArtifactPort).register(Mockito.eq("http://127.0.0.1:1601"), captor.capture());
        Mockito.verify(fileArtifactPort, Mockito.never()).upload(Mockito.anyString(), Mockito.any());
        Assert.assertEquals("out/report.md", captor.getValue().getFileName());
        Assert.assertNull(captor.getValue().getContent());
        Assert.assertTrue(captor.getValue().getLocalPath().replace('\\', '/').endsWith("out/report.md"));
        ArgumentCaptor<Map> eventCaptor = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(printer).send(Mockito.eq("file"), eventCaptor.capture(), Mockito.isNull());
        Map<?, ?> fileEvent = eventCaptor.getValue();
        Assert.assertEquals(Boolean.TRUE, fileEvent.get("fileListOnly"));
        Assert.assertEquals("out/report.md", fileEvent.get("relativePath"));
        Assert.assertEquals("session-write-001", fileEvent.get("sessionId"));
        Assert.assertEquals(1, ((java.util.List<?>) fileEvent.get("fileInfo")).size());
        CodeInterpreterResponse.FileInfo info =
                (CodeInterpreterResponse.FileInfo) ((java.util.List<?>) fileEvent.get("fileInfo")).get(0);
        Assert.assertEquals("out/report.md", info.getRelativePath());
        Assert.assertEquals("http://127.0.0.1:1601/v1/file_tool/preview/session-write-001/report.md",
                info.getDomainUrl());
        Assert.assertFalse(agentContext.getVisibleArtifactFiles().isEmpty());
    }
}
