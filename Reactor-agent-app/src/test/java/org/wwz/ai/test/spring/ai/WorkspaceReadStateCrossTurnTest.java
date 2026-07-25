package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceEditTool;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspacePathGuard;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceReadStateStore;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceReadTool;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceRuntimeOptions;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceService;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 跨轮：readState 元数据落盘后，新 AgentContext hydrate 可 stub 去重并允许 edit。
 */
public class WorkspaceReadStateCrossTurnTest {

    private Path workspaceRoot;
    private Path targetFile;
    private WorkspaceService workspaceService;
    private WorkspaceRuntimeOptions options;
    private WorkspaceReadStateStore store;

    @Before
    public void setUp() throws Exception {
        workspaceRoot = Files.createTempDirectory("reactor-ws-cross-turn");
        targetFile = workspaceRoot.resolve("demo.txt");
        Files.writeString(targetFile, "hello cross turn\n", StandardCharsets.UTF_8);

        options = WorkspaceRuntimeOptions.builder()
                .enabled(true)
                .rootTemplate(workspaceRoot.toString())
                .maxReadChars(10000)
                .maxWriteChars(100000)
                .build();
        workspaceService = new WorkspaceService(options, new WorkspacePathGuard(), null);
        store = new WorkspaceReadStateStore();
    }

    @Test
    public void shouldHydrateAndSkipRereadThenAllowEdit() throws Exception {
        AgentContext turn1 = AgentContext.builder()
                .requestId("req-1")
                .sessionId("session-x")
                .workspaceRoot(workspaceRoot.toString())
                .build();
        WorkspaceReadTool read1 = new WorkspaceReadTool(workspaceService, options);
        read1.setAgentContext(turn1);
        String first = String.valueOf(read1.execute(Map.of("path", "demo.txt")));
        Assert.assertTrue(first.contains("hello cross turn"));
        store.persist(turn1);

        // 模拟下一轮新请求
        AgentContext turn2 = AgentContext.builder()
                .requestId("req-2")
                .sessionId("session-x")
                .workspaceRoot(workspaceRoot.toString())
                .build();
        store.hydrate(turn2);
        Assert.assertTrue(turn2.hasWorkspaceFileBeenRead(targetFile.toAbsolutePath().normalize().toString()));

        WorkspaceReadTool read2 = new WorkspaceReadTool(workspaceService, options);
        read2.setAgentContext(turn2);
        String second = String.valueOf(read2.execute(Map.of("path", "demo.txt")));
        Assert.assertTrue(second.contains("File unchanged since last read"));

        WorkspaceEditTool edit = new WorkspaceEditTool(workspaceService, options);
        edit.setAgentContext(turn2);
        String editResult = String.valueOf(edit.execute(Map.of(
                "path", "demo.txt",
                "old_string", "hello cross turn",
                "new_string", "hello next turn"
        )));
        Assert.assertTrue(editResult.contains("已编辑") || editResult.contains("替换次数"));
        Assert.assertTrue(Files.readString(targetFile, StandardCharsets.UTF_8).contains("hello next turn"));
    }
}
