package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.memory.ltm.LtmAgentForkSupport;
import org.wwz.ai.domain.agent.memory.ltm.LtmForkParity;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.common.MemoryTool;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class LtmForkParityTest {

    @Test
    public void flushParityIsMemoryOnly() {
        ToolCollection tools = new ToolCollection();
        tools.addTool(new MemoryTool());
        tools.addTool(new StubTool("bash"));
        tools.addTool(new StubTool("workspace_write"));
        LtmForkParity parity = LtmForkParity.forFlush(
                "frozen-system",
                tools,
                List.of(Message.builder().role(RoleType.USER).content("hi").build()));
        Assert.assertEquals("frozen-system", parity.getFrozenSystemPrompt());
        Assert.assertEquals(Set.of("memory"), parity.getDispatchWhitelist());
    }

    @Test
    public void reviewWhitelistIntersectsParentTools() {
        ToolCollection tools = new ToolCollection();
        tools.addTool(new MemoryTool());
        tools.addTool(new StubTool("bash"));
        tools.addTool(new StubTool("workspace_write"));
        tools.addTool(new StubTool("WebSearch"));
        tools.addTool(new StubTool("skill_tool"));
        Set<String> wl = LtmForkParity.resolveCuratorWhitelist(tools);
        Assert.assertTrue(wl.contains("memory"));
        Assert.assertTrue(wl.contains("bash"));
        Assert.assertTrue(wl.contains("workspace_write"));
        Assert.assertTrue(wl.contains("skill_tool"));
        Assert.assertFalse(wl.contains("WebSearch"));

        LtmForkParity review = LtmForkParity.forReview("sys", tools, List.of());
        Assert.assertEquals(wl, review.getDispatchWhitelist());
    }

    @Test
    public void reviewWhitelistKeepsMemoryWhenParentHasNoTools() {
        Set<String> wl = LtmForkParity.resolveCuratorWhitelist(null);
        Assert.assertEquals(Set.of("memory"), wl);
    }

    @Test
    public void toolWhitelistDeniesNonCuratorAtRuntime() {
        ToolCollection tools = new ToolCollection();
        tools.addTool(new MemoryTool());
        tools.addTool(new StubTool("WebSearch"));
        tools.addTool(new StubTool("bash"));
        AgentContext ctx = AgentContext.builder()
                .requestId("t1")
                .toolDispatchWhitelist(LtmForkParity.resolveCuratorWhitelist(tools))
                .toolCollection(tools)
                .build();
        tools.setAgentContext(ctx);

        Object denied = tools.execute("WebSearch", Map.of());
        Assert.assertTrue(String.valueOf(denied).contains("denied non-whitelisted tool"));

        Object bashOk = tools.execute("bash", Map.of("command", "echo hi"));
        Assert.assertFalse(String.valueOf(bashOk).contains("denied non-whitelisted tool"));

        Object memoryResult = tools.execute(MemoryTool.TOOL_NAME, Map.of(
                "action", "add",
                "target", "user",
                "content", "prefers concise"));
        Assert.assertFalse(String.valueOf(memoryResult).contains("denied non-whitelisted tool"));
    }

    @Test
    public void copyParentToolsPreservesNamesAndEnsuresMemory() {
        ToolCollection parent = new ToolCollection();
        parent.addTool(new StubTool("workspace_read"));
        parent.addTool(new StubTool("WebSearch"));
        AgentContext child = AgentContext.builder().requestId("fork-1").build();
        ToolCollection copy = LtmAgentForkSupport.copyParentToolsForFork(parent, child);
        Assert.assertNotNull(copy.getTool("workspace_read"));
        Assert.assertNotNull(copy.getTool("WebSearch"));
        Assert.assertNotNull(copy.getTool(MemoryTool.TOOL_NAME));
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
            return Map.of("type", "object", "properties", Map.of());
        }

        @Override
        public Object execute(Object input) {
            return "ok:" + name;
        }
    }
}
