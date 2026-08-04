package org.wwz.ai.test.spring.ai;

import com.alibaba.fastjson.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.tool.McpToolInfo;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentRegistry;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentRunner;
import org.wwz.ai.domain.agent.runtime.tool.factory.AgentToolCollectionFactory;
import org.wwz.ai.domain.agent.runtime.tool.mcp.runtime.McpToolExecutor;
import org.wwz.ai.domain.agent.runtime.tool.skill.DefaultSkillRegistry;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillMarkdownParser;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillPathGuard;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeOptions;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillScriptDiscoverer;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspacePathGuard;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceRuntimeOptions;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceService;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.test.domain.support.ReactorRuntimeTestSupport;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 工具装配工厂测试，确保 skill 只进入 PlanSolve / ReAct。
 */
public class AgentToolCollectionFactoryTest {

    @Test
    public void shouldIncludeSkillToolForReactAndKeepStableOrder() throws Exception {
        DefaultSkillRegistry skillRegistry = createRegistry(true, true);
        skillRegistry.refresh();

        McpToolExecutor mcpToolExecutor = Mockito.mock(McpToolExecutor.class);
        Mockito.when(mcpToolExecutor.discoverConfiguredTools()).thenReturn(List.of(
                McpToolInfo.builder()
                        .name("remote_tool")
                        .desc("远程测试工具")
                        .parameters("{}")
                        .build()
        ));

        AgentToolCollectionFactory factory = newFactory(
                buildReactorConfig(),
                mcpToolExecutor,
                skillRegistry,
                SkillRuntimeOptions.builder()
                        .enabled(true)
                        .reactEnabled(true)
                        .planSolveEnabled(true)
                        .build(),
                disabledWorkspaceService(),
                disabledWorkspaceOptions()
        );

        ToolCollection toolCollection = factory.buildForReact(buildAgentContext(), buildAgentRequest("html"));

        // 顺序随 tool_list 配置；本测显式只挂 search/web_fetch/code/multimodalagent（默认不含 report）
        Assert.assertTrue(toolCollection.getToolMap().containsKey("file_tool"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("code_interpreter"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("code_execution"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("report_tool"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("deep_search"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("WebFetch"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("multimodalagent_tool"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("skill_tool"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("script_runner_tool"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("Agent"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("SendUserMessage"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("Brief"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("Bash"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("PowerShell"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("WebSearch"));
        Assert.assertTrue(toolCollection.getMcpToolMap().containsKey("remote_tool"));
    }

    @Test
    public void shouldNotIncludeSkillToolWhenPlanSolveSkillDisabled() throws Exception {
        DefaultSkillRegistry skillRegistry = createRegistry(true, true);
        skillRegistry.refresh();

        McpToolExecutor mcpToolExecutor = Mockito.mock(McpToolExecutor.class);
        Mockito.when(mcpToolExecutor.discoverConfiguredTools()).thenReturn(List.of());

        AgentToolCollectionFactory factory = newFactory(
                buildReactorConfig(),
                mcpToolExecutor,
                skillRegistry,
                SkillRuntimeOptions.builder()
                        .enabled(true)
                        .reactEnabled(true)
                        .planSolveEnabled(false)
                        .build(),
                disabledWorkspaceService(),
                disabledWorkspaceOptions()
        );

        ToolCollection toolCollection = factory.buildForPlanSolve(buildAgentContext(), buildAgentRequest("docs"));

        Assert.assertFalse(toolCollection.getToolMap().containsKey("skill_tool"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("script_runner_tool"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("file_tool"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("multimodalagent_tool"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("Agent"));
    }

    @Test
    public void shouldNotIncludeMultiModalAgentWhenRemovedFromDefaultList() {
        McpToolExecutor mcpToolExecutor = Mockito.mock(McpToolExecutor.class);
        Mockito.when(mcpToolExecutor.discoverConfiguredTools()).thenReturn(List.of());

        ReactorConfig reactorConfig = buildReactorConfig();
        reactorConfig.setMultiAgentToolList("{\"default\":\"search,code,report\"}");

        AgentToolCollectionFactory factory = newFactory(
                reactorConfig,
                mcpToolExecutor,
                Mockito.mock(DefaultSkillRegistry.class),
                SkillRuntimeOptions.builder()
                        .enabled(false)
                        .reactEnabled(false)
                        .planSolveEnabled(false)
                        .build(),
                disabledWorkspaceService(),
                disabledWorkspaceOptions()
        );

        ToolCollection toolCollection = factory.buildForReact(buildAgentContext(), buildAgentRequest("html"));

        Assert.assertFalse(toolCollection.getToolMap().containsKey("multimodalagent_tool"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("Agent"));
    }

    @Test
    public void shouldRegisterAllDocumentGenerationToolsForReactAndPlanSolve() {
        McpToolExecutor mcpToolExecutor = Mockito.mock(McpToolExecutor.class);
        Mockito.when(mcpToolExecutor.discoverConfiguredTools()).thenReturn(List.of());

        ReactorConfig reactorConfig = buildReactorConfig();
        reactorConfig.setMultiAgentToolList("{\"default\":\"docgen\"}");
        AgentToolCollectionFactory factory = newFactory(
                reactorConfig,
                mcpToolExecutor,
                Mockito.mock(DefaultSkillRegistry.class),
                SkillRuntimeOptions.builder().enabled(false).build(),
                disabledWorkspaceService(),
                disabledWorkspaceOptions()
        );

        assertDocumentGenerationTools(factory.buildForReact(buildAgentContext(), buildAgentRequest("html")));
        assertDocumentGenerationTools(factory.buildForPlanSolve(buildAgentContext(), buildAgentRequest("html")));
    }

    @Test
    public void shouldKeepDataAgentToolingStableWithoutMultiModalAgent() {
        McpToolExecutor mcpToolExecutor = Mockito.mock(McpToolExecutor.class);
        Mockito.when(mcpToolExecutor.discoverConfiguredTools()).thenReturn(List.of());

        AgentToolCollectionFactory factory = newFactory(
                buildReactorConfig(),
                mcpToolExecutor,
                Mockito.mock(DefaultSkillRegistry.class),
                SkillRuntimeOptions.builder()
                        .enabled(false)
                        .reactEnabled(false)
                        .planSolveEnabled(false)
                        .build(),
                disabledWorkspaceService(),
                disabledWorkspaceOptions()
        );

        ToolCollection toolCollection = factory.buildForReact(buildAgentContext(), buildAgentRequest("dataAgent"));

        Assert.assertFalse(toolCollection.getToolMap().containsKey("report_tool"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("data_analysis"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("multimodalagent_tool"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("Agent"));
    }

    @Test
    public void shouldBuildChildToolCollectionWithIsolatedTaskScopedState() {
        McpToolExecutor mcpToolExecutor = Mockito.mock(McpToolExecutor.class);
        Mockito.when(mcpToolExecutor.discoverConfiguredTools()).thenReturn(List.of());

        AgentToolCollectionFactory factory = newFactory(
                buildReactorConfig(),
                mcpToolExecutor,
                Mockito.mock(DefaultSkillRegistry.class),
                SkillRuntimeOptions.builder()
                        .enabled(false)
                        .reactEnabled(false)
                        .planSolveEnabled(false)
                        .build(),
                disabledWorkspaceService(),
                disabledWorkspaceOptions()
        );

        AgentContext parentContext = buildAgentContext();
        AgentRequest request = buildAgentRequest("html");
        ToolCollection parentToolCollection = factory.buildForPlanSolve(parentContext, request);
        parentContext.setToolCollection(parentToolCollection);
        parentContext.setTask("父任务");
        parentContext.getProductFiles().add(File.builder().fileName("parent-session.md").ossUrl("https://parent/session").domainUrl("https://parent/session").isInternalFile(false).build());

        JSONObject parentEmployees = new JSONObject();
        parentEmployees.put("file_tool", "父数字员工");
        parentToolCollection.updateDigitalEmployee(parentEmployees);
        parentToolCollection.setCurrentTask("父任务");

        AgentContext childContext = parentContext.forkForParallelTask("子任务");
        ToolCollection childToolCollection = factory.buildForParallelTask(childContext, request, parentToolCollection);
        childContext.setToolCollection(childToolCollection);

        Assert.assertNotSame(parentContext, childContext);
        Assert.assertNotSame(parentToolCollection, childToolCollection);
        Assert.assertSame(parentContext.getToolArtifactRegistry(), childContext.getToolArtifactRegistry());
        Assert.assertSame(parentContext.getAgentRunState(), childContext.getAgentRunState());
        Assert.assertEquals("子任务", childContext.getTask());
        Assert.assertEquals("父任务", parentContext.getTask());
        Assert.assertEquals("父任务", childToolCollection.getCurrentTask());
        Assert.assertEquals("父任务", parentToolCollection.getCurrentTask());
        Assert.assertEquals("父数字员工", childToolCollection.getDigitalEmployee("file_tool"));

        childToolCollection.setCurrentTask("子任务");
        JSONObject childEmployees = new JSONObject();
        childEmployees.put("file_tool", "子数字员工");
        childToolCollection.updateDigitalEmployee(childEmployees);
        childContext.getProductFiles().add(File.builder().fileName("child-session.md").ossUrl("https://child/session").domainUrl("https://child/session").isInternalFile(false).build());
        childContext.getCurrentToolArtifactSourceHolder().set(null);

        Assert.assertEquals("父任务", parentToolCollection.getCurrentTask());
        Assert.assertEquals("父数字员工", parentToolCollection.getDigitalEmployee("file_tool"));
        Assert.assertEquals(1, parentContext.getProductFiles().size());
        Assert.assertEquals(2, childContext.getProductFiles().size());
    }

    @Test
    public void shouldKeepRunStateCursorThreadScopedForParallelReaders() throws Exception {
        AgentContext context = buildAgentContext();
        context.activateLedgerRun(101L, "run-101");
        context.markExecutionPosition("parent", 1);
        context.getAgentRunState().bindCurrentLlmInvocationId(11L);

        final String[] agentName = new String[1];
        final Integer[] stepNo = new Integer[1];
        final Long[] llmInvocationId = new Long[1];

        Thread childThread = new Thread(() -> {
            AgentContext childContext = context.forkForParallelTask("并发子任务");
            childContext.markExecutionPosition("child", 3);
            childContext.getAgentRunState().bindCurrentLlmInvocationId(33L);
            agentName[0] = childContext.getAgentRunState().getCurrentAgentName();
            stepNo[0] = childContext.getAgentRunState().getCurrentStepNo();
            llmInvocationId[0] = childContext.getAgentRunState().getCurrentLlmInvocationId();
        });
        childThread.start();
        childThread.join();

        Assert.assertEquals("parent", context.getAgentRunState().getCurrentAgentName());
        Assert.assertEquals(Integer.valueOf(1), context.getAgentRunState().getCurrentStepNo());
        Assert.assertEquals(Long.valueOf(11L), context.getAgentRunState().getCurrentLlmInvocationId());
        Assert.assertEquals("child", agentName[0]);
        Assert.assertEquals(Integer.valueOf(3), stepNo[0]);
        Assert.assertEquals(Long.valueOf(33L), llmInvocationId[0]);
    }


    @Test
    public void shouldExposeWorkspaceToolsAndHideFileToolWhenEnabled() throws Exception {
        DefaultSkillRegistry skillRegistry = createRegistry(true, true);
        skillRegistry.refresh();

        McpToolExecutor mcpToolExecutor = Mockito.mock(McpToolExecutor.class);
        Mockito.when(mcpToolExecutor.discoverConfiguredTools()).thenReturn(List.of());

        AgentToolCollectionFactory factory = newFactory(
                buildReactorConfig(),
                mcpToolExecutor,
                skillRegistry,
                SkillRuntimeOptions.builder()
                        .enabled(true)
                        .reactEnabled(true)
                        .planSolveEnabled(true)
                        .build(),
                enabledWorkspaceService(),
                enabledWorkspaceOptions()
        );

        AgentContext ctx = buildAgentContext();
        ToolCollection toolCollection = factory.buildForReact(ctx, buildAgentRequest("html"));

        Assert.assertFalse(toolCollection.getToolMap().containsKey("file_tool"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("workspace_read"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("workspace_write"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("workspace_edit"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("workspace_list"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("workspace_glob"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("workspace_grep"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("read_tool"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("list_directory_tool"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("glob_tool"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("grep_tool"));
        Assert.assertNotNull(ctx.getWorkspaceRoot());
    }

    private AgentToolCollectionFactory newFactory(ReactorConfig reactorConfig,
                                                  McpToolExecutor mcpToolExecutor,
                                                  org.wwz.ai.domain.agent.runtime.tool.skill.SkillRegistry skillRegistry,
                                                  SkillRuntimeOptions skillRuntimeOptions,
                                                  WorkspaceService workspaceService,
                                                  WorkspaceRuntimeOptions workspaceRuntimeOptions) {
        SubAgentRegistry subAgentRegistry = new SubAgentRegistry();
        SubAgentRunner subAgentRunner = new SubAgentRunner(subAgentRegistry);
        return new AgentToolCollectionFactory(
                reactorConfig,
                mcpToolExecutor,
                skillRegistry,
                skillRuntimeOptions,
                workspaceService,
                workspaceRuntimeOptions,
                subAgentRunner,
                subAgentRegistry,
                Mockito.mock(org.wwz.ai.domain.agent.runtime.askuser.PendingUserQuestionRegistry.class),
                Mockito.mock(org.wwz.ai.domain.agent.runtime.planmode.PendingPlanApprovalRegistry.class),
                Mockito.mock(org.wwz.ai.domain.agent.runtime.planmode.PlanArtifactStore.class)
        );
    }

    private WorkspaceService disabledWorkspaceService() {
        return new WorkspaceService(
                WorkspaceRuntimeOptions.builder().enabled(false).build(),
                new WorkspacePathGuard(),
                Mockito.mock(org.wwz.ai.domain.agent.runtime.tool.skill.SkillRegistry.class)
        );
    }

    private WorkspaceService enabledWorkspaceService() {
        return new WorkspaceService(
                WorkspaceRuntimeOptions.builder()
                        .enabled(true)
                        .rootTemplate(System.getProperty("java.io.tmpdir") + "/reactor-agent-workspace-test/{sessionId}")
                        .build(),
                new WorkspacePathGuard(),
                Mockito.mock(org.wwz.ai.domain.agent.runtime.tool.skill.SkillRegistry.class)
        );
    }

    private WorkspaceRuntimeOptions disabledWorkspaceOptions() {
        return WorkspaceRuntimeOptions.builder().enabled(false).build();
    }

    private WorkspaceRuntimeOptions enabledWorkspaceOptions() {
        return WorkspaceRuntimeOptions.builder()
                .enabled(true)
                .rootTemplate(System.getProperty("java.io.tmpdir") + "/reactor-agent-workspace-test/{sessionId}")
                .build();
    }

    private DefaultSkillRegistry createRegistry(boolean reactEnabled, boolean planSolveEnabled) throws Exception {
        SkillPathGuard skillPathGuard = new SkillPathGuard();
        return new DefaultSkillRegistry(
                SkillRuntimeOptions.builder()
                        .enabled(true)
                        .directories(List.of(new ClassPathResource("skills").getFile().getAbsolutePath()))
                        .reactEnabled(reactEnabled)
                        .planSolveEnabled(planSolveEnabled)
                        .build(),
                new SkillMarkdownParser(),
                new SkillScriptDiscoverer(skillPathGuard),
                skillPathGuard
        );
    }

    private ReactorConfig buildReactorConfig() {
        ReactorConfig reactorConfig = new ReactorConfig();
        reactorConfig.setMultiAgentToolList("{\"default\":\"search,web_fetch,code,code_execution,multimodalagent\"}");
        ReflectionTestUtils.setField(reactorConfig, "plannerMaxParallelTasks", 2);
        return reactorConfig;
    }

    private void assertDocumentGenerationTools(ToolCollection toolCollection) {
        Assert.assertTrue(toolCollection.getToolMap().containsKey("document_generate"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("slides_generate"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("excel_generator"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("checklist_generate"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("template_filler"));

        Map<String, Object> documentParameters = toolCollection.getTool("document_generate").toParams();
        Map<String, Object> properties = (Map<String, Object>) documentParameters.get("properties");
        Assert.assertTrue(((List<String>) documentParameters.get("required")).contains("output_path"));
        Assert.assertFalse(((List<String>) documentParameters.get("required")).contains("content"));
        Assert.assertTrue(properties.containsKey("blocks"));
        Assert.assertTrue(properties.containsKey("header"));
        Assert.assertTrue(properties.containsKey("footer"));
        Assert.assertTrue(properties.containsKey("watermark"));
        Assert.assertTrue(properties.containsKey("encryption"));
    }

    private AgentContext buildAgentContext() {
        ReactorRuntimeDependencies runtimeDependencies = ReactorRuntimeTestSupport.runtimeDependencies(buildReactorConfig());
        return AgentContext.builder()
                .requestId("req-001")
                .sessionId("session-001")
                .query("测试 skill 工具装配")
                .task("")
                .printer(new SilentPrinter())
                .productFiles(new ArrayList<>())
                .runtimeDependencies(runtimeDependencies)
                .historyDialogue("")
                .basePrompt("")
                .sopPrompt("")
                .dateInfo("2026-05-10")
                .build();
    }

    private AgentRequest buildAgentRequest(String outputStyle) {
        return AgentRequest.builder()
                .requestId("req-001")
                .sessionId("session-001")
                .query("测试 skill 工具装配")
                .outputStyle(outputStyle)
                .build();
    }

    private static final class SilentPrinter implements Printer {

        @Override
        public void send(String messageId, String messageType, Object message, String digitalEmployee, Boolean isFinal) {
        }

        @Override
        public void send(String messageId, String messageType, Object message, java.util.Map<String, Object> extraResultMap, String digitalEmployee, Boolean isFinal) {
        }

        @Override
        public void send(String messageType, Object message) {
        }

        @Override
        public void send(String messageType, Object message, String digitalEmployee) {
        }

        @Override
        public void send(String messageId, String messageType, Object message, Boolean isFinal) {
        }

        @Override
        public void sendWithResultMap(String messageId, String messageType, Object message, java.util.Map<String, Object> extraResultMap, Boolean isFinal) {
        }

        @Override
        public void sendWithResultMap(String messageType, Object message, java.util.Map<String, Object> extraResultMap) {
        }

        @Override
        public void close() {
        }

        @Override
        public void updateAgentType(org.wwz.ai.domain.agent.runtime.enums.AgentType agentType) {
        }
    }
}
