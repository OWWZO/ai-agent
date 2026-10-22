package org.wwz.ai.test.domain.yield;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.yield.PendingYieldToolCalls;

import java.util.ArrayList;
import java.util.List;

/**
 * 让出类工具的 tool_call 配对查询与 waiting 占位。
 * <p>
 * AskUserQuestion / RequestDesktopControl / ExitPlanMode 三条链路共用这里：
 * yield 时 memory 里只有 assistant.tool_calls、尚无 tool result，必须先补一条
 * waiting 占位，否则 OpenAI-compatible 网关会因 tool_call 未配对直接 400。
 */
public class PendingYieldToolCallsTest {

    private static final String WAITING = "{\"status\":\"waiting_user_input\"}";

    @Test
    public void appendsWaitingToolResultWhenNonePresent() {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.fromToolCalls("准备询问", List.of(toolCall("call-1", "AskUserQuestion"))));

        PendingYieldToolCalls.appendWaitingToolResult(messages, "call-1", WAITING);

        Assert.assertEquals(2, messages.size());
        Message placeholder = messages.get(1);
        Assert.assertEquals(org.wwz.ai.domain.agent.runtime.enums.RoleType.TOOL, placeholder.getRole());
        Assert.assertEquals("call-1", placeholder.getToolCallId());
        Assert.assertTrue(placeholder.getContent().contains("waiting_user_input"));
    }

    @Test
    public void doesNotDuplicateWaitingWhenToolResultAlreadyPresent() {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.fromToolCalls("准备询问", List.of(toolCall("call-1", "AskUserQuestion"))));
        messages.add(Message.toolMessage("用户的回答", "call-1", null));

        PendingYieldToolCalls.appendWaitingToolResult(messages, "call-1", WAITING);

        Assert.assertEquals("已有 tool result 时不得再补占位", 2, messages.size());
    }

    @Test
    public void skipsForBlankToolCallId() {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.fromToolCalls("准备询问", List.of(toolCall("call-1", "AskUserQuestion"))));

        PendingYieldToolCalls.appendWaitingToolResult(messages, "  ", WAITING);
        PendingYieldToolCalls.appendWaitingToolResult(messages, null, WAITING);

        Assert.assertEquals(1, messages.size());
    }

    @Test
    public void skipsForNnullMessageList() {
        PendingYieldToolCalls.appendWaitingToolResult(null, "call-1", WAITING);
    }

    @Test
    public void hasToolResultDetectsAnyToolMessage() {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.toolMessage("占位", "call-1", null));

        Assert.assertTrue(PendingYieldToolCalls.hasToolResult(messages, "call-1"));
        Assert.assertFalse(PendingYieldToolCalls.hasToolResult(messages, "call-2"));
        Assert.assertFalse(PendingYieldToolCalls.hasToolResult(null, "call-1"));
        Assert.assertFalse(PendingYieldToolCalls.hasToolResult(messages, "  "));
    }

    @Test
    public void resolvePrefersExplicitToolCallId() {
        Assert.assertEquals("call-9",
                PendingYieldToolCalls.resolve(new ArrayList<>(), " call-9 ", "AskUserQuestion", false));
    }

    @Test
    public void resolveFindsLatestUnansweredToolCallByName() {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.fromToolCalls("旧的", List.of(toolCall("old", "AskUserQuestion"))));
        messages.add(Message.toolMessage("已回答", "old", null));
        messages.add(Message.fromToolCalls("新的", List.of(toolCall("new", "AskUserQuestion"))));

        Assert.assertEquals("new",
                PendingYieldToolCalls.resolve(messages, null, "AskUserQuestion", false));
    }

    @Test
    public void resolveIgnoresWaitingPlaceholderWhenMatchingAnswered() {
        // waiting 占位不是真回答：resolve 的 answeredIds 会跳过它，
        // 因此同一条 tool_call 仍应被视为未配对
        List<Message> messages = new ArrayList<>();
        messages.add(Message.fromToolCalls("准备", List.of(toolCall("call-1", "AskUserQuestion"))));
        messages.add(Message.toolMessage(WAITING, "call-1", null));

        Assert.assertEquals("call-1",
                PendingYieldToolCalls.resolve(messages, null, "AskUserQuestion", false));
    }

    @Test
    public void resolveSkipsToolCallWithMissingIdUnlessAssigning() {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.fromToolCalls("准备", List.of(toolCall("", "AskUserQuestion"))));

        Assert.assertNull("不分配 id 时跳过缺失 id 的 tool_call",
                PendingYieldToolCalls.resolve(messages, null, "AskUserQuestion", false));

        String assigned = PendingYieldToolCalls.resolve(messages, null, "AskUserQuestion", true);
        Assert.assertNotNull("assignMissingId 时应补一个 id", assigned);
        Assert.assertEquals(assigned, messages.get(0).getToolCalls().get(0).getId());
    }

    @Test
    public void resolveReturnsNnullForOtherToolNames() {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.fromToolCalls("准备", List.of(toolCall("call-1", "ExitPlanMode"))));

        Assert.assertNull(PendingYieldToolCalls.resolve(messages, null, "AskUserQuestion", true));
    }

    private static ToolCall toolCall(String id, String name) {
        return ToolCall.builder()
                .id(id)
                .type("function")
                .function(ToolCall.Function.builder()
                        .name(name)
                        .arguments("{}")
                        .build())
                .build();
    }
}
