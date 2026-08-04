package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceEditTool;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspacePathGuard;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceReadTool;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceRuntimeOptions;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceService;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

/**
 * 对齐 cchaha：同 range + mtime 未变时 read 返回 unchanged stub；
 * 外部修改后 edit 要求重读。
 */
public class WorkspaceReadDedupTest {

    private Path workspaceRoot;
    private Path targetFile;
    private AgentContext agentContext;
    private WorkspaceReadTool readTool;
    private WorkspaceEditTool editTool;

    @Before
    public void setUp() throws Exception {
        workspaceRoot = Files.createTempDirectory("reactor-ws-dedup");
        targetFile = workspaceRoot.resolve("demo.txt");
        Files.writeString(targetFile, "alpha\nbeta\n", StandardCharsets.UTF_8);

        WorkspaceRuntimeOptions options = WorkspaceRuntimeOptions.builder()
                .enabled(true)
                .rootTemplate(workspaceRoot.toString())
                .maxReadChars(10000)
                .maxWriteChars(100000)
                .build();
        WorkspaceService service = new WorkspaceService(options, new WorkspacePathGuard(), null);
        agentContext = AgentContext.builder()
                .requestId("req-dedup")
                .sessionId("session-dedup")
                .workspaceRoot(workspaceRoot.toString())
                .build();
        readTool = new WorkspaceReadTool(service, options);
        readTool.setAgentContext(agentContext);
        editTool = new WorkspaceEditTool(service, options);
        editTool.setAgentContext(agentContext);
    }

    @Test
    public void shouldReturnUnchangedStubOnSameRangeAndMtime() {
        String first = String.valueOf(readTool.execute(Map.of("path", "demo.txt", "start_line", 1, "line_count", 20)));
        Assert.assertTrue(first.contains("alpha"));

        String second = String.valueOf(readTool.execute(Map.of("path", "demo.txt", "start_line", 1, "line_count", 20)));
        Assert.assertTrue(second.contains("File unchanged since last read"));
        Assert.assertFalse(second.contains("1 | alpha"));
    }

    @Test
    public void shouldRereadWhenRangeDiffers() {
        readTool.execute(Map.of("path", "demo.txt", "start_line", 1, "line_count", 1));
        String second = String.valueOf(readTool.execute(Map.of("path", "demo.txt", "start_line", 1, "line_count", 20)));
        Assert.assertFalse(second.contains("File unchanged since last read"));
        Assert.assertTrue(second.contains("beta") || second.contains("alpha"));
    }

    @Test
    public void shouldRequireRereadWhenFileChangedBeforeEdit() throws Exception {
        readTool.execute(Map.of("path", "demo.txt"));
        // 模拟外部修改
        Thread.sleep(5);
        Files.writeString(targetFile, "alpha\nbeta\nchanged\n", StandardCharsets.UTF_8);

        String editResult = String.valueOf(editTool.execute(Map.of(
                "path", "demo.txt",
                "old_string", "beta",
                "new_string", "BETA"
        )));
        Assert.assertTrue(editResult.contains("modified since read") || editResult.contains("Read it again"));
    }

    @Test
    public void shouldReturnTextMetadataAndImageContentBlock() throws Exception {
        String text = String.valueOf(readTool.execute(Map.of("file_path", "demo.txt", "offset", 2, "limit", 1)));
        Assert.assertTrue(text.contains("\"type\":\"text\""));
        Assert.assertTrue(text.contains("\"startLine\":2"));
        Assert.assertTrue(text.contains("beta"));

        Path image = workspaceRoot.resolve("pixel.png");
        Files.write(image, Base64.getDecoder().decode("iVBORw0KGgo="));
        Object raw = readTool.execute(Map.of("path", "pixel.png"));
        Assert.assertTrue(raw instanceof org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload);
        org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload payload =
                (org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload) raw;
        Assert.assertNotNull(payload.getBase64Image());
        Assert.assertTrue(payload.getBase64Image().startsWith("data:image/png;base64,"));
        Assert.assertEquals("image/png", payload.getImageMimeType());
        String llmData = String.valueOf(payload.getLlmData());
        Assert.assertTrue(llmData.contains("type=image") || llmData.contains("\"type\":\"image\""));
        Assert.assertFalse("observation must not embed full image base64",
                llmData.contains("base64=") || llmData.contains("\"base64\""));
    }
}
