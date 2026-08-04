package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.memory.ltm.LtmManager;
import org.wwz.ai.domain.agent.memory.ltm.LtmOwner;
import org.wwz.ai.domain.agent.memory.ltm.MemoryContextFence;
import org.wwz.ai.infrastructure.memory.BuiltinMemoryProvider;
import org.wwz.ai.infrastructure.memory.InMemoryCuratedMemoryStore;
import org.wwz.ai.infrastructure.memory.holographic.HolographicMemoryProvider;
import org.wwz.ai.infrastructure.memory.openviking.OpenVikingMemoryProvider;

import java.util.Map;

public class DeepProviderActivationTest {

    @Test
    public void holographicPrefetchAndSingleExternal() {
        InMemoryCuratedMemoryStore store = new InMemoryCuratedMemoryStore();
        LtmManager manager = new LtmManager(200);
        Assert.assertTrue(manager.addProvider(new BuiltinMemoryProvider(store)));
        HolographicMemoryProvider holo = new HolographicMemoryProvider();
        Assert.assertTrue(manager.addProvider(holo));
        Assert.assertFalse(manager.addProvider(new OpenVikingMemoryProvider("")));

        LtmOwner owner = LtmOwner.user("u-deep");
        manager.initializeAll("s1", owner, Map.of());
        holo.handleToolCall("fact_store", Map.of("action", "add", "content", "deploy port is 9090"));
        String fenced = manager.prefetchAll("deploy port", "s1");
        Assert.assertTrue(fenced.contains(MemoryContextFence.OPEN) || fenced.contains("9090"));
        manager.shutdownAll();
    }

    @Test
    public void openvikingRememberAndSearch() {
        OpenVikingMemoryProvider viking = new OpenVikingMemoryProvider("");
        viking.initialize("s2", LtmOwner.visitor("v1"), Map.of());
        String add = viking.handleToolCall("viking_memory", Map.of(
                "action", "remember",
                "category", "preference",
                "content", "likes markdown tables"));
        Assert.assertTrue(add.contains("\"success\":true"));
        String prefetch = viking.prefetch("markdown", "s2");
        Assert.assertTrue(prefetch.contains("markdown"));
    }
}
