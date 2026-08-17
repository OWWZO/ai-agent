package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.dto.tool.McpToolInfo;
import org.wwz.ai.domain.agent.runtime.tool.mcp.runtime.DeferredMcpCatalog;

import java.util.List;

/**
 * Deferred MCP 目录：名称列表 / available-deferred-tools 块。
 */
public class DeferredMcpCatalogTest {

    @Test
    public void formatsNamesOnlyExcludingAlwaysLoad() {
        McpToolInfo deferred = McpToolInfo.builder()
                .name("mcp__demo__alpha")
                .originalName("alpha")
                .desc("search documents")
                .parameters("{\"type\":\"object\",\"properties\":{\"q\":{\"type\":\"string\"}}}")
                .serverKey("demo")
                .alwaysLoad(false)
                .build();
        McpToolInfo always = McpToolInfo.builder()
                .name("mcp__demo__always")
                .originalName("always")
                .desc("always load")
                .parameters("{}")
                .serverKey("demo")
                .alwaysLoad(true)
                .build();
        DeferredMcpCatalog catalog = new DeferredMcpCatalog(List.of(deferred, always));

        String block = catalog.formatAvailableDeferredToolsBlock();
        Assert.assertTrue(block.contains("<available-deferred-tools>"));
        Assert.assertTrue(block.contains("mcp__demo__alpha"));
        Assert.assertFalse(block.contains("mcp__demo__always"));
        Assert.assertFalse(block.contains("properties"));
        Assert.assertEquals("mcp__demo__alpha", catalog.deferredNamesSignature());
    }

    @Test
    public void emptyWhenNoDeferredTools() {
        McpToolInfo always = McpToolInfo.builder()
                .name("mcp__demo__always")
                .alwaysLoad(true)
                .build();
        DeferredMcpCatalog catalog = new DeferredMcpCatalog(List.of(always));
        Assert.assertEquals("", catalog.formatAvailableDeferredToolsBlock());
        Assert.assertEquals("", catalog.deferredNamesSignature());
    }
}
