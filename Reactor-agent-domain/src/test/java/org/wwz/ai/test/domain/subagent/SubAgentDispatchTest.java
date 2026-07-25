package org.wwz.ai.test.domain.subagent;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentDefinition;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentRegistry;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentResult;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentRunner;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentToolFilter;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.common.AgentDispatchTool;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 同步 SubAgent 派发：工具过滤、参数校验、防递归。
 */
public class SubAgentDispatchTest {

    @Test
    public void shouldRegisterExploreAndGeneralPurpose() {
        SubAgentRegistry registry = new SubAgentRegistry();
        Assert.assertTrue(registry.find(SubAgentRegistry.TYPE_EXPLORE).isPresent());
        Assert.assertTrue(registry.find(SubAgentRegistry.TYPE_GENERAL_PURPOSE).isPresent());
        Assert.assertEquals(SubAgentRegistry.TYPE_GENERAL_PURPOSE,
                registry.resolveOrDefault(null).getAgentType());
    }

    @Test
    public void shouldFilterOutAgentAndWriteToolsForExplore() {
        ToolCollection parent = new ToolCollection();
        parent.addTool(new StubTool("workspace_read"));
        parent.addTool(new StubTool("workspace_write"));
        parent.addTool(new StubTool("deep_search"));
        parent.addTool(new StubTool("file_tool"));
        parent.addTool(new StubTool(AgentDispatchTool.NAME));

        SubAgentDefinition explore = new SubAgentRegistry().require(SubAgentRegistry.TYPE_EXPLORE);
        ToolCollection child = SubAgentToolFilter.filter(parent, explore);

        Assert.assertTrue(child.getToolMap().containsKey("workspace_read"));
        Assert.assertTrue(child.getToolMap().containsKey("deep_search"));
        Assert.assertFalse(child.getToolMap().containsKey("workspace_write"));
        Assert.assertFalse(child.getToolMap().containsKey("file_tool"));
        Assert.assertFalse(child.getToolMap().containsKey(AgentDispatchTool.NAME));
    }

    @Test
    public void shouldAlwaysStripAgentFromGeneralPurpose() {
        ToolCollection parent = new ToolCollection();
        parent.addTool(new StubTool("file_tool"));
        parent.addTool(new StubTool("code_interpreter"));
        parent.addTool(new StubTool(AgentDispatchTool.NAME));

        SubAgentDefinition gp = new SubAgentRegistry().require(SubAgentRegistry.TYPE_GENERAL_PURPOSE);
        ToolCollection child = SubAgentToolFilter.filter(parent, gp);

        Assert.assertTrue(child.getToolMap().containsKey("file_tool"));
        Assert.assertTrue(child.getToolMap().containsKey("code_interpreter"));
        Assert.assertFalse(child.getToolMap().containsKey(AgentDispatchTool.NAME));
    }

    @Test
    public void shouldRejectBlankPromptWithoutCallingRunner() {
        SubAgentRegistry registry = new SubAgentRegistry();
        SubAgentRunner runner = new SubAgentRunner(registry);
        AgentDispatchTool tool = new AgentDispatchTool(runner, registry);
        tool.setAgentContext(AgentContext.builder()
                .requestId("req-1")
                .sessionId("s-1")
                .query("q")
                .printer(new NoopPrinter())
                .toolCollection(new ToolCollection())
                .build());

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("description", "test");
        input.put("prompt", "  ");
        ToolResultPayload payload = (ToolResultPayload) tool.execute(input);

        Assert.assertTrue(Boolean.TRUE.equals(payload.getFailed()));
        Assert.assertTrue(payload.getToolResult().contains("prompt"));
    }

    @Test
    public void shouldFailWhenParentToolCollectionMissing() {
        SubAgentRegistry registry = new SubAgentRegistry();
        SubAgentRunner runner = new SubAgentRunner(registry);

        SubAgentResult result = runner.run(
                AgentContext.builder()
                        .requestId("req-2")
                        .sessionId("s-2")
                        .query("q")
                        .printer(new NoopPrinter())
                        .toolCollection(null)
                        .build(),
                "explore files",
                "find all controllers",
                SubAgentRegistry.TYPE_EXPLORE
        );

        Assert.assertEquals(SubAgentResult.STATUS_FAILED, result.getStatus());
        Assert.assertTrue(result.getErrorMsg().contains("工具池"));
    }

    @Test
    public void agentToolSchemaShouldRequireDescriptionAndPrompt() {
        AgentDispatchTool tool = new AgentDispatchTool(new SubAgentRunner(new SubAgentRegistry()), new SubAgentRegistry());
        Map<String, Object> params = tool.toParams();
        Assert.assertEquals("object", params.get("type"));
        Assert.assertTrue(((java.util.List<?>) params.get("required")).contains("description"));
        Assert.assertTrue(((java.util.List<?>) params.get("required")).contains("prompt"));
        Assert.assertTrue(tool.getDescription().contains("Explore"));
    }

    private static final class StubTool implements BaseTool {
        private final String name;

        private StubTool(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return name;
        }

        @Override
        public Map<String, Object> toParams() {
            return Collections.emptyMap();
        }

        @Override
        public Object execute(Object input) {
            return "ok";
        }
    }

    private static final class NoopPrinter implements Printer {
        @Override
        public void send(String messageId, String messageType, Object message, String digitalEmployee, Boolean isFinal) {
        }

        @Override
        public void send(String messageId, String messageType, Object message, Map<String, Object> extraResultMap, String digitalEmployee, Boolean isFinal) {
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
        public void sendWithResultMap(String messageId, String messageType, Object message, Map<String, Object> extraResultMap, Boolean isFinal) {
        }

        @Override
        public void sendWithResultMap(String messageType, Object message, Map<String, Object> extraResultMap) {
        }

        @Override
        public void close() {
        }

        @Override
        public void updateAgentType(AgentType agentType) {
        }
    }
}
