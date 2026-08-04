package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.memory.ltm.LtmOwner;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.common.MemoryTool;
import org.wwz.ai.infrastructure.memory.InMemoryCuratedMemoryStore;

import java.util.Map;

public class MemoryToolTest {

    @Test
    public void descriptionEncouragesProactiveSave() {
        MemoryTool tool = new MemoryTool();
        String desc = tool.getDescription();
        Assert.assertTrue(desc.contains("save proactively"));
        Assert.assertTrue(desc.contains("WHEN:"));
        Assert.assertTrue(desc.contains("target=user") || desc.contains("'user'"));
        Assert.assertTrue(desc.contains("curated"));
        Assert.assertTrue(desc.contains("SKIP:"));
    }

    @Test
    public void addReplaceRemove() {
        InMemoryCuratedMemoryStore store = new InMemoryCuratedMemoryStore(500, 500);
        AgentContext ctx = AgentContext.builder()
                .sessionId("s1")
                .requestId("r1")
                .ltmOwner(LtmOwner.user("u1"))
                .runtimeDependencies(ReactorRuntimeDependencies.builder()
                        .curatedMemoryStore(store)
                        .build())
                .build();
        MemoryTool tool = new MemoryTool();
        tool.setAgentContext(ctx);

        ToolResultPayload add = (ToolResultPayload) tool.execute(Map.of(
                "action", "add",
                "target", "user",
                "content", "prefers concise Chinese"));
        Assert.assertTrue(add.getLlmData() instanceof Map<?, ?>);
        Assert.assertTrue(Boolean.TRUE.equals(((Map<?, ?>) add.getLlmData()).get("success")));

        ToolResultPayload replace = (ToolResultPayload) tool.execute(Map.of(
                "action", "replace",
                "target", "user",
                "old_text", "concise",
                "content", "prefers brief Chinese answers"));
        Assert.assertTrue(Boolean.TRUE.equals(((Map<?, ?>) replace.getLlmData()).get("success")));

        ToolResultPayload remove = (ToolResultPayload) tool.execute(Map.of(
                "action", "remove",
                "target", "user",
                "old_text", "brief"));
        Assert.assertTrue(Boolean.TRUE.equals(((Map<?, ?>) remove.getLlmData()).get("success")));
        Assert.assertTrue(store.listActive(LtmOwner.user("u1"),
                org.wwz.ai.domain.agent.memory.ltm.CuratedMemoryScope.USER).isEmpty());
    }
}
