package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.wwz.ai.application.agent.authorization.ConversationSessionAuthorizationService;
import org.wwz.ai.application.agent.authorization.SessionNotInteractiveException;
import org.wwz.ai.application.agent.authorization.SessionOwnershipDeniedException;
import org.wwz.ai.domain.agent.ledger.entity.DialogueSession;
import org.wwz.ai.domain.agent.ledger.model.DialogueSessionUpsertRecord;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.SessionKind;
import org.wwz.ai.domain.agent.memory.ltm.LtmManager;

/**
 * 会话归属应用服务测试。
 */
public class ConversationSessionAuthorizationServiceTest {

    @SuppressWarnings("unchecked")
    private static ConversationSessionAuthorizationService newService(
            ExecutionLedgerFixtureFactory.LedgerTestContext ctx) {
        ObjectProvider<LtmManager> ltm = Mockito.mock(ObjectProvider.class);
        Mockito.when(ltm.getIfAvailable()).thenReturn(null);
        return new ConversationSessionAuthorizationService(
                ctx.readRepository,
                ctx.writeRepository,
                ltm
        );
    }

    @Test
    public void shouldBindSessionToFirstUser() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        ConversationSessionAuthorizationService service = newService(ctx);

        DialogueSession session = service.ensureSessionAccessible("user-001", "session-001", "帮我总结项目结构");

        Assert.assertNotNull(session);
        Assert.assertEquals("user-001", session.getUserId());
        Assert.assertEquals("session-001", session.getSessionId());
        Assert.assertEquals("帮我总结项目结构", session.getTitle());
        Assert.assertEquals("user-001", ctx.readRepository.querySessionEntity("session-001").getUserId());
    }

    @Test
    public void shouldAllowRepeatedAccessForSameUser() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        ConversationSessionAuthorizationService service = newService(ctx);
        service.ensureSessionAccessible("user-001", "session-001", "第一次进入");

        DialogueSession session = service.ensureSessionAccessible("user-001", "session-001", "再次进入");

        Assert.assertNotNull(session);
        Assert.assertEquals("user-001", session.getUserId());
        Assert.assertEquals("session-001", session.getSessionId());
    }

    @Test(expected = SessionOwnershipDeniedException.class)
    public void shouldRejectCrossUserAccess() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        ConversationSessionAuthorizationService service = newService(ctx);
        service.ensureSessionAccessible("user-001", "session-001", "第一次进入");

        service.ensureSessionAccessible("user-002", "session-001", "尝试越权访问");
    }

    @Test
    public void shouldRejectMissingSessionWithExplicitMessage() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        ConversationSessionAuthorizationService service = newService(ctx);

        try {
            service.ensureExistingSessionAccessible("user-001", "session-missing-001");
            Assert.fail("缺失会话应被拒绝");
        } catch (SessionOwnershipDeniedException exception) {
            Assert.assertEquals("当前会话不存在", exception.getMessage());
        }
    }

    @Test
    public void shouldAllowOwnerToReadSubAgentSession() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        seedSubAgentSession(ctx, "user-001", "sub_agent-1", "parent-001");
        ConversationSessionAuthorizationService service = newService(ctx);

        DialogueSession session = service.ensureExistingSessionAccessible("user-001", "sub_agent-1");

        Assert.assertNotNull("owner 应能读取子会话 history", session);
        Assert.assertEquals("user-001", session.getUserId());
        // 读取路径返回最小归属投影，但必须带上身份列，否则只读闸门无从判定
        Assert.assertEquals(SessionKind.SUBAGENT.code(), session.getSessionKind());
        // parent_session_id 属深列，读取路径不取；用全量查询单独校验
        Assert.assertEquals("parent-001",
                ctx.readRepository.querySessionEntity("sub_agent-1").getParentSessionId());
    }

    @Test
    public void shouldRejectCrossUserReadOfSubAgentSession() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        seedSubAgentSession(ctx, "user-001", "sub_agent-2", "parent-001");
        ConversationSessionAuthorizationService service = newService(ctx);

        try {
            service.ensureExistingSessionAccessible("user-002", "sub_agent-2");
            Assert.fail("跨用户读取子会话应被拒绝");
        } catch (SessionOwnershipDeniedException expected) {
            Assert.assertEquals("当前用户无权访问该会话", expected.getMessage());
        }
    }

    @Test
    public void shouldRejectSubmittingQueryOnSubAgentSession() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        seedSubAgentSession(ctx, "user-001", "sub_agent-3", "parent-001");
        ConversationSessionAuthorizationService service = newService(ctx);

        try {
            service.ensureSessionAccessible("user-001", "sub_agent-3", "继续这个子会话");
            Assert.fail("子会话不得作为交互会话提交新 query");
        } catch (SessionNotInteractiveException expected) {
            Assert.assertTrue(expected.getMessage().contains("只读会话"));
        }
    }

    @Test
    public void shouldRejectResumeOnSubAgentSession() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        seedSubAgentSession(ctx, "user-001", "sub_agent-4", "parent-001");
        ConversationSessionAuthorizationService service = newService(ctx);

        try {
            service.ensureInteractiveSessionAccessible("user-001", "sub_agent-4");
            Assert.fail("子会话不得被 resume");
        } catch (SessionNotInteractiveException expected) {
            Assert.assertTrue(expected.getMessage().contains("只读会话"));
        }
    }

    @Test
    public void shouldAllowInteractiveAccessForConversationSession() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        ConversationSessionAuthorizationService service = newService(ctx);
        service.ensureSessionAccessible("user-001", "session-conv-1", "第一次进入");

        DialogueSession session = service.ensureInteractiveSessionAccessible("user-001", "session-conv-1");

        Assert.assertNotNull(session);
        Assert.assertEquals(SessionKind.CONVERSATION.code(), session.getSessionKind());
    }

    private static void seedSubAgentSession(ExecutionLedgerFixtureFactory.LedgerTestContext ctx,
                                            String userId,
                                            String sessionId,
                                            String parentSessionId) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        ctx.writeRepository.upsertSession(DialogueSessionUpsertRecord.builder()
                .sessionId(sessionId)
                .userId(userId)
                .sessionKind(SessionKind.SUBAGENT.code())
                .parentSessionId(parentSessionId)
                .title("子任务")
                .status(ExecutionLedgerConstants.STATUS_RUNNING)
                .runCount(0)
                .finishedRunCount(0)
                .failedRunCount(0)
                .startedAt(now)
                .lastActiveAt(now)
                .build());
    }
}
