package org.wwz.ai.test.domain;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.memory.ltm.LtmOwner;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.common.MemoryTool;
import org.wwz.ai.infrastructure.memory.InMemoryCuratedMemoryStore;

import java.util.Map;

public class MemoryToolTest {

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

        Object add = tool.execute(Map.of(
                "action", "add",
                "target", "user",
                "content", "prefers concise Chinese"));
        JSONObject addJson = JSON.parseObject(String.valueOf(add));
        Assert.assertTrue(addJson.getBooleanValue("success"));

        Object replace = tool.execute(Map.of(
                "action", "replace",
                "target", "user",
                "old_text", "concise",
                "content", "prefers brief Chinese answers"));
        Assert.assertTrue(JSON.parseObject(String.valueOf(replace)).getBooleanValue("success"));

        Object remove = tool.execute(Map.of(
                "action", "remove",
                "target", "user",
                "old_text", "brief"));
        Assert.assertTrue(JSON.parseObject(String.valueOf(remove)).getBooleanValue("success"));
        Assert.assertTrue(store.listActive(LtmOwner.user("u1"),
                org.wwz.ai.domain.agent.memory.ltm.CuratedMemoryScope.USER).isEmpty());
    }
}
