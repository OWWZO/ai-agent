package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.memory.PromptMemoryMessage;
import org.wwz.ai.domain.agent.memory.PromptMemoryProjector;
import org.wwz.ai.domain.agent.runtime.dto.Memory;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;

import java.util.Collections;
import java.util.List;

/**
 * Prompt Memory 投影测试。
 */
public class PromptMemoryProjectorTest {

    private final PromptMemoryProjector projector = new PromptMemoryProjector();

    @Test
    public void shouldRoundTripAllModelVisibleMessageFields() {
        List<Message> messages = List.of(
                Message.userMessage("请分析这张图", "data:image/png;base64,user-image"),
                Message.fromToolCalls("我先检索资料", List.of(toolCall("call-search", "search", "{\"query\":\"prompt cache\"}"))),
                Message.toolMessage("检索完成", "call-search", "data:image/png;base64,tool-image"),
                Message.assistantMessage("这是结论", null)
        );

        List<PromptMemoryMessage> rows = projector.project(messages, 0);
        List<Message> hydrated = projector.hydrate(rows);
        Memory memory = new Memory();
        memory.replaceMessages(hydrated);
        hydrated.clear();

        Assert.assertEquals(4, memory.size());
        assertMessageEquals(messages.get(0), memory.get(0));
        assertMessageEquals(messages.get(1), memory.get(1));
        assertMessageEquals(messages.get(2), memory.get(2));
        assertMessageEquals(messages.get(3), memory.get(3));
        Assert.assertEquals("{\"query\":\"prompt cache\"}", memory.get(1).getToolCalls().get(0).getFunction().getArguments());
    }

    @Test
    public void shouldProjectOnlyMessagesAfterBaseline() {
        List<Message> messages = List.of(
                Message.userMessage("历史问题", null),
                Message.assistantMessage("历史回答", null),
                Message.userMessage("新问题", "data:image/png;base64,new-image"),
                Message.assistantMessage("新回答", null)
        );

        List<Message> hydrated = projector.hydrate(projector.project(messages, 2));

        Assert.assertEquals(2, hydrated.size());
        assertMessageEquals(messages.get(2), hydrated.get(0));
        assertMessageEquals(messages.get(3), hydrated.get(1));
    }

    @Test
    public void shouldTrimIncompleteAssistantToolCallSuffix() {
        Message completedUser = Message.userMessage("先完成这一步", null);
        Message incompleteToolCalls = Message.fromToolCalls("调用两个工具", List.of(
                toolCall("call-a", "search", "{\"q\":\"a\"}"),
                toolCall("call-b", "search", "{\"q\":\"b\"}")
        ));
        Message onlyOneToolResponse = Message.toolMessage("只返回了第一个", "call-a", null);

        List<Message> valid = projector.validPrefix(List.of(completedUser, incompleteToolCalls, onlyOneToolResponse));
        List<Message> hydrated = projector.hydrate(projector.project(
                List.of(completedUser, incompleteToolCalls, onlyOneToolResponse), 0));

        Assert.assertEquals(1, valid.size());
        assertMessageEquals(completedUser, valid.get(0));
        Assert.assertEquals(1, hydrated.size());
        assertMessageEquals(completedUser, hydrated.get(0));
    }

    @Test
    public void shouldTrimToolCallSuffixWhenNonToolMessageBreaksResponseBlock() {
        Message completedUser = Message.userMessage("已完成的历史", null);
        Message toolCalls = Message.fromToolCalls("调用工具", List.of(toolCall("call-a", "search", "{\"q\":\"a\"}")));
        Message interveningUser = Message.userMessage("这是一条不应跨越的消息", null);
        Message lateToolResponse = Message.toolMessage("过晚的响应", "call-a", null);

        List<Message> valid = projector.validPrefix(List.of(completedUser, toolCalls, interveningUser, lateToolResponse));

        Assert.assertEquals(1, valid.size());
        assertMessageEquals(completedUser, valid.get(0));
    }

    @Test
    public void shouldRejectMalformedOrUnexpectedToolResponses() {
        Message toolCalls = Message.fromToolCalls("调用工具", List.of(toolCall("call-a", "search", "{}")));

        assertIllegalArgument(() -> projector.validPrefix(List.of(
                toolCalls,
                Message.toolMessage("无效", " ", null)
        )));
        assertIllegalArgument(() -> projector.validPrefix(List.of(
                toolCalls,
                Message.toolMessage("未知", "call-b", null)
        )));
        assertIllegalArgument(() -> projector.validPrefix(List.of(
                toolCalls,
                Message.toolMessage("首次响应", "call-a", null),
                Message.toolMessage("重复响应", "call-a", null)
        )));
    }

    @Test
    public void shouldRejectMalformedPersistedRows() {
        assertIllegalArgument(() -> projector.hydrate(Collections.singletonList(null)));
        assertIllegalArgument(() -> projector.hydrate(List.of(PromptMemoryMessage.builder()
                .content("缺失角色")
                .build())));
    }

    @Test
    public void shouldCopyToolCallsWhenProjectingMessages() {
        Message toolCalls = Message.fromToolCalls("调用工具", List.of(toolCall("call-a", "search", "{\"q\":\"original\"}")));
        Message toolResponse = Message.toolMessage("已完成", "call-a", null);

        List<PromptMemoryMessage> rows = projector.project(List.of(toolCalls, toolResponse), 0);
        toolCalls.getToolCalls().get(0).getFunction().setArguments("{\"q\":\"mutated\"}");

        Assert.assertEquals("{\"q\":\"original\"}", projector.hydrate(rows).get(0).getToolCalls().get(0).getFunction().getArguments());
    }

    private ToolCall toolCall(String id, String name, String arguments) {
        return ToolCall.builder()
                .id(id)
                .type("function")
                .function(ToolCall.Function.builder()
                        .name(name)
                        .arguments(arguments)
                        .build())
                .build();
    }

    private void assertMessageEquals(Message expected, Message actual) {
        Assert.assertEquals(expected.getRole(), actual.getRole());
        Assert.assertEquals(expected.getContent(), actual.getContent());
        Assert.assertEquals(expected.getBase64Image(), actual.getBase64Image());
        Assert.assertEquals(expected.getToolCallId(), actual.getToolCallId());
        if (expected.getToolCalls() == null) {
            Assert.assertNull(actual.getToolCalls());
            return;
        }
        Assert.assertEquals(expected.getToolCalls().size(), actual.getToolCalls().size());
        for (int i = 0; i < expected.getToolCalls().size(); i++) {
            ToolCall expectedToolCall = expected.getToolCalls().get(i);
            ToolCall actualToolCall = actual.getToolCalls().get(i);
            Assert.assertEquals(expectedToolCall.getId(), actualToolCall.getId());
            Assert.assertEquals(expectedToolCall.getType(), actualToolCall.getType());
            Assert.assertEquals(expectedToolCall.getFunction().getName(), actualToolCall.getFunction().getName());
            Assert.assertEquals(expectedToolCall.getFunction().getArguments(), actualToolCall.getFunction().getArguments());
        }
    }

    private void assertIllegalArgument(Runnable action) {
        try {
            action.run();
            Assert.fail("应拒绝无效的提示词记忆消息");
        } catch (IllegalArgumentException ignored) {
            // 预期的输入校验异常。
        }
    }
}
