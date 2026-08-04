package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.memory.ltm.CuratedMemoryScope;
import org.wwz.ai.domain.agent.memory.ltm.LtmExtractionApplier;
import org.wwz.ai.domain.agent.memory.ltm.LtmExtractionOp;
import org.wwz.ai.domain.agent.memory.ltm.LtmOwner;
import org.wwz.ai.infrastructure.memory.InMemoryCuratedMemoryStore;

import java.util.List;

public class LtmExtractionApplierTest {

    @Test
    public void parseAndApplyAdd() {
        String raw = "```json\n[{\"action\":\"add\",\"target\":\"user\",\"content\":\"prefers terse replies\"}]\n```";
        List<LtmExtractionOp> ops = LtmExtractionApplier.parseOps(raw);
        Assert.assertEquals(1, ops.size());
        InMemoryCuratedMemoryStore store = new InMemoryCuratedMemoryStore();
        LtmOwner owner = LtmOwner.user("u-extract");
        int n = LtmExtractionApplier.apply(store, owner, ops, "s", "r", "memory_flush");
        Assert.assertEquals(1, n);
        Assert.assertEquals(1, store.listActive(owner, CuratedMemoryScope.USER).size());
    }

    @Test
    public void emptyArrayNoWrite() {
        Assert.assertTrue(LtmExtractionApplier.parseOps("[]").isEmpty());
    }
}
