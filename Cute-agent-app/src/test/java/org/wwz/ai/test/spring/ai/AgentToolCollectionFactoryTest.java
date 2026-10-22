package org.wwz.ai.test.spring.ai;

import com.alibaba.fastjson.JSONObject;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.wwz.ai.domain.agent.runtime.util.ToolSchemaNormalizer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.tool.mcp.model.McpToolInfo;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentRegistry;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentRunner;
import org.wwz.ai.domain.agent.runtime.tool.factory.AgentToolCollectionFactory;
import org.wwz.ai.domain.agent.runtime.tool.mcp.port.McpToolExecutor;
import org.wwz.ai.domain.agent.runtime.tool.skill.DefaultSkillRegistry;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillMarkdownParser;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillPathGuard;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeOptions;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillScriptDiscoverer;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspacePathGuard;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceRuntimeOptions;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceService;
import org.wwz.ai.domain.agent.adapter.port.BrowserRelayPort;
import org.wwz.ai.domain.agent.adapter.port.AgentBrowserSessionPort;
import org.wwz.ai.domain.agent.adapter.port.cli.CliExecutionPort;
import org.wwz.ai.domain.agent.runtime.tool.codemode.CodeModeExecutorPort;
import org.wwz.ai.types.agent.config.CodeModeProperties;
import org.wwz.ai.types.agent.config.HostCliProperties;
import org.wwz.ai.types.agent.config.OpenCliProperties;
import org.wwz.ai.domain.agent.runtime.CuteRuntimeDependencies;
import org.wwz.ai.domain.agent.cute.config.CuteConfig;
import org.wwz.ai.domain.agent.runtime.command.AgentExecutionCommand;
import org.wwz.ai.test.domain.support.CuteRuntimeTestSupport;

