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
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentContextFactory;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentRegistry;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;

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
        workspaceService = new WorkspaceService(options, new WorkspacePathGuard(), null, new org.wwz.ai.domain.agent.runtime.tool.skill.SkillVirtualPaths(org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeOptions.builder().enabled(false).build()));
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
        Assert.assertTrue("edit should succeed: " + editResult,
                editResult.contains("replacements") || editResult.contains("已编辑") || editResult.contains("替换次数"));
        Assert.assertFalse(editResult.contains("File has been modified") || editResult.contains("must use workspace_read"));
        Assert.assertTrue(Files.readString(targetFile, StandardCharsets.UTF_8).contains("hello next turn"));
    }

    @Test
    public void shouldAllowFullRereadAfterCompactionClearsReadState() throws Exception {
        AgentContext turn1 = AgentContext.builder()
                .requestId("req-compact-1")
                .sessionId("session-x")
                .workspaceRoot(workspaceRoot.toString())
                .build();
        WorkspaceReadTool read1 = new WorkspaceReadTool(workspaceService, options);
        read1.setAgentContext(turn1);
        Assert.assertTrue(String.valueOf(read1.execute(Map.of("path", "demo.txt"))).contains("hello cross turn"));
        store.persist(turn1);

        // 模拟压缩成功：清内存 + 落盘（cc-haha clear readFileState）
        store.clear(turn1);
        Assert.assertFalse(turn1.hasWorkspaceFileBeenRead(targetFile.toAbsolutePath().normalize().toString()));

        AgentContext turn2 = AgentContext.builder()
                .requestId("req-compact-2")
                .sessionId("session-x")
                .workspaceRoot(workspaceRoot.toString())
                .build();
        store.hydrate(turn2);
        Assert.assertFalse(turn2.hasWorkspaceFileBeenRead(targetFile.toAbsolutePath().normalize().toString()));

        WorkspaceReadTool read2 = new WorkspaceReadTool(workspaceService, options);
        read2.setAgentContext(turn2);
        String second = String.valueOf(read2.execute(Map.of("path", "demo.txt")));
        Assert.assertFalse(second.contains("File unchanged since last read"));
        Assert.assertTrue(second.contains("hello cross turn"));
    }

    @Test
    public void persistEmptySnapshotDeletesStaleReadStateFile() throws Exception {
        AgentContext turn1 = AgentContext.builder()
                .requestId("req-empty-1")
                .sessionId("session-x")
                .workspaceRoot(workspaceRoot.toString())
                .build();
        WorkspaceReadTool read1 = new WorkspaceReadTool(workspaceService, options);
        read1.setAgentContext(turn1);
        read1.execute(Map.of("path", "demo.txt"));
        store.persist(turn1);
        Path storeFile = workspaceRoot.resolve(".reactor/read-state.json");
        Assert.assertTrue(Files.isRegularFile(storeFile));

        turn1.clearWorkspaceReadState();
        store.persist(turn1);
        Assert.assertFalse(Files.isRegularFile(storeFile));
    }

    @Test
    public void shouldNotHydrateMainReadStateIntoSubAgent() throws Exception {
        AgentContext parent = AgentContext.builder()
                .requestId("req-parent")
                .sessionId("session-x")
                .workspaceRoot(workspaceRoot.toString())
                .build();
        WorkspaceReadTool parentRead = new WorkspaceReadTool(workspaceService, options);
        parentRead.setAgentContext(parent);
        String first = String.valueOf(parentRead.execute(Map.of("path", "demo.txt")));
        Assert.assertTrue(first.contains("hello cross turn"));
        store.persist(parent);

        AgentContext child = SubAgentContextFactory.create(
                parent,
                "inspect demo.txt",
                "inspect file",
                new ToolCollection(),
                "child-read-2",
                SubAgentRegistry.TYPE_GENERAL_PURPOSE);
        store.hydrate(child);

        Assert.assertFalse(child.hasWorkspaceFileBeenRead(targetFile.toAbsolutePath().normalize().toString()));
        WorkspaceReadTool childRead = new WorkspaceReadTool(workspaceService, options);
        childRead.setAgentContext(child);
        String childResult = String.valueOf(childRead.execute(Map.of("path", "demo.txt")));
        Assert.assertFalse(childResult.contains("File unchanged since last read"));
        Assert.assertTrue(childResult.contains("hello cross turn"));

        childRead.execute(Map.of("path", "demo.txt", "start_line", 1, "line_count", 1));
        store.persist(child);
        AgentContext mainReload = AgentContext.builder()
                .requestId("req-main-reload")
                .sessionId("session-x")
                .workspaceRoot(workspaceRoot.toString())
                .build();
        store.hydrate(mainReload);
        Assert.assertEquals(2000, mainReload.getWorkspaceFileReadState(
                targetFile.toAbsolutePath().normalize().toString()).getLineCount());
    }
}
