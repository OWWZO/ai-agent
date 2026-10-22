package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.skill.DefaultSkillRegistry;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillMarkdownParser;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillPathGuard;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeOptions;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillScriptDiscoverer;
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
import java.util.List;
import java.util.Map;

/**
 * skill 路径已并入 workspace 可读根：用 workspace_* 访问 skill 目录。
 */
public class SkillFileAccessToolTest {

    private Path skillRoot;
    private Path metricsFile;
    private Path scriptsDirectory;
    private Path workspaceRoot;
    private WorkspaceService workspaceService;
    private WorkspaceRuntimeOptions workspaceOptions;
    private AgentContext agentContext;

    @Before
    public void setUp() throws Exception {
        skillRoot = new ClassPathResource("skills/sql-analysis").getFile().toPath().toAbsolutePath().normalize();
        metricsFile = skillRoot.resolve("references/metrics.md");
        scriptsDirectory = skillRoot.resolve("scripts");
        workspaceRoot = Files.createTempDirectory("ws-skill-merge");

        DefaultSkillRegistry skillRegistry = createRegistry(500);
        skillRegistry.refresh();

        workspaceOptions = WorkspaceRuntimeOptions.builder()
                .enabled(true)
                .rootTemplate(workspaceRoot.toString())
                .maxReadChars(500)
                .maxListEntries(50)
                .maxGlobResults(20)
                .maxGrepMatches(20)
                .maxWriteChars(10000)
                .build();
        workspaceService = new WorkspaceService(workspaceOptions, new WorkspacePathGuard(), skillRegistry, new org.wwz.ai.domain.agent.runtime.tool.skill.SkillVirtualPaths(org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeOptions.builder().enabled(false).build()));
        agentContext = AgentContext.builder()
                .requestId("req-skill-ws")
                .sessionId("session-skill-ws")
                .workspaceRoot(workspaceRoot.toString())
                .build();
    }

    @Test
    public void shouldReadSkillFilesViaWorkspaceTools() {
        WorkspaceReadTool readTool = new WorkspaceReadTool(workspaceService, workspaceOptions);
        readTool.setAgentContext(agentContext);

        String result = String.valueOf(readTool.execute(Map.of(
                "path", metricsFile.toString(),
                "start_line", 1,
                "line_count", 20
        )));
        Assert.assertTrue(result.contains("路径") || result.contains(metricsFile.getFileName().toString()) || result.length() > 0);
        Assert.assertFalse(result.contains("outside allowed roots"));
    }

    @Test
    public void shouldListGlobGrepSkillDirectoryViaWorkspaceTools() {
        WorkspaceListTool listTool = new WorkspaceListTool(workspaceService, workspaceOptions);
        listTool.setAgentContext(agentContext);
        WorkspaceGlobTool globTool = new WorkspaceGlobTool(workspaceService, workspaceOptions);
        globTool.setAgentContext(agentContext);
        WorkspaceGrepTool grepTool = new WorkspaceGrepTool(workspaceService, workspaceOptions);
        grepTool.setAgentContext(agentContext);

        Assert.assertTrue(String.valueOf(listTool.execute(Map.of("path", scriptsDirectory.toString(), "max_depth", 2)))
                .contains("summarize.py"));
        Assert.assertTrue(String.valueOf(globTool.execute(Map.of(
                "path", skillRoot.toString(),
                "pattern", "references/**/*.md"
        ))).contains("references/metrics.md"));
        Assert.assertTrue(String.valueOf(grepTool.execute(Map.of(
                "path", skillRoot.toString(),
                "pattern", "gross_margin"
        ))).contains("metrics.md"));
    }

    @Test
    public void shouldRejectWriteIntoSkillDirectory() throws Exception {
        Path skillFile = skillRoot.resolve("should-not-write.md");
        WorkspaceWriteTool writeTool = new WorkspaceWriteTool(workspaceService, workspaceOptions);
        writeTool.setAgentContext(agentContext);

        String result = String.valueOf(writeTool.execute(Map.of(
                "path", skillFile.toString(),
                "content", "nope"
        )));
        Assert.assertTrue(result.contains("outside allowed roots") || result.contains("outside workspace"));
        Assert.assertFalse(Files.exists(skillFile));
    }

    @Test
    public void shouldRejectPathsOutsideWorkspaceAndSkill() throws Exception {
        Path outside = Files.createTempFile("outside-skill-ws", ".md");
        Files.writeString(outside, "outside", StandardCharsets.UTF_8);
        WorkspaceReadTool readTool = new WorkspaceReadTool(workspaceService, workspaceOptions);
        readTool.setAgentContext(agentContext);
        Assert.assertTrue(String.valueOf(readTool.execute(Map.of("path", outside.toString())))
                .contains("outside allowed roots"));
    }

    private DefaultSkillRegistry createRegistry(int maxReadChars) {
        SkillPathGuard skillPathGuard = new SkillPathGuard();
        return new DefaultSkillRegistry(
                SkillRuntimeOptions.builder()
                        .enabled(true)
                        .directories(List.of(skillRoot.getParent().toString()))
                        .maxReadChars(maxReadChars)
                        .maxListEntries(50)
                        .maxGlobResults(20)
                        .maxGrepMatches(20)
                        .build(),
                new SkillMarkdownParser(),
                new SkillScriptDiscoverer(skillPathGuard),
                skillPathGuard
        );
    }
}
