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
import java.util.Map;

/**
 * workspace_edit 对齐 cchaha Edit 的基本语义。
 */
public class WorkspaceEditToolTest {

    private Path workspaceRoot;
    private Path targetFile;
    private WorkspaceService workspaceService;
    private WorkspaceRuntimeOptions options;
    private AgentContext agentContext;
    private WorkspaceReadTool readTool;
    private WorkspaceEditTool editTool;

    @Before
    public void setUp() throws Exception {
        workspaceRoot = Files.createTempDirectory("reactor-workspace-edit");
        targetFile = workspaceRoot.resolve("demo.txt");
        Files.writeString(targetFile, "alpha\nbeta\ngamma\n", StandardCharsets.UTF_8);

        options = WorkspaceRuntimeOptions.builder()
                .enabled(true)
                .rootTemplate(workspaceRoot.toString())
                .maxReadChars(10000)
                .maxWriteChars(100000)
                .build();
        workspaceService = new WorkspaceService(options, new WorkspacePathGuard(), null);
        agentContext = AgentContext.builder()
                .requestId("req-edit-001")
                .sessionId("session-edit-001")
                .workspaceRoot(workspaceRoot.toString())
                .build();

        readTool = new WorkspaceReadTool(workspaceService, options);
        readTool.setAgentContext(agentContext);
        editTool = new WorkspaceEditTool(workspaceService, options);
        editTool.setAgentContext(agentContext);
    }

    @Test
    public void shouldFailEditWithoutPriorRead() throws Exception {
        String result = String.valueOf(editTool.execute(Map.of(
                "path", "demo.txt",
                "old_string", "beta",
                "new_string", "BETA"
        )));
        Assert.assertTrue(result.contains("workspace_read"));
        Assert.assertTrue(Files.readString(targetFile, StandardCharsets.UTF_8).contains("beta"));
    }

    @Test
    public void shouldEditAfterReadWhenUnique() throws Exception {
        readTool.execute(Map.of("path", "demo.txt"));
        String result = String.valueOf(editTool.execute(Map.of(
                "path", "demo.txt",
                "old_string", "beta",
                "new_string", "BETA"
        )));
        Assert.assertTrue(result.contains("已编辑文件") || result.contains("替换次数"));
        Assert.assertEquals("alpha\nBETA\ngamma\n", Files.readString(targetFile, StandardCharsets.UTF_8));
    }

    @Test
    public void shouldFailWhenNotUniqueUnlessReplaceAll() throws Exception {
        Files.writeString(targetFile, "x y x\n", StandardCharsets.UTF_8);
        readTool.execute(Map.of("path", "demo.txt"));

        String uniqueFail = String.valueOf(editTool.execute(Map.of(
                "path", "demo.txt",
                "old_string", "x",
                "new_string", "z"
        )));
        Assert.assertTrue(uniqueFail.contains("occurrences") || uniqueFail.contains("replace_all"));

        String replaceAll = String.valueOf(editTool.execute(Map.of(
                "path", "demo.txt",
                "old_string", "x",
                "new_string", "z",
                "replace_all", true
        )));
        Assert.assertTrue(replaceAll.contains("替换次数") || replaceAll.contains("已编辑"));
        Assert.assertEquals("z y z\n", Files.readString(targetFile, StandardCharsets.UTF_8));
    }

    @Test
    public void shouldFailWhenOldStringMissing() throws Exception {
        readTool.execute(Map.of("path", "demo.txt"));
        String result = String.valueOf(editTool.execute(Map.of(
                "path", "demo.txt",
                "old_string", "not-exist",
                "new_string", "x"
        )));
        Assert.assertTrue(result.contains("not found") || result.contains("old_string"));
    }
}
