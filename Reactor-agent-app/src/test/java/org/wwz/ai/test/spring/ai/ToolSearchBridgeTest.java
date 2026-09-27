package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.BaseAgent;
import org.wwz.ai.domain.agent.runtime.dto.tool.McpToolInfo;
import org.wwz.ai.domain.agent.runtime.llm.LlmToolCallbackProvider;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentDefinition;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentToolFilter;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.common.mcp.McpToolNames;
import org.wwz.ai.domain.agent.runtime.tool.common.mcp.ToolCallTool;
import org.wwz.ai.domain.agent.runtime.tool.common.mcp.ToolDescribeTool;
import org.wwz.ai.domain.agent.runtime.tool.common.mcp.ToolSearchTool;
import org.wwz.ai.domain.agent.runtime.tool.deferred.DeferredToolCatalog;
import org.wwz.ai.domain.agent.runtime.tool.deferred.DeferredToolEntry;
import org.wwz.ai.domain.agent.runtime.tool.mcp.runtime.DeferredToolCall;
import org.wwz.ai.domain.agent.runtime.tool.mcp.runtime.McpToolExecutor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ToolSearch / ToolDescribe / ToolCall 三件套契约。
 */
public class ToolSearchBridgeTest {

    @Test
    public void searchDoesNotChangeToolSignatureOrAttachMcp() {
        Fixture fx = fixture();
        String before = LlmToolCallbackProvider.buildToolSignature(fx.collection);
        Map<String, Object> data = executeSearch(fx, "select:mcp__demo__remote_tool");
        Assert.assertEquals(before, LlmToolCallbackProvider.buildToolSignature(fx.collection));
        Assert.assertFalse(fx.collection.getMcpToolMap().containsKey("mcp__demo__remote_tool"));
        List<?> matches = (List<?>) data.get("matches");
        Assert.assertEquals(1, matches.size());
        Map<?, ?> row = (Map<?, ?>) matches.get(0);
        Assert.assertEquals("mcp__demo__remote_tool", row.get("name"));
        Assert.assertEquals("mcp", row.get("source"));
        Assert.assertFalse(row.containsKey("parameters"));
        Assert.assertFalse(data.containsKey("available_sources"));
    }

    @Test
    public void describeReturnsParameters() {
        Fixture fx = fixture();
        ToolDescribeTool describe = new ToolDescribeTool();
        describe.setAgentContext(fx.context);
        Object result = describe.execute(Map.of("name", "mcp__demo__remote_tool"));
        Map<String, Object> data = llmData(result);
        Assert.assertEquals("mcp__demo__remote_tool", data.get("name"));
        Assert.assertTrue(data.get("parameters") instanceof Map);
        Map<?, ?> parameters = (Map<?, ?>) data.get("parameters");
        Assert.assertTrue(parameters.containsKey("properties") || parameters.containsKey("type"));
    }

    @Test
    public void toolCallResolveDispatchesToExecutor() {
        Fixture fx = fixture();
        Mockito.when(fx.executor.executeTool(Mockito.any(), Mockito.any())).thenReturn("ok-remote");
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("name", "mcp__demo__remote_tool");
        args.put("arguments", Map.of("q", "hello"));
        DeferredToolCall.Result resolved = DeferredToolCall.resolve(fx.collection, args);
        Assert.assertTrue(resolved.ok());
        Object output = fx.collection.executeResolved(resolved.name(), resolved.arguments());
        Assert.assertEquals("ok-remote", output);
        Mockito.verify(fx.executor).executeTool(Mockito.argThat(info ->
                "remote_tool".equals(info.getOriginalName())), Mockito.eq(Map.of("q", "hello")));
    }

    @Test
    public void missingRequiredDoesNotDispatch() {
        Fixture fx = fixture();
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("name", "mcp__demo__remote_tool");
        args.put("arguments", Map.of());
        DeferredToolCall.Result resolved = DeferredToolCall.resolve(fx.collection, args);
        Assert.assertFalse(resolved.ok());
        String text = String.valueOf(resolved.errorPayload());
        Assert.assertTrue(text.contains("parameters") || String.valueOf(
                ((ToolResultPayload) resolved.errorPayload()).getLlmData()).contains("parameters"));
        Mockito.verifyNoInteractions(fx.executor);
    }

    @Test
    public void directDeferredExecuteFails() {
        Fixture fx = fixture();
        Object result = fx.collection.execute("mcp__demo__remote_tool", Map.of("q", "x"));
        Assert.assertTrue(String.valueOf(result).contains(McpToolNames.TOOL_CALL));
        Mockito.verifyNoInteractions(fx.executor);
    }

