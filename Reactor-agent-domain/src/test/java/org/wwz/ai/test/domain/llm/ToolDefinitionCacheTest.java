package org.wwz.ai.test.domain.llm;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.llm.ToolDefinitionCache;
import org.wwz.ai.domain.agent.runtime.llm.LlmToolDefinition;

public class ToolDefinitionCacheTest {

    @Test
    public void reusesSameDefinitionForSameContent() {
        LlmToolDefinition first = ToolDefinitionCache.getOrCreate("cache-test", "description", "{}");
        LlmToolDefinition second = ToolDefinitionCache.getOrCreate("cache-test", "description", "{}");

        Assert.assertSame(first, second);
    }

    @Test
    public void maximumSizePreventsUnboundedGrowth() {
        for (int i = 0; i < 4100; i++) {
            ToolDefinitionCache.getOrCreate("cache-test-" + i, "description", "{}");
        }

        Assert.assertTrue(ToolDefinitionCache.size() <= 4096);
    }
}
