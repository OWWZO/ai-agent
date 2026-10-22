package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.mcp.model.McpToolInfo;
import org.wwz.ai.domain.agent.runtime.llm.LlmToolCallbackProvider;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.common.mcp.McpToolNames;
import org.wwz.ai.domain.agent.runtime.tool.common.mcp.ToolSearchTool;
import org.wwz.ai.domain.agent.runtime.tool.deferred.DeferredToolCatalog;
import org.wwz.ai.domain.agent.runtime.tool.deferred.DeferredToolEntry;
import org.wwz.ai.domain.agent.runtime.tool.mcp.model.McpToolNamePolicy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP FQ 命名 + ToolSearch 不改 tools[]。
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
    public void toolSearchShouldNotAttachActivatedToolsToCollection() {
        McpToolInfo tool = remoteTool();
         DeferredToolCatalog catalog = new DeferredToolCatalog(List.of(DeferredToolEntry.mcp(
                 tool, "demo", Map.of("type", "object", "properties", Map.of()), "")));
         ToolCollection collection = new ToolCollection();
         collection.setDeferredToolCatalog(catalog);
        AgentContext context = AgentContext.builder()
                .requestId("r1")
                .sessionId("s1")
                .toolCollection(collection)
                .build();
        collection.setAgentContext(context);

        String signatureBefore = LlmToolCallbackProvider.buildToolSignature(collection);
        ToolSearchTool searchTool = new ToolSearchTool();
        searchTool.setAgentContext(context);
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("query", "select:mcp__demo__remote_tool");
        Object result = searchTool.execute(input);

        Assert.assertTrue(result instanceof ToolResultPayload);
        Assert.assertFalse(collection.getMcpToolMap().containsKey("mcp__demo__remote_tool"));
        Assert.assertEquals(signatureBefore, LlmToolCallbackProvider.buildToolSignature(collection));
        Assert.assertTrue(catalog.contains("mcp__demo__remote_tool"));
    }

    @Test
    public void executeShouldHintToolCallWhenDeferred() {
        McpToolInfo tool = remoteTool();
         DeferredToolCatalog catalog = new DeferredToolCatalog(List.of(DeferredToolEntry.mcp(
                 tool, "demo", Map.of("type", "object", "properties", Map.of()), "")));
         ToolCollection collection = new ToolCollection();
         collection.setDeferredToolCatalog(catalog);
        AgentContext context = AgentContext.builder()
                .requestId("r1")
                .toolCollection(collection)
                .build();
        collection.setAgentContext(context);

        Object result = collection.execute("mcp__demo__remote_tool", Map.of());
        String text = String.valueOf(result);
        Assert.assertTrue(text.contains(McpToolNames.TOOL_CALL));
        Assert.assertFalse(text.contains("select:"));
    }

    private static McpToolInfo remoteTool() {
        return McpToolInfo.builder()
                .name("mcp__demo__remote_tool")
                .originalName("remote_tool")
                .desc("远程工具")
                .parameters("{}")
                .serverKey("demo")
                .mcpId("mcp-1")
                .build();
    }
}