    @Test
    public void toolSearchDescriptionIsStableAndContainsListing() {
        Fixture fx = fixture();
        ToolSearchTool search = new ToolSearchTool();
        search.setAgentContext(fx.context);
        String first = search.getDescription();
        String second = search.getDescription();
        Assert.assertEquals(first, second);
        Assert.assertTrue(first.contains("mcp__demo__remote_tool"));
        Assert.assertTrue(first.contains("ToolDescribe"));
        Assert.assertTrue(first.contains("ToolCall"));
        Assert.assertFalse(first.contains("<available-deferred-tools>"));
    }

    @Test
    public void systemPromptDoesNotContainDeferredListing() {
        Fixture fx = fixture();
        ProbeAgent agent = new ProbeAgent();
        agent.setContext(fx.context);
        agent.setAvailableTools(fx.collection);
        agent.setName("probe");
        String system = agent.exposeSystem("You are a helper.");
        Assert.assertFalse(system.contains("<available-deferred-tools>"));
    }

    @Test
    public void allowAllChildKeepsFilteredCatalogAndTrio() {
        Fixture fx = fixture();
        fx.collection.addTool(searchTool(fx.context));
        fx.collection.addTool(describeTool(fx.context));
        fx.collection.addTool(callTool(fx.context));
        ToolCollection child = SubAgentToolFilter.filter(fx.collection, SubAgentDefinition.builder()
                .agentType("general-purpose")
                .allowedTools(Set.of("*"))
                .build());
        Assert.assertTrue(child.getToolMap().containsKey(McpToolNames.TOOL_SEARCH));
        Assert.assertTrue(child.getToolMap().containsKey(McpToolNames.TOOL_DESCRIBE));
        Assert.assertTrue(child.getToolMap().containsKey(McpToolNames.TOOL_CALL));
         Assert.assertEquals(1, child.getDeferredToolCatalog().size());
        Assert.assertFalse(child.getMcpToolMap().containsKey("mcp__demo__remote_tool"));
    }

    @Test
    public void explicitChildWithoutToolCallCannotReachDeferred() {
        Fixture fx = fixture();
        fx.collection.addTool(searchTool(fx.context));
        fx.collection.addTool(describeTool(fx.context));
        fx.collection.addTool(callTool(fx.context));
        ToolCollection child = SubAgentToolFilter.filter(fx.collection, SubAgentDefinition.builder()
                .agentType("reader")
                .allowedTools(Set.of("workspace_read"))
                .build());
         Assert.assertTrue(child.getDeferredToolCatalog() == null || child.getDeferredToolCatalog().size() == 0);
        Assert.assertFalse(child.getToolMap().containsKey(McpToolNames.TOOL_CALL));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> executeSearch(Fixture fx, String query) {
        ToolSearchTool search = new ToolSearchTool();
        search.setAgentContext(fx.context);
        Object result = search.execute(Map.of("query", query));
        return llmData(result);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> llmData(Object result) {
        Assert.assertTrue(result instanceof ToolResultPayload);
        Object data = ((ToolResultPayload) result).getLlmData();
        Assert.assertTrue(data instanceof Map);
        return (Map<String, Object>) data;
    }

    private static ToolSearchTool searchTool(AgentContext context) {
        ToolSearchTool tool = new ToolSearchTool();
        tool.setAgentContext(context);
        return tool;
    }

    private static ToolDescribeTool describeTool(AgentContext context) {
        ToolDescribeTool tool = new ToolDescribeTool();
        tool.setAgentContext(context);
        return tool;
    }

    private static ToolCallTool callTool(AgentContext context) {
        ToolCallTool tool = new ToolCallTool();
        tool.setAgentContext(context);
        return tool;
    }

    private static Fixture fixture() {
        McpToolInfo tool = McpToolInfo.builder()
                .name("mcp__demo__remote_tool")
                .originalName("remote_tool")
                .desc("远程工具")
                .parameters("{\"type\":\"object\",\"properties\":{\"q\":{\"type\":\"string\"}},\"required\":[\"q\"]}")
                .serverKey("demo")
                .mcpId("mcp-1")
                .build();
         DeferredToolCatalog catalog = new DeferredToolCatalog(List.of(DeferredToolEntry.mcp(
                 tool,
                 "demo",
                 Map.of("type", "object", "properties", Map.of("q", Map.of("type", "string")),
                         "required", List.of("q")),
                 "")));
        McpToolExecutor executor = Mockito.mock(McpToolExecutor.class);
        ToolCollection collection = new ToolCollection();
         collection.setDeferredToolCatalog(catalog);
        collection.setMcpToolExecutor(executor);
        AgentContext context = AgentContext.builder()
                .requestId("r1")
                .sessionId("s1")
                .toolCollection(collection)
                .build();
        collection.setAgentContext(context);
        return new Fixture(collection, context, executor);
    }

    private record Fixture(ToolCollection collection, AgentContext context, McpToolExecutor executor) {
    }

    private static final class ProbeAgent extends BaseAgent {
        @Override
        public String step() {
            return "ok";
        }

        String exposeSystem(String template) {
            return buildStableSystemPrompt(template);
        }
    }
}
