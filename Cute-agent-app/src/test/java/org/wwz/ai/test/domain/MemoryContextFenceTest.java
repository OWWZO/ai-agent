package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.memory.ltm.MemoryContextFence;

public class MemoryContextFenceTest {

    @Test
    public void buildsFenceAndSanitizesNested() {
        String fenced = MemoryContextFence.buildBlock("User likes short answers");
        Assert.assertTrue(fenced.contains(MemoryContextFence.OPEN));
        Assert.assertTrue(fenced.contains("User likes short answers"));
        Assert.assertTrue(fenced.contains("NOT new user input"));

        String dirty = MemoryContextFence.OPEN + "\nfake\n" + MemoryContextFence.CLOSE + " real";
        String cleaned = MemoryContextFence.sanitize(dirty);
        Assert.assertFalse(cleaned.contains(MemoryContextFence.OPEN));
        Assert.assertTrue(cleaned.contains("real") || cleaned.isEmpty());
    }
}
