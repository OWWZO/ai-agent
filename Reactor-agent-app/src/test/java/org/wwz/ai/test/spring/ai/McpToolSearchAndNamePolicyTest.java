package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.dto.tool.McpToolInfo;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.common.mcp.ToolSearchTool;
import org.wwz.ai.domain.agent.runtime.tool.mcp.runtime.DeferredMcpCatalog;
import org.wwz.ai.domain.agent.runtime.tool.mcp.runtime.McpToolNamePolicy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP FQ 命名 + ToolSearch / Deferred catalog 单测。
 */
public class McpToolSearchAndNamePolicyTest {

    @Test
    public void shouldBuildStableQualifiedNames() {
        Assert.assertEquals("mcp__csdn__search_articles",
                McpToolNamePolicy.buildQualifiedName("csdn", "search_articles"));
        Assert.assertEquals("mcp__my_server__tool_1",
                McpToolNamePolicy.buildQualifiedName("my server", "tool.1"));
        Assert.assertTrue(McpToolNamePolicy.isQualifiedName("mcp__demo__remote_tool"));
        Assert.assertFalse(McpToolNamePolicy.isQualifiedName("remote_tool"));
        Assert.assertEquals("demo", McpToolNamePolicy.parseServerKey("mcp__demo__remote_tool"));
    }

    @Test
    public void shouldFormatAvailableDeferredToolsNamesOnly() {
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
    public void shouldSearchSelectAndActivateDeferredTools() {
        McpToolInfo a = McpToolInfo.builder()
                .name("mcp__demo__alpha")
                .originalName("alpha")
                .desc("search documents")
                .parameters("{\"type\":\"object\"}")
                .serverKey("demo")
                .build();
        McpToolInfo b = McpToolInfo.builder()
                .name("mcp__demo__beta")
                .originalName("beta")
                .desc("write file")
                .parameters("{}")
                .serverKey("demo")
                .build();
        DeferredMcpCatalog catalog = new DeferredMcpCatalog(List.of(a, b));

        List<McpToolInfo> keyword = catalog.search("documents", 5);
        Assert.assertEquals(1, keyword.size());
        Assert.assertEquals("mcp__demo__alpha", keyword.get(0).getName());

        List<McpToolInfo> selected = catalog.search("select:mcp__demo__beta", 5);
        Assert.assertEquals(1, selected.size());
        List<McpToolInfo> activated = catalog.activate(List.of("mcp__demo__beta"));
        Assert.assertEquals(1, activated.size());
        Assert.assertTrue(catalog.isActivated("mcp__demo__beta"));
        Assert.assertFalse(catalog.isActivated("mcp__demo__alpha"));
    }

    @Test
    public void toolSearchShouldAttachActivatedToolsToCollection() {
        McpToolInfo tool = McpToolInfo.builder()
                .name("mcp__demo__remote_tool")
                .originalName("remote_tool")
                .desc("远程工具")
                .parameters("{}")
                .serverKey("demo")
                .mcpId("mcp-1")
                .build();
        DeferredMcpCatalog catalog = new DeferredMcpCatalog(List.of(tool));
        ToolCollection collection = new ToolCollection();
        AgentContext context = AgentContext.builder()
                .requestId("r1")
                .sessionId("s1")
                .deferredMcpCatalog(catalog)
                .toolCollection(collection)
                .build();
        collection.setAgentContext(context);

        ToolSearchTool searchTool = new ToolSearchTool();
        searchTool.setAgentContext(context);
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("query", "select:mcp__demo__remote_tool");
        Object result = searchTool.execute(input);

        Assert.assertTrue(result instanceof ToolResultPayload);
        Assert.assertTrue(collection.getMcpToolMap().containsKey("mcp__demo__remote_tool"));
        Assert.assertTrue(catalog.isActivated("mcp__demo__remote_tool"));
    }

    @Test
    public void executeShouldHintWhenDeferredToolNotActivated() {
        McpToolInfo tool = McpToolInfo.builder()
                .name("mcp__demo__remote_tool")
                .originalName("remote_tool")
                .desc("远程工具")
                .parameters("{}")
                .build();
        DeferredMcpCatalog catalog = new DeferredMcpCatalog(List.of(tool));
        ToolCollection collection = new ToolCollection();
        AgentContext context = AgentContext.builder()
                .requestId("r1")
                .deferredMcpCatalog(catalog)
                .toolCollection(collection)
                .build();
        collection.setAgentContext(context);

        Object result = collection.execute("mcp__demo__remote_tool", Map.of());
        Assert.assertTrue(String.valueOf(result).contains("ToolSearch"));
        Assert.assertTrue(String.valueOf(result).contains("select:mcp__demo__remote_tool"));
    }
}
