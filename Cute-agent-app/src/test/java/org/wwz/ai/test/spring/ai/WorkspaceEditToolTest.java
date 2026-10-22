package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
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
 * workspace_edit 的基本语义。
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
        workspaceService = new WorkspaceService(options, new WorkspacePathGuard(), null, new org.wwz.ai.domain.agent.runtime.tool.skill.SkillVirtualPaths(org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeOptions.builder().enabled(false).build()));
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
        ToolResultPayload payload = (ToolResultPayload) editTool.execute(Map.of(
                "path", "demo.txt",
                "old_string", "beta",
                "new_string", "BETA"
        ));
        Assert.assertFalse(Boolean.TRUE.equals(payload.getFailed()));
        Assert.assertEquals(1, ((Map<?, ?>) payload.getLlmData()).get("replacements"));
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

        ToolResultPayload replaceAll = (ToolResultPayload) editTool.execute(Map.of(
                "path", "demo.txt",
                "old_string", "x",
                "new_string", "z",
                "replace_all", true
        ));
        Assert.assertFalse(Boolean.TRUE.equals(replaceAll.getFailed()));
        Assert.assertEquals(2, ((Map<?, ?>) replaceAll.getLlmData()).get("replacements"));
        Assert.assertEquals("z y z\n", Files.readString(targetFile, StandardCharsets.UTF_8));
    }

    @Test
    public void shouldFailWhenOldStringMissing() throws Exception {
        readTool.execute(Map.of("path", "demo.txt"));
        ToolResultPayload payload = (ToolResultPayload) editTool.execute(Map.of(
                "path", "demo.txt",
                "old_string", "not-exist",
                "new_string", "x"
        ));
        Assert.assertTrue(Boolean.TRUE.equals(payload.getFailed()));
        Assert.assertTrue(payload.getLlmData() instanceof Map<?, ?>);
        Map<?, ?> detail = (Map<?, ?>) payload.getLlmData();
        Assert.assertEquals("workspace_edit", detail.get("tool"));
        Assert.assertTrue(String.valueOf(detail.get("message")).contains("not found"));
    }

    /**
     * 返回里必须带上带真实行号与上下文的 unified diff —— 前端内联预览与右侧 PatchDiff
     * 都只吃这个字段；缺失时前端只能拿 old_string/new_string 做片段 LCS，
     * 既没有上下文也不知道改动落在第几行。
     */
    @Test
    public void shouldReturnUnifiedDiffWithRealLineNumbers() throws Exception {
        readTool.execute(Map.of("path", "demo.txt"));
        ToolResultPayload payload = (ToolResultPayload) editTool.execute(Map.of(
                "path", "demo.txt",
                "old_string", "beta",
                "new_string", "BETA"
        ));

        Map<?, ?> data = (Map<?, ?>) payload.getLlmData();
        Object diff = data.get("diff");
        Assert.assertTrue("diff 字段缺失", diff instanceof String);
        String patch = (String) diff;
        // 3 行文件、改第 2 行 → 上下文被截断到文件头尾
        Assert.assertTrue(patch, patch.contains("@@ -1,3 +1,3 @@"));
        Assert.assertTrue(patch, patch.contains("-beta\n+BETA\n"));
        Assert.assertTrue(patch, patch.contains(" alpha\n"));
        Assert.assertTrue(patch, patch.contains(" gamma\n"));
        Assert.assertTrue(patch, patch.startsWith("--- a/"));
    }

    @Test
    public void shouldEmitMultipleHunksForReplaceAll() throws Exception {
        StringBuilder content = new StringBuilder();
        for (int line = 1; line <= 20; line++) {
            content.append(line == 3 || line == 15 ? "TOKEN" : "L" + line).append('\n');
        }
        Files.writeString(targetFile, content.toString(), StandardCharsets.UTF_8);
        readTool.execute(Map.of("path", "demo.txt"));

        ToolResultPayload payload = (ToolResultPayload) editTool.execute(Map.of(
                "path", "demo.txt",
                "old_string", "TOKEN",
                "new_string", "TOKEN2",
                "replace_all", true
        ));

        Map<?, ?> data = (Map<?, ?>) payload.getLlmData();
        Assert.assertEquals(2, data.get("replacements"));
        String patch = String.valueOf(data.get("diff"));
        // 第 3 行与第 15 行相距 12 行 > 2 × context，拆成两个 hunk，行号是文件真实行号
        Assert.assertTrue(patch, patch.contains("@@ -1,6 +1,6 @@"));
        Assert.assertTrue(patch, patch.contains("@@ -12,7 +12,7 @@"));
    }
}
