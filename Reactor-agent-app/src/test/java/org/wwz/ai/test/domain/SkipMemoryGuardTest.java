package org.wwz.ai.test.domain;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.memory.ltm.CuratedMemoryScope;
import org.wwz.ai.domain.agent.memory.ltm.LtmMemoryGuard;
import org.wwz.ai.domain.agent.memory.ltm.LtmOwner;
import org.wwz.ai.domain.agent.memory.ltm.LtmTurnSyncSupport;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentContextFactory;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentDefinition;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentToolFilter;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.common.MemoryTool;
import org.wwz.ai.domain.agent.runtime.tool.common.SessionSearchTool;
import org.wwz.ai.infrastructure.memory.InMemoryCuratedMemoryStore;

import java.util.Map;
import java.util.Set;

public class SkipMemoryGuardTest {

    @Test
    public void memoryToolRejectsWhenSkipMemory() {
        InMemoryCuratedMemoryStore store = new InMemoryCuratedMemoryStore();
        AgentContext ctx = AgentContext.builder()
                .sessionId("s1")
                .requestId("r1")
                .skipMemory(true)
                .ltmOwner(LtmOwner.user("u1"))
                .runtimeDependencies(ReactorRuntimeDependencies.builder()
                        .curatedMemoryStore(store)
                        .build())
                .build();
        MemoryTool tool = new MemoryTool();
        tool.setAgentContext(ctx);
        Object out = tool.execute(Map.of(
                "action", "add",
                "target", "user",
                "content", "should not persist"));
        JSONObject json = JSON.parseObject(String.valueOf(out));
        Assert.assertFalse(json.getBooleanValue("success"));
        Assert.assertTrue(json.getString("message").contains("skip_memory"));
        Assert.assertTrue(store.listActive(LtmOwner.user("u1"), CuratedMemoryScope.USER).isEmpty());
    }

    @Test
    public void subAgentContextSetsSkipMemory() {
        AgentContext parent = AgentContext.builder()
                .requestId("req")
                .sessionId("sess")
                .skipMemory(false)
                .build();
        ToolCollection tools = new ToolCollection();
        AgentContext child = SubAgentContextFactory.create(
                parent, "do research", "explore", tools, "agent1", "Explore");
        Assert.assertTrue(LtmMemoryGuard.isSkipMemory(child));
        Assert.assertNull(child.getLtmOwner());
        Assert.assertNull(child.getLtmMemoryContext());
    }

    @Test
    public void subAgentToolFilterStripsMemoryTools() {
        ToolCollection parent = new ToolCollection();
        MemoryTool memoryTool = new MemoryTool();
        SessionSearchTool searchTool = new SessionSearchTool();
        parent.addTool(memoryTool);
        parent.addTool(searchTool);

        SubAgentDefinition def = SubAgentDefinition.builder()
                .agentType("Explore")
                .allowedTools(Set.of("*"))
                .build();
        // allowsAllTools if *
        ToolCollection child = SubAgentToolFilter.filter(parent, defWithAllowAll(), false);
        Assert.assertNull(child.getToolMap().get(MemoryTool.TOOL_NAME));
        Assert.assertNull(child.getToolMap().get(SessionSearchTool.TOOL_NAME));
    }

    @Test
    public void turnSyncSkippedWhenSkipMemory() {
        AgentContext ctx = AgentContext.builder()
                .skipMemory(true)
                .query("hi")
                .sessionId("s")
                .build();
        // 不应抛异常
        LtmTurnSyncSupport.syncSuccessfulTurn(ctx, null);
    }

    private static SubAgentDefinition defWithAllowAll() {
        return SubAgentDefinition.builder()
                .agentType("Explore")
                .allowedTools(null)
                .build();
    }
}
