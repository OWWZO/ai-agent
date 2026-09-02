package org.wwz.ai.test.domain.canvas;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.adapter.port.FileArtifactPort;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.CanvasPublishToolOutput;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.common.canvas.CanvasPublishTool;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspacePaths;
import org.wwz.ai.test.domain.support.ReactorRuntimeTestSupport;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CanvasPublishToolTest {

    private static final String SESSION_ID = "canvas-publish-direct-url-test";
    private static final String FILE_PATH = "pages/canvas page.html";

    private Path sessionRoot;

    @Before
    public void setUp() throws Exception {
        sessionRoot = WorkspacePaths.skillOutputSessionRoot(SESSION_ID);
        Files.createDirectories(sessionRoot.resolve("pages"));
        Files.writeString(
                sessionRoot.resolve(FILE_PATH),
                "<html><body>canvas</body></html>",
                StandardCharsets.UTF_8
        );
    }

    @After
    public void tearDown() throws Exception {
        Files.deleteIfExists(sessionRoot.resolve(FILE_PATH));
        Files.deleteIfExists(sessionRoot.resolve("pages"));
        Files.deleteIfExists(sessionRoot);
    }

    @Test
    public void shouldPublishExistingWorkspaceFileWithoutUploadOrRegister() throws Exception {
        FileArtifactPort fileArtifactPort = Mockito.mock(FileArtifactPort.class);
        AgentContext agentContext = createAgentContext(fileArtifactPort, new ArrayList<>());
        CanvasPublishTool tool = new CanvasPublishTool();
        tool.setAgentContext(agentContext);

        Map<?, ?> parameters = (Map<?, ?>) tool.toParams();
        Assert.assertEquals(List.of("html_path"), parameters.get("required"));
        Map<?, ?> properties = (Map<?, ?>) parameters.get("properties");
        Assert.assertFalse(properties.containsKey("html"));
        Assert.assertFalse(properties.containsKey("filename"));
        Assert.assertFalse(properties.containsKey("mode"));

        ToolResultPayload result = execute(agentContext);

        Assert.assertFalse(result.getFailed());
        Assert.assertTrue(result.getLlmData() instanceof Map<?, ?>);
        Map<?, ?> llmData = (Map<?, ?>) result.getLlmData();
        Assert.assertEquals("canvas page.html", llmData.get("fileName"));
        Assert.assertEquals(FILE_PATH, llmData.get("relativePath"));
        Assert.assertEquals("html", ((CanvasPublishToolOutput) result.getStructuredOutput()).getMode());
        Assert.assertEquals(
                "http://127.0.0.1:1601/v1/file_tool/preview/" + SESSION_ID + "/pages/canvas%20page.html",
                ((CanvasPublishToolOutput) result.getStructuredOutput()).getPreviewUrl()
        );
        Assert.assertEquals(
                "http://127.0.0.1:1601/v1/file_tool/download/" + SESSION_ID + "/pages/canvas%20page.html",
                ((CanvasPublishToolOutput) result.getStructuredOutput()).getDownloadUrl()
        );
        Mockito.verify(fileArtifactPort, Mockito.never()).upload(Mockito.anyString(), Mockito.any());
        Mockito.verify(fileArtifactPort, Mockito.never()).register(Mockito.anyString(), Mockito.any());
        Assert.assertTrue(agentContext.getVisibleArtifactFiles().isEmpty());
    }

    @Test
    public void shouldRejectMissingHtmlPathWithoutFileServiceCalls() {
        FileArtifactPort fileArtifactPort = Mockito.mock(FileArtifactPort.class);
        AgentContext agentContext = createAgentContext(fileArtifactPort, new ArrayList<>());

        ToolResultPayload result = execute(agentContext, Map.of(
                "title", "Canvas",
                "html_path", "missing.html",
                "open_in_panel", false
        ));

        Assert.assertTrue(result.getFailed());
        Mockito.verifyNoInteractions(fileArtifactPort);
    }

    @Test
    public void shouldRejectInlineHtmlInput() {
        FileArtifactPort fileArtifactPort = Mockito.mock(FileArtifactPort.class);
        AgentContext agentContext = createAgentContext(fileArtifactPort, new ArrayList<>());

        ToolResultPayload result = execute(agentContext, Map.of(
                "title", "Canvas",
                "html", "<p>inline</p>",
                "open_in_panel", false
        ));

        Assert.assertTrue(result.getFailed());
        Mockito.verifyNoInteractions(fileArtifactPort);
    }

    private AgentContext createAgentContext(FileArtifactPort fileArtifactPort, List<File> productFiles) {
        ReactorConfig reactorConfig = new ReactorConfig();
        ReflectionTestUtils.setField(reactorConfig, "codeInterpreterUrl", "http://127.0.0.1:1601");
        ReactorRuntimeDependencies base = ReactorRuntimeTestSupport.runtimeDependencies(reactorConfig);
        ReactorRuntimeDependencies dependencies = base.toBuilder()
                .fileArtifactPort(fileArtifactPort)
                .build();
        AgentContext context = AgentContext.builder()
                .requestId("request-canvas-publish-direct-url")
                .sessionId(SESSION_ID)
                .runtimeDependencies(dependencies)
                .productFiles(productFiles)
                .build();
        context.bindCurrentToolArtifactSource(ToolArtifactSource.builder()
                .sessionId(SESSION_ID)
                .requestId("request-canvas-publish-direct-url")
                .toolCallId("call-canvas-publish-direct-url")
                .toolName("canvas_publish")
                .build());
        return context;
    }

    private ToolResultPayload execute(AgentContext agentContext) {
        return execute(agentContext, Map.of(
                "title", "Canvas",
                "html_path", FILE_PATH,
                "open_in_panel", false
        ));
    }

    private ToolResultPayload execute(AgentContext agentContext, Map<String, Object> input) {
        CanvasPublishTool tool = new CanvasPublishTool();
        tool.setAgentContext(agentContext);
        return (ToolResultPayload) tool.execute(input);
    }
}
