package org.wwz.ai.test.domain.llm;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.wwz.ai.domain.agent.runtime.llm.ToolDefinitionCache;

public class ToolDefinitionCacheTest {

    @Test
    public void reusesSameDefinitionForSameContent() {
        ToolDefinition first = ToolDefinitionCache.getOrCreate("cache-test", "description", "{}");
        ToolDefinition second = ToolDefinitionCache.getOrCreate("cache-test", "description", "{}");

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
