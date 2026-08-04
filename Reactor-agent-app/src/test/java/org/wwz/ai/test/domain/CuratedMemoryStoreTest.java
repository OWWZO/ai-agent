package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.wwz.ai.domain.agent.memory.ltm.CuratedMemoryScope;
import org.wwz.ai.domain.agent.memory.ltm.CuratedMemoryWriteResult;
import org.wwz.ai.domain.agent.memory.ltm.LtmOwner;
import org.wwz.ai.infrastructure.memory.InMemoryCuratedMemoryStore;

public class CuratedMemoryStoreTest {

    private InMemoryCuratedMemoryStore store;

    @Before
    public void setUp() {
        store = new InMemoryCuratedMemoryStore(50, 40);
    }

    @Test
    public void addDuplicateIsNoChange() {
        LtmOwner owner = LtmOwner.user("u1");
        Assert.assertTrue(store.add(owner, CuratedMemoryScope.USER, "likes concise Chinese", "s1", "r1", "tool").isSuccess());
        CuratedMemoryWriteResult second = store.add(owner, CuratedMemoryScope.USER, "likes concise Chinese", "s1", "r2", "tool");
        Assert.assertTrue(second.isSuccess());
        Assert.assertTrue(second.isNoChange());
        Assert.assertEquals(1, store.listActive(owner, CuratedMemoryScope.USER).size());
    }

    @Test
    public void capacityRejectsWithoutDrop() {
        LtmOwner owner = LtmOwner.user("u1");
        Assert.assertTrue(store.add(owner, CuratedMemoryScope.CURATED, "aaaaaaaaaaaaaaaaaaaaaaaa", "s", "r", "tool").isSuccess());
        CuratedMemoryWriteResult full = store.add(owner, CuratedMemoryScope.CURATED, "bbbbbbbbbbbbbbbbbbbbbbbbbbbb", "s", "r", "tool");
        Assert.assertFalse(full.isSuccess());
        Assert.assertTrue(full.getMessage().contains("full") || full.getMessage().contains("consolidate"));
        Assert.assertEquals(1, store.listActive(owner, CuratedMemoryScope.CURATED).size());
    }

    @Test
    public void ownersAreIsolated() {
        LtmOwner a = LtmOwner.user("alice");
        LtmOwner b = LtmOwner.user("bob");
        store.add(a, CuratedMemoryScope.USER, "alice secret", "s", "r", "tool");
        store.add(b, CuratedMemoryScope.USER, "bob secret", "s", "r", "tool");
        Assert.assertTrue(store.listActive(a, CuratedMemoryScope.USER).stream().allMatch(e -> e.getContent().contains("alice")));
        Assert.assertTrue(store.listActive(b, CuratedMemoryScope.USER).stream().noneMatch(e -> e.getContent().contains("alice")));
    }

    @Test
    public void replaceAndRemoveBySubstring() {
        LtmOwner owner = LtmOwner.visitor("v1");
        store.add(owner, CuratedMemoryScope.CURATED, "deploy port is 8080", "s", "r", "tool");
        Assert.assertTrue(store.replace(owner, CuratedMemoryScope.CURATED, "8080", "deploy port is 9090", "s", "r", "tool").isSuccess());
        Assert.assertTrue(store.listActive(owner, CuratedMemoryScope.CURATED).get(0).getContent().contains("9090"));
        Assert.assertTrue(store.remove(owner, CuratedMemoryScope.CURATED, "9090", "s", "r", "tool").isSuccess());
        Assert.assertTrue(store.listActive(owner, CuratedMemoryScope.CURATED).isEmpty());
    }

    @Test
    public void snapshotContainsUsageHeader() {
        LtmOwner owner = LtmOwner.user("u1");
        store.add(owner, CuratedMemoryScope.USER, "prefers markdown", "s", "r", "tool");
        String snap = store.formatSnapshot(owner);
        Assert.assertTrue(snap.contains("USER PROFILE"));
        Assert.assertTrue(snap.contains("prefers markdown"));
        Assert.assertTrue(snap.contains("chars]"));
    }
}
