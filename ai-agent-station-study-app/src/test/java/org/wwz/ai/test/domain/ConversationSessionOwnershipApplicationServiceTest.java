package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.application.agent.visitor.ConversationSessionOwnershipApplicationService;
import org.wwz.ai.application.agent.visitor.SessionOwnershipDeniedException;
import org.wwz.ai.domain.agent.ledger.entity.DialogueSession;

/**
 * 会话归属应用服务测试。
 */
public class ConversationSessionOwnershipApplicationServiceTest {

    @Test
    public void shouldBindSessionToFirstVisitor() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        ConversationSessionOwnershipApplicationService service = new ConversationSessionOwnershipApplicationService(
                ctx.readRepository,
                ctx.writeRepository
        );

        DialogueSession session = service.ensureSessionAccessible("visitor-001", "session-001", "帮我总结项目结构");

        Assert.assertNotNull(session);
        Assert.assertEquals("visitor-001", session.getVisitorId());
        Assert.assertEquals("session-001", session.getSessionId());
        Assert.assertEquals("帮我总结项目结构", session.getTitle());
        Assert.assertEquals("visitor-001", ctx.readRepository.querySessionEntity("session-001").getVisitorId());
    }

    @Test
    public void shouldAllowRepeatedAccessForSameVisitor() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        ConversationSessionOwnershipApplicationService service = new ConversationSessionOwnershipApplicationService(
                ctx.readRepository,
                ctx.writeRepository
        );
        service.ensureSessionAccessible("visitor-001", "session-001", "第一次进入");

        DialogueSession session = service.ensureSessionAccessible("visitor-001", "session-001", "再次进入");

        Assert.assertNotNull(session);
        Assert.assertEquals("visitor-001", session.getVisitorId());
        Assert.assertEquals("session-001", session.getSessionId());
    }

    @Test(expected = SessionOwnershipDeniedException.class)
    public void shouldRejectCrossVisitorAccess() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        ConversationSessionOwnershipApplicationService service = new ConversationSessionOwnershipApplicationService(
                ctx.readRepository,
                ctx.writeRepository
        );
        service.ensureSessionAccessible("visitor-001", "session-001", "第一次进入");

        service.ensureSessionAccessible("visitor-002", "session-001", "尝试越权访问");
    }

    @Test
    public void shouldRejectMissingSessionWithExplicitMessage() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        ConversationSessionOwnershipApplicationService service = new ConversationSessionOwnershipApplicationService(
                ctx.readRepository,
                ctx.writeRepository
        );

        try {
            service.ensureExistingSessionAccessible("visitor-001", "session-missing-001");
            Assert.fail("缺失会话应被拒绝");
        } catch (SessionOwnershipDeniedException exception) {
            Assert.assertEquals("当前会话不存在", exception.getMessage());
        }
    }
}
