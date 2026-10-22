package org.wwz.ai.test.domain.llm;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;
import org.wwz.ai.domain.agent.runtime.llm.LlmMessage;
import org.wwz.ai.domain.agent.runtime.llm.LlmRequest;
import org.wwz.ai.domain.agent.runtime.llm.LlmResponse;
import org.wwz.ai.domain.agent.runtime.llm.LlmToolCall;
import org.wwz.ai.domain.agent.runtime.llm.LlmToolDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 跨 Adapter LLM 契约不可变性测试。
 * <p>契约模型必须对外不可变：集合/Map 防御性复制，调用方修改原始集合不得影响契约对象。
 */
public class LlmContractImmutabilityTest {

    @Test
    public void llmRequestShouldDefensivelyCopyMessagesAndMetadata() {
        List<LlmMessage> messages = new ArrayList<>();
        messages.add(LlmMessage.builder().role(RoleType.USER).content("hi").build());
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("k", "v");

        LlmRequest request = LlmRequest.builder()
                .model("m1")
                .messages(messages)
                .metadata(metadata)
                .build();

        // 修改原始集合不应影响契约对象
        messages.add(LlmMessage.builder().role(RoleType.USER).content("second").build());
        metadata.put("k2", "v2");

        Assert.assertEquals(1, request.getMessages().size());
        Assert.assertFalse(request.getMetadata().containsKey("k2"));
    }

    @Test
    public void llmRequestCollectionsShouldBeUnmodifiable() {
        LlmRequest request = LlmRequest.builder()
                .model("m1")
                .messages(new ArrayList<>())
                .build();
        try {
            request.getMessages().add(LlmMessage.builder().role(RoleType.USER).content("x").build());
            Assert.fail("契约集合必须不可变");
        } catch (UnsupportedOperationException expected) {
            // 预期
        }
    }

    @Test
    public void llmResponseShouldDefensivelyCopyToolCalls() {
        List<LlmToolCall> toolCalls = new ArrayList<>();
        toolCalls.add(LlmToolCall.builder().id("call-1").name("read").arguments("{}").build());

        LlmResponse response = LlmResponse.builder()
                .content("ok")
                .toolCalls(toolCalls)
                .build();

        toolCalls.clear();

        Assert.assertEquals(1, response.getToolCalls().size());
        try {
            response.getToolCalls().add(LlmToolCall.builder().id("call-2").name("write").arguments("{}").build());
            Assert.fail("契约集合必须不可变");
        } catch (UnsupportedOperationException expected) {
            // 预期
        }
    }

    @Test
    public void llmMessageShouldDefensivelyCopyToolCalls() {
        List<LlmToolCall> toolCalls = new ArrayList<>();
        toolCalls.add(LlmToolCall.builder().id("call-1").name("read").arguments("{}").build());

        LlmMessage message = LlmMessage.builder()
                .role(RoleType.ASSISTANT)
                .content("thinking")
                .toolCalls(toolCalls)
                .build();

        toolCalls.clear();
        Assert.assertEquals(1, message.getToolCalls().size());
    }

    @Test
    public void llmToolDefinitionWithInvokerShouldKeepOtherFields() {
        LlmToolDefinition definition = LlmToolDefinition.builder()
                .name("read")
                .description("读取")
                .inputSchema("{\"type\":\"object\"}")
                .build();

        LlmToolDefinition withInvoker = definition.withInvoker(input -> "done");

        Assert.assertEquals("read", withInvoker.getName());
        Assert.assertEquals("读取", withInvoker.getDescription());
        Assert.assertEquals("{\"type\":\"object\"}", withInvoker.getInputSchema());
        Assert.assertNotNull(withInvoker.getInvoker());
        Assert.assertNull(definition.getInvoker());
    }

    @Test
    public void nullCollectionsShouldStayNull() {
        LlmRequest request = LlmRequest.builder().model("m1").build();
        Assert.assertNull(request.getMessages());
        Assert.assertNull(request.getMetadata());
    }
}
