package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.core.io.ClassPathResource;
import org.wwz.ai.domain.agent.reactor.agent.agent.AgentContext;
import org.wwz.ai.domain.agent.reactor.agent.dto.tool.McpToolInfo;
import org.wwz.ai.domain.agent.reactor.agent.tool.ToolCollection;
import org.wwz.ai.domain.agent.reactor.agent.tool.factory.AgentToolCollectionFactory;
import org.wwz.ai.domain.agent.reactor.agent.tool.mcp.runtime.McpToolExecutor;
import org.wwz.ai.domain.agent.reactor.agent.tool.skill.DefaultSkillRegistry;
import org.wwz.ai.domain.agent.reactor.agent.tool.skill.SkillMarkdownParser;
import org.wwz.ai.domain.agent.reactor.agent.tool.skill.SkillPathGuard;
import org.wwz.ai.domain.agent.reactor.agent.tool.skill.SkillRuntimeOptions;
import org.wwz.ai.domain.agent.reactor.agent.tool.skill.SkillScriptDiscoverer;
import org.wwz.ai.domain.agent.reactor.agent.tool.skill.SkillScriptRunnerClient;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

        AgentToolCollectionFactory factory = new AgentToolCollectionFactory(
                buildReactorConfig(),
                mcpToolExecutor,
                skillRegistry,
                SkillRuntimeOptions.builder()
                        .enabled(true)
                        .reactEnabled(true)
                        .planSolveEnabled(true)
                        .build(),
                Mockito.mock(SkillScriptRunnerClient.class)
        );

        ToolCollection toolCollection = factory.buildForReact(buildAgentContext(), buildAgentRequest("html"));

        Assert.assertEquals(
                Arrays.asList(
                        "file_tool",
                        "code_interpreter",
                        "report_tool",
                        "deep_search",
                        "skill_tool",
                        "read_tool",
                        "list_directory_tool",
                        "glob_tool",
                        "grep_tool",
                        "script_runner_tool"
                ),
                new ArrayList<>(toolCollection.getToolMap().keySet())
        );
        Assert.assertTrue(toolCollection.getMcpToolMap().containsKey("remote_tool"));
    }

    @Test
    public void shouldNotIncludeSkillToolWhenPlanSolveSkillDisabled() throws Exception {
        DefaultSkillRegistry skillRegistry = createRegistry(true, true);
        skillRegistry.refresh();

        McpToolExecutor mcpToolExecutor = Mockito.mock(McpToolExecutor.class);
        Mockito.when(mcpToolExecutor.discoverConfiguredTools()).thenReturn(List.of());

        AgentToolCollectionFactory factory = new AgentToolCollectionFactory(
                buildReactorConfig(),
                mcpToolExecutor,
                skillRegistry,
                SkillRuntimeOptions.builder()
                        .enabled(true)
                        .reactEnabled(true)
                        .planSolveEnabled(false)
                        .build(),
                Mockito.mock(SkillScriptRunnerClient.class)
        );

        ToolCollection toolCollection = factory.buildForPlanSolve(buildAgentContext(), buildAgentRequest("docs"));

        Assert.assertFalse(toolCollection.getToolMap().containsKey("skill_tool"));
        Assert.assertFalse(toolCollection.getToolMap().containsKey("script_runner_tool"));
        Assert.assertTrue(toolCollection.getToolMap().containsKey("file_tool"));
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
        reactorConfig.setMultiAgentToolList("{\"default\":\"search,code,report\"}");
        return reactorConfig;
    }

    private AgentContext buildAgentContext() {
        return AgentContext.builder()
                .requestId("req-001")
                .sessionId("session-001")
                .query("测试 skill 工具装配")
                .task("")
                .productFiles(new ArrayList<>())
                .taskProductFiles(new ArrayList<>())
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
}
