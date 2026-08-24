package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceGlobTool;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceGrepTool;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceListTool;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspacePathGuard;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceReadTool;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceRuntimeOptions;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceService;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceWriteTool;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 会话工作区文件工具测试。
 */
public class WorkspaceFileAccessToolTest {

    private Path workspaceRoot;
    private WorkspaceService workspaceService;
    private WorkspaceRuntimeOptions options;
    private AgentContext agentContext;

    @Before
    public void setUp() throws Exception {
        workspaceRoot = Files.createTempDirectory("reactor-workspace-tool-test");
        options = WorkspaceRuntimeOptions.builder()
                .enabled(true)
                .rootTemplate(workspaceRoot.toString())
                .maxReadChars(4000)
                .maxListEntries(50)
                .maxGlobResults(20)
                .maxGrepMatches(20)
                .maxWriteChars(10000)
                .build();
        workspaceService = new WorkspaceService(options, new WorkspacePathGuard(), null, new org.wwz.ai.domain.agent.runtime.tool.skill.SkillVirtualPaths(org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeOptions.builder().enabled(false).build()));
        agentContext = AgentContext.builder()
                .requestId("req-ws-001")
                .sessionId("session-ws-001")
                .workspaceRoot(workspaceRoot.toString())
                .build();

        Files.writeString(workspaceRoot.resolve("notes.md"), "hello workspace\nline2 gross_margin\n", StandardCharsets.UTF_8);
        Files.createDirectories(workspaceRoot.resolve("docs"));
        Files.writeString(workspaceRoot.resolve("docs/readme.md"), "# readme\n", StandardCharsets.UTF_8);
    }

    @Test
    public void shouldReadAndWriteWithinWorkspace() {
        WorkspaceReadTool readTool = new WorkspaceReadTool(workspaceService, options);
        readTool.setAgentContext(agentContext);
        WorkspaceWriteTool writeTool = new WorkspaceWriteTool(workspaceService, options);
        writeTool.setAgentContext(agentContext);

        String writeResult = String.valueOf(writeTool.execute(Map.of(
                "path", "out/report.md",
                "content", "generated content"
        )));
        Assert.assertTrue(writeResult.contains("已写入文件"));

        String readResult = String.valueOf(readTool.execute(Map.of(
                "path", "notes.md",
                "start_line", 1,
                "line_count", 10
        )));
        Assert.assertTrue(readResult.contains("1 | hello workspace"));
    }

    @Test
    public void shouldListGlobAndGrepWithinWorkspace() {
        WorkspaceListTool listTool = new WorkspaceListTool(workspaceService, options);
        listTool.setAgentContext(agentContext);
        WorkspaceGlobTool globTool = new WorkspaceGlobTool(workspaceService, options);
        globTool.setAgentContext(agentContext);
        WorkspaceGrepTool grepTool = new WorkspaceGrepTool(workspaceService, options);
        grepTool.setAgentContext(agentContext);

        Assert.assertTrue(String.valueOf(listTool.execute(Map.of("path", workspaceRoot.toString(), "max_depth", 2)))
                .contains("notes.md"));
        Assert.assertTrue(String.valueOf(globTool.execute(Map.of(
                "path", workspaceRoot.toString(),
                "pattern", "**/*.md"
        ))).contains("docs/readme.md"));
        Assert.assertTrue(String.valueOf(grepTool.execute(Map.of(
                "path", workspaceRoot.toString(),
                "pattern", "gross_margin"
        ))).contains("notes.md"));
    }

    @Test
    public void shouldRejectPathsOutsideWorkspace() throws Exception {
        Path outside = Files.createTempFile("outside-workspace", ".md");
        Files.writeString(outside, "secret", StandardCharsets.UTF_8);

        WorkspaceReadTool readTool = new WorkspaceReadTool(workspaceService, options);
        readTool.setAgentContext(agentContext);
        WorkspaceWriteTool writeTool = new WorkspaceWriteTool(workspaceService, options);
        writeTool.setAgentContext(agentContext);

        Assert.assertTrue(String.valueOf(readTool.execute(Map.of("path", outside.toString())))
                .contains("outside allowed roots"));
        Assert.assertTrue(String.valueOf(writeTool.execute(Map.of(
                "path", outside.toString(),
                "content", "x"
        ))).contains("outside allowed roots"));
    }
}
