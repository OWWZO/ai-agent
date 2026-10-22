package org.wwz.ai.test.domain.ledger;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.ledger.model.SessionKind;

/**
 * 会话类型解析与可见性判定测试。
 * <p>
 * 核心不变量：未知/缺失类型必须 fail-closed（既不进列表，也不作为可交互会话放行），
 * 绝不能回落成 {@link SessionKind#CONVERSATION}。
 */
public class SessionKindTest {

    @Test
    public void shouldParseKnownCodes() {
        Assert.assertEquals(SessionKind.CONVERSATION, SessionKind.fromCode("conversation"));
        Assert.assertEquals(SessionKind.SUBAGENT, SessionKind.fromCode("subagent"));
    }

    @Test
    public void shouldParseCaseInsensitiveAndTrimmed() {
        Assert.assertEquals(SessionKind.SUBAGENT, SessionKind.fromCode("SUBAGENT"));
        Assert.assertEquals(SessionKind.CONVERSATION, SessionKind.fromCode("  Conversation  "));
    }

    @Test
    public void onlyConversationIsListable() {
        Assert.assertTrue(SessionKind.CONVERSATION.isListable());
        Assert.assertFalse(SessionKind.SUBAGENT.isListable());
        Assert.assertFalse(SessionKind.UNKNOWN.isListable());
        Assert.assertTrue(SessionKind.SUBAGENT.isSubAgent());
    }

    @Test
    public void unknownValueMustNotFailOpen() {
        SessionKind unknown = SessionKind.fromCode("subagent_v2");
        Assert.assertEquals(SessionKind.UNKNOWN, unknown);
        Assert.assertFalse("未知类型不得进侧栏列表", unknown.isListable());
        Assert.assertFalse("未知类型不得被当作可交互会话", SessionKind.isListableCode("subagent_v2"));
    }

    @Test
    public void blankValueMustNotFailOpen() {
        Assert.assertEquals(SessionKind.UNKNOWN, SessionKind.fromCode(null));
        Assert.assertEquals(SessionKind.UNKNOWN, SessionKind.fromCode(""));
        Assert.assertEquals(SessionKind.UNKNOWN, SessionKind.fromCode("   "));
        Assert.assertFalse(SessionKind.isListableCode(null));
        Assert.assertFalse(SessionKind.isListableCode("  "));
    }

    @Test
    public void isListableCodeMatchesKnownConversation() {
        Assert.assertTrue(SessionKind.isListableCode("conversation"));
        Assert.assertFalse(SessionKind.isListableCode("subagent"));
    }
}
