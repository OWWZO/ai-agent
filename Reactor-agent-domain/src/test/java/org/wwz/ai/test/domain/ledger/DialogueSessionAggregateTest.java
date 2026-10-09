package org.wwz.ai.test.domain.ledger;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.ledger.entity.DialogueRun;
import org.wwz.ai.domain.agent.ledger.entity.DialogueSession;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.aggregate.DialogueSessionAggregate;

import java.time.LocalDateTime;

/**
 * DialogueSessionAggregate 聚合行为测试：会话与 run 的联合状态转移。
 */
public class DialogueSessionAggregateTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 8, 12, 0, 0);

    @Test
    public void startRunShouldCreateRunAndSyncSessionCount() {
        DialogueSession session = DialogueSession.builder().sessionId("s1").userId("u1").build();
        DialogueSessionAggregate aggregate = DialogueSessionAggregate.of(session, null);

        DialogueRun run = aggregate.startRun("req-1", "问题", "react", NOW);

        Assert.assertEquals("req-1", run.getRequestId());
        Assert.assertEquals("s1", run.getSessionId());
        Assert.assertEquals("u1", run.getUserId());
        Assert.assertEquals("react", run.getEntryAgent());
        Assert.assertTrue(run.isRunning());
        Assert.assertEquals(Integer.valueOf(1), session.getRunCount());
        Assert.assertEquals(1, aggregate.runs().size());
        Assert.assertSame(run, aggregate.findRun("req-1"));
    }

    @Test
    public void startRunShouldRejectBlankRequestId() {
        DialogueSessionAggregate aggregate = DialogueSessionAggregate.of(
                DialogueSession.builder().sessionId("s1").build(), null);
        try {
            aggregate.startRun("  ", "问题", NOW);
            Assert.fail("requestId 不能为空");
        } catch (IllegalArgumentException expected) {
            // 预期
        }
    }

    @Test
    public void finishRunSuccessShouldUpdateBothEntities() {
        DialogueSession session = DialogueSession.builder().sessionId("s1").build();
        DialogueSessionAggregate aggregate = DialogueSessionAggregate.of(session, null);
        aggregate.startRun("req-1", "问题", NOW);

        DialogueRun run = aggregate.finishRun("req-1", ExecutionLedgerConstants.STATUS_SUCCESS, "总结", NOW.plusSeconds(3));

        Assert.assertEquals(Integer.valueOf(ExecutionLedgerConstants.STATUS_SUCCESS), run.getStatus());
        Assert.assertTrue(run.isTerminal());
        Assert.assertEquals(Integer.valueOf(1), session.getFinishedRunCount());
        Assert.assertEquals(Integer.valueOf(0), session.getFailedRunCount());
        Assert.assertEquals("总结", session.getLatestSummaryText());
    }

    @Test
    public void finishRunFailedShouldIncrementFailedCount() {
        DialogueSession session = DialogueSession.builder().sessionId("s1").build();
        DialogueSessionAggregate aggregate = DialogueSessionAggregate.of(session, null);
        aggregate.startRun("req-1", "问题", NOW);

        DialogueRun run = aggregate.finishRun("req-1", ExecutionLedgerConstants.STATUS_FAILED, "失败", NOW.plusSeconds(1));

        Assert.assertEquals(Integer.valueOf(ExecutionLedgerConstants.STATUS_FAILED), run.getStatus());
        Assert.assertEquals(Integer.valueOf(1), session.getFinishedRunCount());
        Assert.assertEquals(Integer.valueOf(1), session.getFailedRunCount());
        session.assertInvariants();
    }

    @Test
    public void finishRunStoppedShouldMarkStopped() {
        DialogueSession session = DialogueSession.builder().sessionId("s1").build();
        DialogueSessionAggregate aggregate = DialogueSessionAggregate.of(session, null);
        aggregate.startRun("req-1", "问题", NOW);

        DialogueRun run = aggregate.finishRun("req-1", ExecutionLedgerConstants.STATUS_STOPPED, "用户停止", NOW.plusSeconds(1));

        Assert.assertEquals(Integer.valueOf(ExecutionLedgerConstants.STATUS_STOPPED), run.getStatus());
        Assert.assertEquals(Integer.valueOf(1), session.getFinishedRunCount());
        Assert.assertEquals(Integer.valueOf(0), session.getFailedRunCount());
    }

    @Test
    public void finishRunShouldRejectUnknownRun() {
        DialogueSessionAggregate aggregate = DialogueSessionAggregate.of(
                DialogueSession.builder().sessionId("s1").build(), null);
        try {
            aggregate.finishRun("missing", ExecutionLedgerConstants.STATUS_SUCCESS, "总结", NOW);
            Assert.fail("找不到对应 run 应报错");
        } catch (IllegalStateException expected) {
            // 预期
        }
    }

    @Test
    public void multipleRunsShouldKeepCountersConsistent() {
        DialogueSession session = DialogueSession.builder().sessionId("s1").build();
        DialogueSessionAggregate aggregate = DialogueSessionAggregate.of(session, null);

        aggregate.startRun("req-1", "问题1", NOW);
        aggregate.finishRun("req-1", ExecutionLedgerConstants.STATUS_SUCCESS, "总结1", NOW.plusSeconds(1));
        aggregate.startRun("req-2", "问题2", NOW.plusMinutes(1));
        aggregate.finishRun("req-2", ExecutionLedgerConstants.STATUS_FAILED, "失败2", NOW.plusMinutes(2));

        Assert.assertEquals(Integer.valueOf(2), session.getRunCount());
        Assert.assertEquals(Integer.valueOf(2), session.getFinishedRunCount());
        Assert.assertEquals(Integer.valueOf(1), session.getFailedRunCount());
        Assert.assertEquals("req-2", session.getLatestRequestId());
        session.assertInvariants();
    }
}
