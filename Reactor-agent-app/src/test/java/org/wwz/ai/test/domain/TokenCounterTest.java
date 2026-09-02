package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.tool.McpToolInfo;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.llm.LlmAskToolProtocol;
import org.wwz.ai.domain.agent.runtime.llm.LlmPromptShapeFactory;
import org.wwz.ai.domain.agent.runtime.llm.PromptShape;
import org.wwz.ai.domain.agent.runtime.llm.TokenCounter;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TokenCounterTest {

    private final TokenCounter counter = new TokenCounter();

    @Test
    public void functionCallCountsToolsOnce() {
        ToolCollection tools = toolsWithSchema();
        Message system = Message.systemMessage("sys", null);
        List<Message> messages = List.of(Message.userMessage("hello", null));
        TokenCounter.PromptEstimate estimate = counter.estimatePrompt(
                PromptShape.functionCall(system, messages, tools));
        int toolsOnly = counter.estimateTools(tools);
        Assert.assertTrue(toolsOnly > 0);
        Assert.assertEquals(toolsOnly, estimate.getToolTokens());
        Assert.assertEquals(estimate.getSystemTokens() + estimate.getMessageTokens() + toolsOnly,
                estimate.getEstimatedTotalTokens());
        Assert.assertEquals(TokenCounter.SOURCE_LOCAL_ESTIMATE, estimate.getEstimateSource());
    }

    @Test
    public void structParseCountsToolSchemaOnlyInSystem() {
        ToolCollection tools = toolsWithSchema();
        Message system = Message.systemMessage("sys", null);
        List<Message> messages = List.of(Message.userMessage("hello", null));
        Message merged = LlmPromptShapeFactory.buildStructParseSystemMessage(system, tools);
        TokenCounter.PromptEstimate structEstimate = counter.estimatePrompt(
                PromptShape.structParse(merged, messages, tools));
        TokenCounter.PromptEstimate functionEstimate = counter.estimatePrompt(
                PromptShape.functionCall(system, messages, tools));
        Assert.assertEquals(0, structEstimate.getToolTokens());
        Assert.assertTrue(structEstimate.getSystemTokens() > functionEstimate.getSystemTokens());
        Assert.assertTrue(merged.getContent().contains("search"));
        Assert.assertTrue(merged.getContent().contains("query"));
        Assert.assertEquals(LlmAskToolProtocol.STRUCT_PARSE, PromptShape.structParse(merged, messages, tools).getProtocol());
    }

    @Test
    public void mcpParameterSchemaIsCounted() {
        ToolCollection withParams = new ToolCollection();
        withParams.addMcpTool(McpToolInfo.builder()
                .name("mcp_search")
                .desc("search")
                .parameters("{\"type\":\"object\",\"properties\":{\"q\":{\"type\":\"string\"}},\"required\":[\"q\"]}")
                .build());
        ToolCollection withoutParams = new ToolCollection();
        withoutParams.addMcpTool(McpToolInfo.builder()
                .name("mcp_search")
                .desc("search")
                .build());
        Assert.assertTrue(counter.estimateTools(withParams) > counter.estimateTools(withoutParams));
    }

    @Test
    public void reasoningContentIsCounted() {
        Message plain = Message.assistantMessage("answer", null);
        Message withReasoning = Message.assistantMessage("answer", "long hidden chain of thought here", null);
        Assert.assertTrue(counter.estimateOneMessage(withReasoning) > counter.estimateOneMessage(plain));
    }

    @Test
    public void imagesUseFixedMediaTokensNotBase64Length() {
        String hugeBase64 = "A".repeat(80_000);
        Message image = Message.userMessage("see this", hugeBase64);
        int tokens = counter.estimateOneMessage(image);
        Assert.assertTrue(tokens < 5_000);
        Assert.assertTrue(tokens >= TokenCounter.IMAGE_DEFAULT_TOKENS);
        Message dataUri = Message.builder()
                .role(org.wwz.ai.domain.agent.runtime.enums.RoleType.USER)
                .content("data:image/png;base64," + hugeBase64)
                .build();
        Assert.assertEquals(TokenCounter.IMAGE_DEFAULT_TOKENS + 4
                        + counter.estimateTokens("USER"),
                counter.estimateOneMessage(dataUri));
    }

    @Test
    public void estimateMessageDeltaOmitsFormatOverhead() {
        List<Message> messages = List.of(
                Message.userMessage("alpha", null),
                Message.assistantMessage("beta", null)
        );
        int full = counter.estimateMessages(messages);
        int delta = counter.estimateMessageDelta(messages);
        Assert.assertEquals(full - 2, delta);
        Assert.assertEquals(0, counter.estimateMessageDelta(List.of()));
    }

    @Test
    public void toolCallAndToolResultFieldsAreCounted() {
        Message assistant = Message.fromToolCalls("call", List.of(ToolCall.builder()
                .id("call-1")
                .type("function")
                .function(ToolCall.Function.builder().name("search").arguments("{\"q\":\"x\"}").build())
                .build()));
        Message tool = Message.toolMessage("result-body", "call-1", null);
        Assert.assertTrue(counter.estimateOneMessage(assistant) > counter.estimateOneMessage(
                Message.assistantMessage("call", null)));
        Assert.assertTrue(counter.estimateOneMessage(tool) > counter.estimateOneMessage(
                Message.toolMessage("", "call-1", null)));
    }

    @Test
    public void toolSchemaFingerprintChangesWithSchema() {
        ToolCollection a = toolsWithSchema();
        ToolCollection b = toolsWithSchema();
        b.addTool(new StubTool("other", "other desc", Map.of("type", "object")));
        Assert.assertNotEquals(counter.toolSchemaFingerprint(a), counter.toolSchemaFingerprint(b));
        Assert.assertEquals(counter.fingerprint(PromptShape.functionCall(null, List.of(), a)),
                counter.fingerprint(PromptShape.functionCall(null, List.of(), a)));
    }

    private static ToolCollection toolsWithSchema() {
        ToolCollection tools = new ToolCollection();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", Map.of("query", Map.of("type", "string")));
        params.put("required", List.of("query"));
        tools.addTool(new StubTool("search", "search the web", params));
        return tools;
    }

    private static final class StubTool implements BaseTool {
        private final String name;
        private final String description;
        private final Map<String, Object> params;

        private StubTool(String name, String description, Map<String, Object> params) {
            this.name = name;
            this.description = description;
            this.params = params;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public Map<String, Object> toParams() {
            return params;
        }

        @Override
        public Object execute(Object input) {
            return "ok";
        }
    }
}