import java.nio.charset.StandardCharsets;
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
                buildCuteConfig(),
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

        ToolCollection toolCollection = factory.buildForReact(buildAgentContext(), buildAgentExecutionCommand("html"));

        // code_interpreter 和 multimodalagent_tool 已从主 Agent 工具装配移除；code_execution 保留。
        Assert.assertFalse(toolCollection.getToolMap().containsKey("file_tool"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("code_interpreter"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("code_execution"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("report_tool"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("planning"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("deep_search"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("WebFetch"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("multimodalagent_tool"));
         Assert.assertTrue(toolCollection.getToolMap().containsKey("skill_view"));
         Assert.assertTrue(toolCollection.getToolMap().containsKey("skills_search"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("skill_author"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("script_runner_tool"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("Agent"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("SendUserMessage"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("Brief"));
        // workspace 未启用时不挂 bash；启用 workspace + sandboxBash 时见 shouldRegisterBashWhenWorkspaceEnabled
        Assert.assertFalse(toolCollection.getToolMap().containsKey("bash"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("PowerShell"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("WebSearch"));
        // standard 模式 + FQ 命名：mcp__{server}__{tool}
        Assert.assertTrue(toolCollection.getMcpToolMap().keySet().stream()
                .anyMatch(name -> name.endsWith("__remote_tool") || "remote_tool".equals(name)
                        || name.contains("remote_tool")));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("ListMcpResources"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("ReadMcpResource"));
    }

    @Test
    public void shouldDeferMcpToolsAndExposeToolSearchInAlwaysMode() {
        McpToolExecutor mcpToolExecutor = Mockito.mock(McpToolExecutor.class);
        Mockito.when(mcpToolExecutor.discoverConfiguredTools()).thenReturn(List.of(
                McpToolInfo.builder()
                        .mcpId("mcp-1")
                        .serverKey("demo")
                        .name("mcp__demo__remote_tool")
                        .originalName("remote_tool")
                        .desc("远程测试工具")
                        .parameters("{}")
                        .alwaysLoad(false)
                        .build(),
                McpToolInfo.builder()
                        .mcpId("mcp-1")
                        .serverKey("demo")
                        .name("mcp__demo__always_tool")
                        .originalName("always_tool")
                        .desc("always load")
                        .parameters("{}")
                        .alwaysLoad(true)
                        .build()
        ));
        Mockito.when(mcpToolExecutor.hasAnyResources()).thenReturn(false);

        CuteConfig cuteConfig = buildCuteConfig();
        cuteConfig.setMcpToolSearchMode("always");
        AgentToolCollectionFactory factory = newFactory(
                cuteConfig,
                mcpToolExecutor,
                Mockito.mock(DefaultSkillRegistry.class),
                SkillRuntimeOptions.builder().enabled(false).build(),
                disabledWorkspaceService(),
                disabledWorkspaceOptions()
        );

        AgentContext ctx = buildAgentContext();
        ToolCollection toolCollection = factory.buildForReact(ctx, buildAgentExecutionCommand("html"));

        Assert.assertTrue(toolCollection.getToolMap().containsKey("ToolSearch"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("ToolDescribe"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("ToolCall"));
        Assert.assertTrue(toolCollection.getMcpToolMap().containsKey("mcp__demo__always_tool"));
        Assert.assertFalse(toolCollection.getMcpToolMap().containsKey("mcp__demo__remote_tool"));
         Assert.assertNotNull(toolCollection.getDeferredToolCatalog());
         Assert.assertEquals(2, toolCollection.getDeferredToolCatalog().size());
         Assert.assertTrue(toolCollection.getDeferredToolCatalog().contains("mcp__demo__remote_tool"));
         Assert.assertTrue(toolCollection.getDeferredToolCatalog().contains("deep_search"));
    }

    @Test
    public void alwaysModeWithLocalCandidateRegistersBridge() {
        McpToolExecutor mcpToolExecutor = Mockito.mock(McpToolExecutor.class);
        Mockito.when(mcpToolExecutor.discoverConfiguredTools()).thenReturn(List.of());
        Mockito.when(mcpToolExecutor.hasAnyResources()).thenReturn(false);
        CuteConfig cuteConfig = buildCuteConfig();
        cuteConfig.setMcpToolSearchMode("always");
        AgentToolCollectionFactory factory = newFactory(
                cuteConfig,
                mcpToolExecutor,
                Mockito.mock(DefaultSkillRegistry.class),
                SkillRuntimeOptions.builder().enabled(false).build(),
                disabledWorkspaceService(),
                disabledWorkspaceOptions()
        );
        ToolCollection toolCollection = factory.buildForReact(buildAgentContext(), buildAgentExecutionCommand("html"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("ToolSearch"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("ToolDescribe"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("ToolCall"));
        Assert.assertTrue(toolCollection.getDeferredToolCatalog().contains("deep_search"));
    }

    @Test
    public void standardModeKeepsLocalCandidateEager() {
        McpToolExecutor mcpToolExecutor = Mockito.mock(McpToolExecutor.class);
        Mockito.when(mcpToolExecutor.discoverConfiguredTools()).thenReturn(List.of());
        CuteConfig cuteConfig = buildCuteConfig();
        cuteConfig.setMcpToolSearchMode("standard");
        AgentToolCollectionFactory factory = newFactory(
                cuteConfig,
                mcpToolExecutor,
                Mockito.mock(DefaultSkillRegistry.class),
                SkillRuntimeOptions.builder().enabled(false).build(),
                disabledWorkspaceService(),
                disabledWorkspaceOptions());

        ToolCollection toolCollection = factory.buildForReact(buildAgentContext(), buildAgentExecutionCommand("html"));

        Assert.assertTrue(toolCollection.getToolMap().containsKey("deep_search"));
        Assert.assertTrue(toolCollection.getDeferredToolCatalog() == null
                || !toolCollection.getDeferredToolCatalog().contains("deep_search"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("ToolSearch"));
    }

    @Test
    public void dataAgentCanExposeDeferredDataAnalysisThroughTheSameBridge() {
        McpToolExecutor mcpToolExecutor = Mockito.mock(McpToolExecutor.class);
        Mockito.when(mcpToolExecutor.discoverConfiguredTools()).thenReturn(List.of());
        CuteConfig cuteConfig = buildCuteConfig();
        cuteConfig.setMcpToolSearchMode("always");
        AgentToolCollectionFactory factory = newFactory(
                cuteConfig,
                mcpToolExecutor,
                Mockito.mock(DefaultSkillRegistry.class),
                SkillRuntimeOptions.builder().enabled(false).build(),
                disabledWorkspaceService(),
                disabledWorkspaceOptions());
        AgentExecutionCommand request = AgentExecutionCommand.builder()
                .requestId("req-data")
                .sessionId("session-data")
                .query("测试数据分析工具装配")
                .outputStyle("dataAgent")
                .build();

        ToolCollection toolCollection = factory.buildForReact(buildAgentContext(), request);

        Assert.assertFalse(toolCollection.getToolMap().containsKey("data_analysis"));
        Assert.assertTrue(toolCollection.getDeferredToolCatalog().contains("data_analysis"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("ToolSearch"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("ToolDescribe"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("ToolCall"));
    }

    @Test
    public void sessionDisabledMcpIdIsDroppedFromCatalog() {
        McpToolExecutor mcpToolExecutor = Mockito.mock(McpToolExecutor.class);
        Mockito.when(mcpToolExecutor.discoverConfiguredTools()).thenReturn(List.of(
                McpToolInfo.builder()
                        .mcpId("mcp-blocked")
                        .serverKey("blocked")
                        .name("mcp__blocked__secret")
                        .originalName("secret")
                        .desc("should not be searchable")
                        .parameters("{}")
                        .alwaysLoad(false)
                        .build(),
                McpToolInfo.builder()
                        .mcpId("mcp-1")
                        .serverKey("demo")
                        .name("mcp__demo__remote_tool")
                        .originalName("remote_tool")
                        .desc("远程测试工具")
                        .parameters("{}")
                        .alwaysLoad(false)
                        .build()
        ));
        Mockito.when(mcpToolExecutor.hasAnyResources()).thenReturn(false);
        CuteConfig cuteConfig = buildCuteConfig();
        cuteConfig.setMcpToolSearchMode("always");
        AgentToolCollectionFactory factory = newFactory(
                cuteConfig,
                mcpToolExecutor,
                Mockito.mock(DefaultSkillRegistry.class),
                SkillRuntimeOptions.builder().enabled(false).build(),
                disabledWorkspaceService(),
                disabledWorkspaceOptions()
        );
        AgentContext ctx = buildAgentContext();
        ctx.setDisabledMcpIds(java.util.Set.of("mcp-blocked"));
        ToolCollection toolCollection = factory.buildForReact(ctx, buildAgentExecutionCommand("html"));
         Assert.assertNotNull(toolCollection.getDeferredToolCatalog());
         Assert.assertFalse(toolCollection.getDeferredToolCatalog().contains("mcp__blocked__secret"));
         Assert.assertTrue(toolCollection.getDeferredToolCatalog().contains("mcp__demo__remote_tool"));
         String listing = toolCollection.getDeferredToolCatalog().formatListingForToolSearchDescription();
        Assert.assertFalse(listing.contains("mcp__blocked__secret"));
        Assert.assertTrue(listing.contains("mcp__demo__remote_tool"));
    }

    @Test
    public void shouldRegisterBashWhenWorkspaceEnabledAndSandboxBashOn() throws Exception {
        DefaultSkillRegistry skillRegistry = createRegistry(true, true);
        skillRegistry.refresh();

        McpToolExecutor mcpToolExecutor = Mockito.mock(McpToolExecutor.class);
        Mockito.when(mcpToolExecutor.discoverConfiguredTools()).thenReturn(List.of());

        AgentToolCollectionFactory factory = newFactory(
                buildCuteConfig(),
                mcpToolExecutor,
                skillRegistry,
                SkillRuntimeOptions.builder()
                        .enabled(true)
                        .reactEnabled(true)
                        .planSolveEnabled(true)
                        .sandboxBashEnabled(true)
                        .build(),
                enabledWorkspaceService(),
                enabledWorkspaceOptions()
        );

        AgentContext ctx = buildAgentContext();
        ctx.setWorkspaceRoot(System.getProperty("java.io.tmpdir") + "/reactor-agent-workspace-test/session-001");
        ToolCollection toolCollection = factory.buildForReact(ctx, buildAgentExecutionCommand("html"));

         Assert.assertTrue(toolCollection.getToolMap().containsKey("skill_view"));
         Assert.assertTrue(toolCollection.getToolMap().containsKey("skills_search"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("bash"));
    }

    @Test
    public void shouldNotIncludeSkillToolWhenPlanSolveSkillDisabled() throws Exception {
        DefaultSkillRegistry skillRegistry = createRegistry(true, true);
        skillRegistry.refresh();

        McpToolExecutor mcpToolExecutor = Mockito.mock(McpToolExecutor.class);
        Mockito.when(mcpToolExecutor.discoverConfiguredTools()).thenReturn(List.of());

        AgentToolCollectionFactory factory = newFactory(
                buildCuteConfig(),
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

        ToolCollection toolCollection = factory.buildForPlanSolve(buildAgentContext(), buildAgentExecutionCommand("docs"));

         Assert.assertFalse(toolCollection.getToolMap().containsKey("skill_view"));
         Assert.assertFalse(toolCollection.getToolMap().containsKey("skills_search"));
         Assert.assertFalse(toolCollection.getToolMap().containsKey("script_runner_tool"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("file_tool"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("multimodalagent_tool"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("Agent"));
    }

    @Test
    public void shouldNotIncludeRemovedToolsEvenWhenConfigured() {
        McpToolExecutor mcpToolExecutor = Mockito.mock(McpToolExecutor.class);
        Mockito.when(mcpToolExecutor.discoverConfiguredTools()).thenReturn(List.of());

        CuteConfig cuteConfig = buildCuteConfig();
        cuteConfig.setMultiAgentToolList("{\"default\":\"search,code,code_execution,multimodalagent,report\"}");

        AgentToolCollectionFactory factory = newFactory(
                cuteConfig,
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

        ToolCollection toolCollection = factory.buildForReact(buildAgentContext(), buildAgentExecutionCommand("html"));

        Assert.assertFalse(toolCollection.getToolMap().containsKey("multimodalagent_tool"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("code_interpreter"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("code_execution"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("report_tool"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("Agent"));
    }

    @Test
    public void shouldNotRegisterLegacySocialTools() {
        McpToolExecutor mcpToolExecutor = Mockito.mock(McpToolExecutor.class);
        Mockito.when(mcpToolExecutor.discoverConfiguredTools()).thenReturn(List.of());

        CuteConfig cuteConfig = buildCuteConfig();
        cuteConfig.setMultiAgentToolList("{\"default\":\"twitter,reddit,xueqiu\"}");
        AgentToolCollectionFactory factory = newFactory(
                cuteConfig,
                mcpToolExecutor,
                Mockito.mock(DefaultSkillRegistry.class),
                SkillRuntimeOptions.builder().enabled(false).build(),
                disabledWorkspaceService(),
                disabledWorkspaceOptions()
        );

        ToolCollection toolCollection = factory.buildForReact(buildAgentContext(), buildAgentExecutionCommand("html"));

        Assert.assertFalse(toolCollection.getToolMap().containsKey("twitter"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("reddit"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("xueqiu"));
    }

    @Test
    public void shouldRegisterAllDocumentGenerationToolsForReactAndPlanSolve() {
        McpToolExecutor mcpToolExecutor = Mockito.mock(McpToolExecutor.class);
        Mockito.when(mcpToolExecutor.discoverConfiguredTools()).thenReturn(List.of());

        CuteConfig cuteConfig = buildCuteConfig();
        cuteConfig.setMultiAgentToolList("{\"default\":\"docgen\"}");
        AgentToolCollectionFactory factory = newFactory(
                cuteConfig,
                mcpToolExecutor,
                Mockito.mock(DefaultSkillRegistry.class),
                SkillRuntimeOptions.builder().enabled(false).build(),
                disabledWorkspaceService(),
                disabledWorkspaceOptions()
        );

        assertDocumentGenerationTools(factory.buildForReact(buildAgentContext(), buildAgentExecutionCommand("html")));
        assertDocumentGenerationTools(factory.buildForPlanSolve(buildAgentContext(), buildAgentExecutionCommand("html")));
    }

    @Test
    public void shouldFilterPlanSolveMainToolsWithoutChangingChildToolSource() {
        McpToolExecutor mcpToolExecutor = Mockito.mock(McpToolExecutor.class);
        Mockito.when(mcpToolExecutor.discoverConfiguredTools()).thenReturn(List.of());

        CuteConfig cuteConfig = buildCuteConfig();
        cuteConfig.setPlanSolveMainToolList("Agent,TaskCreate");
        AgentToolCollectionFactory factory = newFactory(
                cuteConfig,
                mcpToolExecutor,
                Mockito.mock(DefaultSkillRegistry.class),
                SkillRuntimeOptions.builder().enabled(false).build(),
                disabledWorkspaceService(),
                disabledWorkspaceOptions()
        );

        ToolCollection full = factory.buildForPlanSolve(buildAgentContext(), buildAgentExecutionCommand("html"));
        ToolCollection main = factory.filterForPlanSolveMain(full);

        Assert.assertTrue(full.getToolMap().containsKey("deep_search"));
        Assert.assertTrue(full.getToolMap().containsKey("Agent"));
        Assert.assertTrue(main.getToolMap().containsKey("Agent"));
        Assert.assertTrue(main.getToolMap().containsKey("TaskCreate"));
        Assert.assertFalse(main.getToolMap().containsKey("deep_search"));
        Assert.assertFalse(main.getToolMap().containsKey("web_search"));
    }

    @Test
    public void deployedPlanSolveMainToolListIncludesTaskOutputAndSendMessage() throws Exception {
        for (String profile : List.of("application-dev.yml", "application-prod.yml")) {
            String yaml = new String(new ClassPathResource(profile).getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            int idx = yaml.indexOf("plan-solve-main-tool-list:");
            Assert.assertTrue(profile + " missing plan-solve-main-tool-list", idx >= 0);
            int end = yaml.indexOf('\n', idx);
            String line = end < 0 ? yaml.substring(idx) : yaml.substring(idx, end);
            Assert.assertTrue(profile + " missing TaskOutput", line.contains("TaskOutput"));
            Assert.assertTrue(profile + " missing SendMessage", line.contains("SendMessage"));
            // code_mode 是主 Agent 的编排工具：白名单漏了它，开关打开也调用不到
            Assert.assertTrue(profile + " missing code_mode", line.contains("code_mode"));
        }
    }

    /**
     * code_mode 的注册条件与主 Agent 可见性。
     * <p>
     * 这里锁住两个曾经同时成立、导致"工具调用不了"的坑：
     * ① 配置关闭 / 执行器缺失时不能注册；② 注册后必须留在 PlanSolve 主 Agent 白名单里，
     * 否则 {@link AgentToolCollectionFactory#filterForPlanSolveMain} 会把它过滤掉。
     * </p>
     */
    @Test
    public void shouldRegisterCodeModeOnlyWhenEnabledAndKeepItVisibleToPlanSolveMain() {
        McpToolExecutor mcpToolExecutor = Mockito.mock(McpToolExecutor.class);
        Mockito.when(mcpToolExecutor.discoverConfiguredTools()).thenReturn(List.of());

        CuteConfig cuteConfig = buildCuteConfig();
        cuteConfig.setPlanSolveMainToolList("Agent,workspace_read,code_mode");

        AgentToolCollectionFactory factory = newFactory(
                cuteConfig,
                mcpToolExecutor,
                Mockito.mock(DefaultSkillRegistry.class),
                SkillRuntimeOptions.builder().enabled(false).build(),
                enabledWorkspaceService(),
                enabledWorkspaceOptions()
        );

        ToolCollection disabled = factory.buildForPlanSolve(
                buildAgentContextWithCodeMode(false, null), buildAgentExecutionCommand("html"));
        Assert.assertFalse("配置关闭时不应注册 code_mode", disabled.getToolMap().containsKey("code_mode"));

        ToolCollection noExecutor = factory.buildForPlanSolve(
                buildAgentContextWithCodeMode(true, null), buildAgentExecutionCommand("html"));
        Assert.assertFalse("缺少执行器时不应注册 code_mode", noExecutor.getToolMap().containsKey("code_mode"));

        ToolCollection full = factory.buildForPlanSolve(
                buildAgentContextWithCodeMode(true, Mockito.mock(CodeModeExecutorPort.class)),
                buildAgentExecutionCommand("html"));
        Assert.assertTrue("启用且执行器就绪时应注册 code_mode", full.getToolMap().containsKey("code_mode"));
        Assert.assertTrue("code_mode 必须能通过主 Agent 白名单过滤",
                factory.filterForPlanSolveMain(full).getToolMap().containsKey("code_mode"));
    }

    /**
     * {@code @Value} 的兜底白名单也必须包含 code_mode。
     * <p>
     * 没有 {@code autobots.autoagent.plan-solve-main-tool-list} 的 profile（例如 test）
     * 走的就是这个兜底值，漏了它主 Agent 同样看不到 code_mode。
     * </p>
     */
    @Test
    public void valueFallbackPlanSolveMainToolListIncludesCodeMode() throws Exception {
        java.lang.reflect.Field field = CuteConfig.class.getDeclaredField("planSolveMainToolList");
        org.springframework.beans.factory.annotation.Value annotation =
                field.getAnnotation(org.springframework.beans.factory.annotation.Value.class);
        Assert.assertNotNull("@Value 注解缺失", annotation);
        String fallback = annotation.value();
        int colon = fallback.indexOf(':');
        String defaults = colon < 0 ? "" : fallback.substring(colon + 1, fallback.length() - 1);
        Assert.assertTrue("@Value 兜底白名单缺少 code_mode",
                Arrays.asList(defaults.split(",")).contains("code_mode"));
    }

    private AgentContext buildAgentContextWithCodeMode(boolean enabled, CodeModeExecutorPort executorPort) {
        AgentContext base = buildAgentContext();
        CodeModeProperties codeModeProperties = new CodeModeProperties();
        codeModeProperties.setEnabled(enabled);
        CuteRuntimeDependencies dependencies = base.getRuntimeDependencies().toBuilder()
                .codeModeProperties(codeModeProperties)
                .codeModeExecutorPort(executorPort)
                .build();
        // AgentContext 只有 @Builder，没有 toBuilder；每次 buildAgentContext() 都是新实例，直接 set 即可
        base.setRuntimeDependencies(dependencies);
        return base;
    }

    @Test
    public void shouldBuildChildToolCollectionWithIsolatedTaskScopedState() {
        McpToolExecutor mcpToolExecutor = Mockito.mock(McpToolExecutor.class);
        Mockito.when(mcpToolExecutor.discoverConfiguredTools()).thenReturn(List.of());

        AgentToolCollectionFactory factory = newFactory(
                buildCuteConfig(),
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
        AgentExecutionCommand request = buildAgentExecutionCommand("html");
        ToolCollection parentToolCollection = factory.buildForPlanSolve(parentContext, request);
        parentContext.setToolCollection(parentToolCollection);
        parentContext.setTask("父任务");
        parentContext.getProductFiles().add(File.builder().fileName("parent-session.md").ossUrl("https://parent/session").domainUrl("https://parent/session").isInternalFile(false).build());

        JSONObject parentEmployees = new JSONObject();
        parentEmployees.put("document_generate", "父数字员工");
        parentToolCollection.updateDigitalEmployee(parentEmployees);
        parentToolCollection.setCurrentTask("父任务");

        AgentContext childContext = parentContext.forkForParallelTask("子任务");
        ToolCollection childToolCollection = factory.buildForPlanSolve(childContext, request);
        childToolCollection.restoreTaskScopedState(parentToolCollection.snapshotTaskScopedState());
        childContext.setToolCollection(childToolCollection);

        Assert.assertNotSame(parentContext, childContext);
        Assert.assertNotSame(parentToolCollection, childToolCollection);
        Assert.assertSame(parentContext.getToolArtifactRegistry(), childContext.getToolArtifactRegistry());
        Assert.assertSame(parentContext.getAgentRunState(), childContext.getAgentRunState());
        Assert.assertEquals("子任务", childContext.getTask());
        Assert.assertEquals("父任务", parentContext.getTask());
        Assert.assertEquals("父任务", childToolCollection.getCurrentTask());
        Assert.assertEquals("父任务", parentToolCollection.getCurrentTask());
        Assert.assertEquals("父数字员工", childToolCollection.getDigitalEmployee("document_generate"));

        childToolCollection.setCurrentTask("子任务");
        JSONObject childEmployees = new JSONObject();
        childEmployees.put("document_generate", "子数字员工");
        childToolCollection.updateDigitalEmployee(childEmployees);
        childContext.getProductFiles().add(File.builder().fileName("child-session.md").ossUrl("https://child/session").domainUrl("https://child/session").isInternalFile(false).build());
        childContext.getCurrentToolArtifactSourceHolder().set(null);

        Assert.assertEquals("父任务", parentToolCollection.getCurrentTask());
        Assert.assertEquals("父数字员工", parentToolCollection.getDigitalEmployee("document_generate"));
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
                buildCuteConfig(),
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
        ToolCollection toolCollection = factory.buildForReact(ctx, buildAgentExecutionCommand("html"));

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

    @Test
    public void shouldRegisterBrowserToolWhenRelayOnline() {
        AgentToolCollectionFactory factory = newFactory(
                buildCuteConfig(),
                Mockito.mock(McpToolExecutor.class),
                Mockito.mock(DefaultSkillRegistry.class),
                SkillRuntimeOptions.builder().enabled(false).build(),
                disabledWorkspaceService(),
                disabledWorkspaceOptions()
        );
        BrowserRelayPort port = Mockito.mock(BrowserRelayPort.class);
        Mockito.when(port.isOnline("user-1")).thenReturn(true);
        AgentContext ctx = buildAgentContext();
        ctx.setUserId("user-1");
        ctx.setRuntimeDependencies(ctx.getRuntimeDependencies().toBuilder()
                .browserRelayPort(port)
                .cliExecutionPort(resolvableCli("node"))
                .openCliProperties(enabledOpenCli())
                .build());
        AgentExecutionCommand request = AgentExecutionCommand.builder()
                .requestId("req-001")
                .sessionId("session-001")
                .userId("user-1")
                .query("打开已登录页面")
                .build();

        ToolCollection toolCollection = factory.buildForReact(ctx, request);

        Assert.assertTrue(toolCollection.getToolMap().containsKey("browser"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("browser_site"));
        Assert.assertEquals("远程操作用户的浏览器。", toolCollection.getTool("browser").getDescription());
        Map<String, Object> params = toolCollection.getTool("browser").toParams();
        Assert.assertTrue(((List<?>) params.get("required")).contains("args"));
    }

    @Test
    public void shouldRegisterBrowserToolEvenWhenRelayOffline() {
        AgentToolCollectionFactory factory = newFactory(
                buildCuteConfig(),
                Mockito.mock(McpToolExecutor.class),
                Mockito.mock(DefaultSkillRegistry.class),
                SkillRuntimeOptions.builder().enabled(false).build(),
                disabledWorkspaceService(),
                disabledWorkspaceOptions()
        );
        BrowserRelayPort port = Mockito.mock(BrowserRelayPort.class);
        Mockito.when(port.isOnline("user-1")).thenReturn(false);
        AgentContext ctx = buildAgentContext();
        ctx.setUserId("user-1");
        ctx.setRuntimeDependencies(ctx.getRuntimeDependencies().toBuilder()
                .browserRelayPort(port)
                .cliExecutionPort(resolvableCli("node"))
                .openCliProperties(enabledOpenCli())
                .build());
        AgentExecutionCommand request = AgentExecutionCommand.builder()
                .requestId("req-001")
                .sessionId("session-001")
                .userId("user-1")
                .query("打开已登录页面")
                .build();

        ToolCollection toolCollection = factory.buildForReact(ctx, request);

        // browser 常驻挂载：relay 离线不影响 tools[]，避免工具集合随用户连接状态抖动打爆 prompt cache。
        Assert.assertTrue(toolCollection.getToolMap().containsKey("browser"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("browser_site"));
        Mockito.verify(port, Mockito.never()).isOnline(Mockito.anyString());
    }

    @Test
    public void shouldNotRegisterBrowserToolsForDataAgentEvenWhenOnline() {
        AgentToolCollectionFactory factory = newFactory(
                buildCuteConfig(),
                Mockito.mock(McpToolExecutor.class),
                Mockito.mock(DefaultSkillRegistry.class),
                SkillRuntimeOptions.builder().enabled(false).build(),
                disabledWorkspaceService(),
                disabledWorkspaceOptions()
        );
        BrowserRelayPort port = Mockito.mock(BrowserRelayPort.class);
        Mockito.when(port.isOnline("user-1")).thenReturn(true);
        AgentContext ctx = buildAgentContext();
        ctx.setUserId("user-1");
        ctx.setRuntimeDependencies(ctx.getRuntimeDependencies().toBuilder()
                .browserRelayPort(port)
                .cliExecutionPort(resolvableCli("node"))
                .openCliProperties(enabledOpenCli())
                .build());
        AgentExecutionCommand request = AgentExecutionCommand.builder()
                .requestId("req-001")
                .sessionId("session-001")
                .userId("user-1")
                .outputStyle("dataAgent")
                .query("问数")
                .build();

        ToolCollection toolCollection = factory.buildForReact(ctx, request);

        Assert.assertFalse(toolCollection.getToolMap().containsKey("browser"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("browser_site"));
    }

    @Test
    public void shouldRegisterAgentBrowserWhenConfiguredEvenIfRelayOffline() {
        AgentToolCollectionFactory factory = newFactory(
                buildCuteConfig(),
                Mockito.mock(McpToolExecutor.class),
                Mockito.mock(DefaultSkillRegistry.class),
                SkillRuntimeOptions.builder().enabled(false).build(),
                disabledWorkspaceService(),
                disabledWorkspaceOptions()
        );
        BrowserRelayPort relay = Mockito.mock(BrowserRelayPort.class);
        Mockito.when(relay.isOnline("user-1")).thenReturn(false);
        AgentBrowserSessionPort agentBrowser = Mockito.mock(AgentBrowserSessionPort.class);
        Mockito.when(agentBrowser.isConfigured()).thenReturn(true);
        AgentContext ctx = buildAgentContext();
        ctx.setUserId("user-1");
        ctx.setRuntimeDependencies(ctx.getRuntimeDependencies().toBuilder()
                .browserRelayPort(relay)
                .agentBrowserSessionPort(agentBrowser)
                .cliExecutionPort(resolvableCli("node"))
                .openCliProperties(enabledOpenCli())
                .build());

        AgentExecutionCommand request = buildAgentExecutionCommand("html");
        request.setUserId("user-1");
        ToolCollection toolCollection = factory.buildForReact(ctx, request);

        Assert.assertTrue(toolCollection.getToolMap().containsKey("agent_browser"));
        // browser 常驻挂载，与 relay 在线状态无关
        Assert.assertTrue(toolCollection.getToolMap().containsKey("browser"));
        Mockito.verify(relay, Mockito.never()).isOnline(Mockito.anyString());
    }

    @Test
    public void shouldNotRegisterAgentBrowserForDataAgent() {
        AgentToolCollectionFactory factory = newFactory(
                buildCuteConfig(),
                Mockito.mock(McpToolExecutor.class),
                Mockito.mock(DefaultSkillRegistry.class),
                SkillRuntimeOptions.builder().enabled(false).build(),
                disabledWorkspaceService(),
                disabledWorkspaceOptions()
        );
        AgentBrowserSessionPort agentBrowser = Mockito.mock(AgentBrowserSessionPort.class);
        Mockito.when(agentBrowser.isConfigured()).thenReturn(true);
        AgentContext ctx = buildAgentContext();
        ctx.setUserId("user-1");
        ctx.setRuntimeDependencies(ctx.getRuntimeDependencies().toBuilder()
                .agentBrowserSessionPort(agentBrowser)
                .cliExecutionPort(resolvableCli("node"))
                .openCliProperties(enabledOpenCli())
                .build());
        AgentExecutionCommand request = AgentExecutionCommand.builder()
                .requestId("req-data")
                .sessionId("session-data")
                .userId("user-1")
                .outputStyle("dataAgent")
                .query("问数")
                .build();

        ToolCollection toolCollection = factory.buildForReact(ctx, request);

        Assert.assertFalse(toolCollection.getToolMap().containsKey("agent_browser"));
        Mockito.verify(agentBrowser, Mockito.never()).isConfigured();
    }

    @Test
    public void shouldRegisterHostCliWhenAllowlistResolves() {
        AgentToolCollectionFactory factory = newFactory(
                buildCuteConfig(),
                Mockito.mock(McpToolExecutor.class),
                Mockito.mock(DefaultSkillRegistry.class),
                SkillRuntimeOptions.builder().enabled(false).build(),
                disabledWorkspaceService(),
                disabledWorkspaceOptions()
        );
        HostCliProperties hostCli = new HostCliProperties();
        hostCli.setEnabled(true);
        hostCli.setAllow(List.of("git", "missing-bin"));
        CliExecutionPort cli = Mockito.mock(CliExecutionPort.class);
        Mockito.when(cli.isResolvable("git")).thenReturn(true);
        Mockito.when(cli.isResolvable("missing-bin")).thenReturn(false);
        AgentContext ctx = buildAgentContext();
        ctx.setRuntimeDependencies(ctx.getRuntimeDependencies().toBuilder()
                .cliExecutionPort(cli)
                .hostCliProperties(hostCli)
                .build());

        ToolCollection toolCollection = factory.buildForReact(ctx, buildAgentExecutionCommand("html"));

        Assert.assertTrue(toolCollection.getToolMap().containsKey("host_cli"));
        Assert.assertTrue(toolCollection.getTool("host_cli").getDescription().contains("git"));
        Assert.assertFalse(toolCollection.getTool("host_cli").getDescription().contains("missing-bin"));
    }

    /**
     * 装配出来的每个工具（本地 + MCP）其 schema 都必须是完整的 object schema。
     * <p>
     * 漏声明 {@code properties}/{@code required} 不会报错（会被 {@link ToolSchemaNormalizer} 自动补齐），
     * 但每次装配都会刷 WARN 噪音，且补齐结果依赖下游推断。新增工具时该测试即护栏。
     * </p>
     * <p>
     * MCP 工具的 schema 由外部服务器提供，可能本身就不完整；这里用完整 schema 的 mock，
     * 只验证「完整 schema 不会被误判」。
     * </p>
     */
    @Test
    public void registeredToolSchemasShouldNotEmitIncompleteSchemaWarning() throws Exception {
        DefaultSkillRegistry skillRegistry = createRegistry(true, true);
        skillRegistry.refresh();

        McpToolExecutor mcpToolExecutor = Mockito.mock(McpToolExecutor.class);
        Mockito.when(mcpToolExecutor.discoverConfiguredTools()).thenReturn(List.of(
                McpToolInfo.builder()
                        .mcpId("mcp-1")
                        .serverKey("demo")
                        .name("mcp__demo__remote_tool")
                        .originalName("remote_tool")
                        .desc("远程测试工具")
                        .parameters("{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}},\"required\":[\"query\"]}")
                        .alwaysLoad(true)
                        .build()
        ));

        AgentToolCollectionFactory factory = newFactory(
                buildCuteConfig(),
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

        ToolCollection toolCollection = factory.buildForReact(buildAgentContext(), buildAgentExecutionCommand("html"));

        Logger logger = (Logger) LoggerFactory.getLogger(ToolSchemaNormalizer.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
        try {
            for (org.wwz.ai.domain.agent.runtime.tool.BaseTool tool : toolCollection.getToolMap().values()) {
                ToolSchemaNormalizer.normalizeSchema(tool.toParams(), tool.getName());
            }
            for (McpToolInfo info : toolCollection.getMcpToolMap().values()) {
                ToolSchemaNormalizer.normalizeSchemaAsMap(info.getParameters(), info.getName());
            }

            List<String> warnings = listAppender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .filter(message -> message.contains("检测到不完整 object schema"))
                    .toList();

            Assert.assertTrue("已注册工具的 schema 不应触发不完整 object schema 告警: " + warnings, warnings.isEmpty());
        } finally {
            logger.detachAppender(listAppender);
            listAppender.stop();
        }
    }

    /**
     * 配置白名单装配契约：锁定 build() 里 9 个 if 块的语义。
     * <p>
     * 这些块是「白名单名命中 → new → addTool」的平铺，重构时最容易改错的是
     * 「组内任一别名命中即整组装配」这条规则——组名与工具别名是或关系，不是与关系。
     */
    @Test
    public void whitelistGroupIsAssembledWhenAnyAliasMatches() {
        CuteConfig cuteConfig = buildCuteConfig();
        // docread 组的别名之一；组名本身不出现，验证别名也能触发整组装配
        cuteConfig.setMultiAgentToolList("{\"default\":\"pdf_reader\"}");

        ToolCollection toolCollection = buildForReactWith(cuteConfig);

        // 别名命中整组：组内其它工具一并装配，而不是只装 pdf_reader
        Assert.assertTrue("docread 组应按别名整组装配",
                toolCollection.getToolMap().containsKey("pdf_reader"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("excel_reader"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("word_reader"));
        // 未命中的组不装配
        Assert.assertFalse(toolCollection.getToolMap().containsKey("data_aggregate"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("document_generate"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("deep_search"));
    }

    @Test
    public void emptyWhitelistAssemblesNoConfiguredGroupTools() {
        CuteConfig cuteConfig = buildCuteConfig();
        cuteConfig.setMultiAgentToolList("{\"default\":\"\"}");

        ToolCollection toolCollection = buildForReactWith(cuteConfig);

        // 白名单为空时，所有「按组装配」的工具都不应出现
        Assert.assertFalse(toolCollection.getToolMap().containsKey("deep_search"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("WebFetch"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("WebSearch"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("document_generate"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("pdf_reader"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("data_aggregate"));
        // 但常驻能力仍在：MCP 元工具与子 Agent 派发不受白名单控制
        Assert.assertTrue(toolCollection.getToolMap().containsKey("Agent"));
    }

    @Test
    public void dataAgentStyleExposesOnlyDataAnalysisEntry() {
        CuteConfig cuteConfig = buildCuteConfig();
        cuteConfig.setMultiAgentToolList("{\"default\":\"search,docread,dataprep,docgen\"}");

        AgentToolCollectionFactory factory = newFactory(
                cuteConfig,
                Mockito.mock(McpToolExecutor.class),
                Mockito.mock(DefaultSkillRegistry.class),
                SkillRuntimeOptions.builder().enabled(false).build(),
                disabledWorkspaceService(),
                disabledWorkspaceOptions());
        AgentExecutionCommand request = AgentExecutionCommand.builder()
                .requestId("req-style")
                .sessionId("session-style")
                .query("问数场景")
                .outputStyle("dataAgent")
                .build();

        ToolCollection toolCollection = factory.buildForReact(buildAgentContext(), request);

        // dataAgent 下白名单完全不生效，只挂 data_analysis
        Assert.assertTrue(toolCollection.getToolMap().containsKey("data_analysis"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("deep_search"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("pdf_reader"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("document_generate"));
        // dataAgent 不挂子 Agent 派发与 Plan Mode 工具
        Assert.assertFalse(toolCollection.getToolMap().containsKey("Agent"));
    }

    private ToolCollection buildForReactWith(CuteConfig cuteConfig) {
        AgentToolCollectionFactory factory = newFactory(
                cuteConfig,
                Mockito.mock(McpToolExecutor.class),
                Mockito.mock(DefaultSkillRegistry.class),
                SkillRuntimeOptions.builder().enabled(false).build(),
                disabledWorkspaceService(),
                disabledWorkspaceOptions());
        return factory.buildForReact(buildAgentContext(), buildAgentExecutionCommand("html"));
    }

    private AgentToolCollectionFactory newFactory(CuteConfig cuteConfig,
                                                  McpToolExecutor mcpToolExecutor,
                                                  org.wwz.ai.domain.agent.runtime.tool.skill.SkillRegistry skillRegistry,
                                                  SkillRuntimeOptions skillRuntimeOptions,
                                                  WorkspaceService workspaceService,
                                                  WorkspaceRuntimeOptions workspaceRuntimeOptions) {
        SubAgentRegistry subAgentRegistry = new SubAgentRegistry();
        SubAgentRunner subAgentRunner = new SubAgentRunner(subAgentRegistry);
         org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeLayout layout =
                 new org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeLayout(skillRuntimeOptions);
         org.wwz.ai.domain.agent.runtime.tool.skill.SkillVirtualPaths virtualPaths =
                 new org.wwz.ai.domain.agent.runtime.tool.skill.SkillVirtualPaths(skillRuntimeOptions);
         org.wwz.ai.domain.agent.runtime.tool.skill.SkillPathGuard guard =
                 new org.wwz.ai.domain.agent.runtime.tool.skill.SkillPathGuard();
         org.wwz.ai.domain.agent.runtime.tool.skill.SkillLoader loader =
                 skillRegistry instanceof org.wwz.ai.domain.agent.runtime.tool.skill.SkillCatalog catalog
                         ? new org.wwz.ai.domain.agent.runtime.tool.skill.DefaultSkillLoader(
                                 catalog,
                                 new org.wwz.ai.domain.agent.runtime.tool.skill.SkillMarkdownParser(),
                                 guard)
                         : Mockito.mock(org.wwz.ai.domain.agent.runtime.tool.skill.SkillLoader.class);
          return new AgentToolCollectionFactory(
                 cuteConfig,
                 mcpToolExecutor,
                 skillRegistry,
                 loader,
                 new org.wwz.ai.domain.agent.runtime.tool.skill.SkillScriptDiscoverer(guard),
                  skillRuntimeOptions,
                  layout,
                  virtualPaths,
                 workspaceService,
                workspaceRuntimeOptions,
                subAgentRunner,
                subAgentRegistry,
                Mockito.mock(org.wwz.ai.domain.agent.runtime.cancel.ActiveAgentRunRegistry.class),
                Mockito.mock(org.wwz.ai.domain.agent.runtime.askuser.PendingUserQuestionRegistry.class),
                Mockito.mock(org.wwz.ai.domain.agent.runtime.planmode.PendingPlanApprovalRegistry.class),
                Mockito.mock(org.wwz.ai.domain.agent.runtime.planmode.PlanArtifactStore.class)
        );
    }

    private WorkspaceService disabledWorkspaceService() {
        return new WorkspaceService(
                WorkspaceRuntimeOptions.builder().enabled(false).build(),
                new WorkspacePathGuard(),
                Mockito.mock(org.wwz.ai.domain.agent.runtime.tool.skill.SkillRegistry.class),
                new org.wwz.ai.domain.agent.runtime.tool.skill.SkillVirtualPaths(
                        SkillRuntimeOptions.builder().enabled(false).build())
        );
    }

    private WorkspaceService enabledWorkspaceService() {
        return new WorkspaceService(
                WorkspaceRuntimeOptions.builder()
                        .enabled(true)
                        .rootTemplate(System.getProperty("java.io.tmpdir") + "/reactor-agent-workspace-test/{sessionId}")
                        .build(),
                new WorkspacePathGuard(),
                Mockito.mock(org.wwz.ai.domain.agent.runtime.tool.skill.SkillRegistry.class),
                new org.wwz.ai.domain.agent.runtime.tool.skill.SkillVirtualPaths(
                        SkillRuntimeOptions.builder().enabled(false).build())
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

    private CuteConfig buildCuteConfig() {
        CuteConfig cuteConfig = new CuteConfig();
        cuteConfig.setMultiAgentToolList("{\"default\":\"search,web_fetch,code,code_execution,multimodalagent\"}");
        // 既有用例默认 standard，避免 MCP deferred 改变 mcpToolMap 断言
        cuteConfig.setMcpToolSearchMode("standard");
        return cuteConfig;
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
        CuteRuntimeDependencies runtimeDependencies = CuteRuntimeTestSupport.runtimeDependencies(buildCuteConfig());
        return AgentContext.builder()
                .requestId("req-001")
                .sessionId("session-001")
                .query("测试 skill 工具装配")
                .task("")
                .printer(new SilentPrinter())
                .productFiles(new ArrayList<>())
                .runtimeDependencies(runtimeDependencies)
                .basePrompt("")
                .sopPrompt("")
                .dateInfo("2026-05-10")
                .build();
    }

    private static CliExecutionPort resolvableCli(String tool) {
        CliExecutionPort cli = Mockito.mock(CliExecutionPort.class);
        Mockito.when(cli.isResolvable(Mockito.anyString())).thenAnswer(invocation ->
                tool.equals(invocation.getArgument(0)));
        return cli;
    }

    private static OpenCliProperties enabledOpenCli() {
        OpenCliProperties properties = new OpenCliProperties();
        properties.setEnabled(true);
        properties.setCommand("node");
        return properties;
    }

    private AgentExecutionCommand buildAgentExecutionCommand(String... ignored) {
        return AgentExecutionCommand.builder()
                .requestId("req-001")
                .sessionId("session-001")
                .query("测试 skill 工具装配")
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
