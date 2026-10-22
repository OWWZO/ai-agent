package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.llm.LlmToolCallbackProvider;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.common.mcp.ToolCallTool;
import org.wwz.ai.domain.agent.runtime.tool.common.mcp.ToolDescribeTool;
import org.wwz.ai.domain.agent.runtime.tool.common.mcp.ToolSearchTool;
import org.wwz.ai.domain.agent.runtime.tool.deferred.DeferredToolCatalog;
import org.wwz.ai.domain.agent.runtime.tool.deferred.DeferredToolEntry;
import org.wwz.ai.domain.agent.runtime.tool.deferred.DeferredToolCall;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DeferredLocalToolCallTest {

    @Test
    public void localDeferredToolUsesSearchDescribeAndCall() {
        LocalTool local = new LocalTool();
        ToolCollection collection = new ToolCollection();
        collection.setDeferredToolCatalog(new DeferredToolCatalog(List.of(localEntry(local))));
        AgentContext context = AgentContext.builder().toolCollection(collection).build();
        collection.setAgentContext(context);

        ToolSearchTool search = new ToolSearchTool();
        search.setAgentContext(context);
        Map<?, ?> searchData = (Map<?, ?>) ((ToolResultPayload) search.execute(
                Map.of("query", "generate document"))).getLlmData();
        Map<?, ?> row = (Map<?, ?>) ((List<?>) searchData.get("matches")).get(0);
        Assert.assertEquals("document_generate", row.get("name"));
        Assert.assertEquals("local", row.get("source"));
        Assert.assertEquals("reactor", row.get("source_name"));
        Assert.assertFalse(row.containsKey("parameters"));

        ToolDescribeTool describe = new ToolDescribeTool();
        describe.setAgentContext(context);
        Map<?, ?> described = (Map<?, ?>) ((ToolResultPayload) describe.execute(
                Map.of("name", "document_generate"))).getLlmData();
        Assert.assertTrue(((Map<?, ?>) described.get("parameters")).containsKey("required"));

        Assert.assertTrue(String.valueOf(collection.execute(
                "document_generate", Map.of("title", "x"))).contains("ToolCall"));

        Map<String, Object> call = new LinkedHashMap<>();
        call.put("name", "document_generate");
        call.put("arguments", Map.of("title", "x"));
        DeferredToolCall.Result resolved = DeferredToolCall.resolve(collection, call);
        Assert.assertTrue(resolved.ok());
        Assert.assertEquals("generated:x", collection.executeResolved(resolved.name(), resolved.arguments()));
        Assert.assertEquals(1, local.invocations);

        ToolCallTool bridge = new ToolCallTool();
        bridge.setAgentContext(context);
        Assert.assertEquals("generated:x", bridge.execute(call));
        Assert.assertEquals(2, local.invocations);
        Assert.assertFalse(LlmToolCallbackProvider.buildToolSignature(collection)
                .contains("document_generate"));
    }

    @Test
    public void requiredArgumentsAreCheckedBeforeLocalExecution() {
        LocalTool local = new LocalTool();
        ToolCollection collection = new ToolCollection();
        collection.setDeferredToolCatalog(new DeferredToolCatalog(List.of(localEntry(local))));

        DeferredToolCall.Result result = DeferredToolCall.resolve(collection, Map.of(
                "name", "document_generate",
                "arguments", Map.of()));
        Assert.assertFalse(result.ok());
        Assert.assertEquals(0, local.invocations);
    }

    private static DeferredToolEntry localEntry(LocalTool local) {
        return DeferredToolEntry.local(local, "reactor", local.toParams(), "");
    }

    private static final class LocalTool implements BaseTool {
        private int invocations;

        @Override
        public String getName() {
            return "document_generate";
        }

        @Override
        public String getDescription() {
            return "generate document";
        }

        @Override
        public Map<String, Object> toParams() {
            return Map.of(
                    "type", "object",
                    "properties", Map.of("title", Map.of("type", "string")),
                    "required", List.of("title"));
        }

        @Override
        public Object execute(Object input) {
            invocations++;
            return "generated:" + ((Map<?, ?>) input).get("title");
        }
    }
}
