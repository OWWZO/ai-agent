package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeOptions;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillVirtualPaths;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspacePathGuard;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceReadTool;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceRuntimeOptions;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceService;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * workspace_read relative path under session cwd.
 */
public class WorkspaceReadToolRelativePathTest {

    @Test
    public void shouldReadRelativePathUnderWorkspaceRoot() throws Exception {
        Path workspaceRoot = Files.createTempDirectory("reactor-workspace-read-rel");
        try {
            Path file = workspaceRoot.resolve("note.txt");
            Files.writeString(file, "hello-rel\n", StandardCharsets.UTF_8);

            WorkspaceRuntimeOptions options = WorkspaceRuntimeOptions.builder()
                    .enabled(true)
                    .rootTemplate(workspaceRoot.toString())
                    .maxReadChars(10000)
                    .build();
            WorkspaceService service = new WorkspaceService(
                    options,
                    new WorkspacePathGuard(),
                    null,
                    new SkillVirtualPaths(SkillRuntimeOptions.builder().enabled(false).build()));
            WorkspaceReadTool readTool = new WorkspaceReadTool(service, options);
            readTool.setAgentContext(AgentContext.builder()
                    .requestId("req-rel")
                    .sessionId("sess-rel")
                    .workspaceRoot(workspaceRoot.toString())
                    .build());

            ToolResultPayload payload = (ToolResultPayload) readTool.execute(Map.of("path", "note.txt"));
            Assert.assertFalse(Boolean.TRUE.equals(payload.getFailed()));
            Assert.assertTrue(String.valueOf(payload.getLlmData()).contains("hello-rel")
                    || String.valueOf(payload).contains("hello-rel"));
        } finally {
            try (var walk = Files.walk(workspaceRoot)) {
                walk.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ignored) {
                    }
                });
            }
        }
    }
}
