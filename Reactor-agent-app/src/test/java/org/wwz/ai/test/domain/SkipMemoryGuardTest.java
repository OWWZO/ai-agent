package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.memory.ltm.BackgroundReviewService;
import org.wwz.ai.domain.agent.memory.ltm.CuratedMemoryScope;
import org.wwz.ai.domain.agent.memory.ltm.LtmManager;
import org.wwz.ai.domain.agent.memory.ltm.LtmMemoryGuard;
import org.wwz.ai.domain.agent.memory.ltm.LtmOwner;
import org.wwz.ai.domain.agent.memory.ltm.LtmServices;
import org.wwz.ai.domain.agent.memory.ltm.LtmTurnSyncSupport;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentContextFactory;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentDefinition;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentToolFilter;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.common.MemoryTool;
import org.wwz.ai.domain.agent.runtime.tool.common.SessionSearchTool;
import org.wwz.ai.infrastructure.memory.InMemoryCuratedMemoryStore;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

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
        Assert.assertNotNull(out);
        Assert.assertTrue(String.valueOf(out).contains("skip_memory"));
        Assert.assertTrue(store.listActive(LtmOwner.user("u1"), CuratedMemoryScope.USER).isEmpty());
    }

    @Test
    public void subAgentContextIsolatedFromLongTermMemory() {
        LtmOwner owner = LtmOwner.user("u-parent");
        AgentContext parent = AgentContext.builder()
                .requestId("req")
                .sessionId("sess")
                .skipMemory(false)
                .ltmOwner(owner)
                .build();
        Assert.assertEquals(owner, parent.getLtmOwner());
        ToolCollection tools = new ToolCollection();
        AgentContext child = SubAgentContextFactory.create(
                parent, "do research", "inspect", tools, "agent1", "general-purpose");
        Assert.assertTrue(LtmMemoryGuard.isSkipMemory(child));
        Assert.assertNull(child.getLtmOwner());
        Assert.assertNull(child.getLtmMemoryContext());
    }

    @Test
    public void subAgentToolFilterStripsLongTermMemoryTools() {
        ToolCollection parent = new ToolCollection();
        MemoryTool memoryTool = new MemoryTool();
        SessionSearchTool searchTool = new SessionSearchTool();
        parent.addTool(memoryTool);
        parent.addTool(searchTool);
        parent.addTool(new StubTool("fact_store"));
        parent.addTool(new StubTool("viking_memory"));

        ToolCollection child = SubAgentToolFilter.filter(parent, defWithAllowAll(), false);
        Assert.assertNull(child.getToolMap().get(MemoryTool.TOOL_NAME));
        Assert.assertNull(child.getToolMap().get("fact_store"));
        Assert.assertNull(child.getToolMap().get("viking_memory"));
        Assert.assertNull(child.getToolMap().get(SessionSearchTool.TOOL_NAME));
    }

    @Test
    public void turnSyncSkipsAllLongTermMemorySideEffectsWhenSkipped() {
        LtmManager manager = mock(LtmManager.class);
        BackgroundReviewService review = mock(BackgroundReviewService.class);
        AgentContext ctx = AgentContext.builder()
                .skipMemory(true)
                .query("durable project convention")
                .sessionId("s")
                .runtimeDependencies(ReactorRuntimeDependencies.builder()
                        .ltmManager(manager)
                        .build())
                .build();
        LtmServices.bind(review, null);
        try {
            LtmTurnSyncSupport.syncSuccessfulTurn(ctx, null);
        } finally {
            LtmServices.bind(null, null);
        }
        verifyNoInteractions(manager, review);
    }

    private static SubAgentDefinition defWithAllowAll() {
        return SubAgentDefinition.builder()
                .agentType("general-purpose")
                .allowedTools(null)
                .build();
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
            return "ok";
        }
    }
}
