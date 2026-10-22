package org.wwz.ai.test.domain.ledger;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.ledger.entity.DialogueSession;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;

import java.time.LocalDateTime;

/**
 * DialogueSession 充血行为与不变量测试。
 */
public class DialogueSessionBehaviorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 8, 12, 0, 0);

    @Test
    public void bindOwnerShouldBindWhenUnbound() {
        DialogueSession session = DialogueSession.builder().sessionId("s1").build();
        session.bindOwner("user-1");
        Assert.assertEquals("user-1", session.getUserId());
    }

    @Test
    public void bindOwnerShouldBeIdempotentForSameUser() {
        DialogueSession session = DialogueSession.builder().sessionId("s1").userId("user-1").build();
        session.bindOwner("user-1");
        Assert.assertEquals("user-1", session.getUserId());
    }

    @Test
    public void bindOwnerShouldRejectDifferentUser() {
        DialogueSession session = DialogueSession.builder().sessionId("s1").userId("user-1").build();
        try {
            session.bindOwner("user-2");
            Assert.fail("已绑定会话不能被其他用户覆盖");
        } catch (IllegalStateException expected) {
            Assert.assertEquals("user-1", session.getUserId());
        }
    }

    @Test
    public void bindOwnerShouldIgnoreBlank() {
        DialogueSession session = DialogueSession.builder().sessionId("s1").build();
        session.bindOwner("  ");
        Assert.assertNull(session.getUserId());
    }

    @Test
    public void renameShouldUpdateNonBlankTitleOnly() {
        DialogueSession session = DialogueSession.builder().sessionId("s1").title("旧标题").build();
        session.rename("新标题");
        Assert.assertEquals("新标题", session.getTitle());
        session.rename("   ");
        Assert.assertEquals("新标题", session.getTitle());
    }

    @Test
    public void startRunShouldIncrementCountAndRefreshLatest() {
        DialogueSession session = DialogueSession.builder().sessionId("s1").build();
        session.startRun("req-1", "第一个问题", NOW);

        Assert.assertEquals(Integer.valueOf(1), session.getRunCount());
        Assert.assertEquals(Integer.valueOf(ExecutionLedgerConstants.STATUS_RUNNING), session.getStatus());
        Assert.assertEquals("req-1", session.getLatestRequestId());
        Assert.assertEquals("第一个问题", session.getLatestQueryText());
        Assert.assertEquals(NOW, session.getStartedAt());
        Assert.assertEquals(NOW, session.getLastActiveAt());
    }

    @Test
    public void startRunShouldKeepOriginalStartedAt() {
        LocalDateTime earlier = NOW.minusDays(1);
        DialogueSession session = DialogueSession.builder().sessionId("s1").startedAt(earlier).build();
        session.startRun("req-2", "第二轮", NOW);
        Assert.assertEquals(earlier, session.getStartedAt());
    }

    @Test
    public void finishRunShouldIncrementFinishedCountAndSetSummary() {
        DialogueSession session = DialogueSession.builder().sessionId("s1").build();
        session.startRun("req-1", "问题", NOW);
        session.finishRun(ExecutionLedgerConstants.STATUS_SUCCESS, "总结", NOW.plusMinutes(1));

        Assert.assertEquals(Integer.valueOf(1), session.getFinishedRunCount());
        Assert.assertEquals(Integer.valueOf(ExecutionLedgerConstants.STATUS_SUCCESS), session.getStatus());
        Assert.assertEquals("总结", session.getLatestSummaryText());
        Assert.assertEquals(NOW.plusMinutes(1), session.getLastActiveAt());
    }

    @Test
    public void recordFailedRunShouldRequireFinishedRun() {
        DialogueSession session = DialogueSession.builder().sessionId("s1").build();
        session.startRun("req-1", "问题", NOW);
        // 尚未 finish 就记失败，违反 failedRunCount <= finishedRunCount
        try {
            session.recordFailedRun(NOW);
            Assert.fail("未结束的 run 不允许记为失败");
        } catch (IllegalStateException expected) {
            Assert.assertEquals(Integer.valueOf(0), session.getFailedRunCount());
        }

        session.finishRun(ExecutionLedgerConstants.STATUS_FAILED, "失败", NOW);
        session.recordFailedRun(NOW);
        Assert.assertEquals(Integer.valueOf(1), session.getFailedRunCount());
    }

    @Test
    public void assertInvariantsShouldRejectFinishedGreaterThanRuns() {
        DialogueSession session = DialogueSession.builder()
                .sessionId("s1")
                .runCount(1)
                .finishedRunCount(2)
                .build();
        try {
            session.assertInvariants();
            Assert.fail("finishedRunCount 不得超过 runCount");
        } catch (IllegalStateException expected) {
            // 预期
        }
    }

    @Test
    public void assertInvariantsShouldRejectFailedGreaterThanFinished() {
        DialogueSession session = DialogueSession.builder()
                .sessionId("s1")
                .runCount(2)
                .finishedRunCount(1)
                .failedRunCount(2)
                .build();
        try {
            session.assertInvariants();
            Assert.fail("failedRunCount 不得超过 finishedRunCount");
        } catch (IllegalStateException expected) {
            // 预期
        }
    }

    @Test
    public void nullCountsShouldBeTreatedAsZero() {
        DialogueSession session = DialogueSession.builder().sessionId("s1").build();
        session.startRun("req-1", "问题", NOW);
        session.finishRun(ExecutionLedgerConstants.STATUS_SUCCESS, "总结", NOW);
        session.assertInvariants();
        Assert.assertEquals(Integer.valueOf(1), session.getRunCount());
        Assert.assertEquals(Integer.valueOf(1), session.getFinishedRunCount());
    }
}
