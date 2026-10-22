package org.wwz.ai.test.domain.ledger;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.ledger.entity.DialogueRun;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;

import java.time.LocalDateTime;

/**
 * DialogueRun 充血行为与状态机测试。
 */
public class DialogueRunBehaviorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 8, 12, 0, 0);

    @Test
    public void startShouldRequireRequestId() {
        DialogueRun run = DialogueRun.builder().build();
        try {
            run.start(NOW);
            Assert.fail("requestId 不能为空");
        } catch (IllegalStateException expected) {
            // 预期
        }
    }

    @Test
    public void startShouldEnterRunningAndResetCounters() {
        DialogueRun run = DialogueRun.builder()
                .requestId("req-1")
                .llmCallCount(9)
                .toolCallCount(8)
                .artifactCount(7)
                .promptTokensTotal(6)
                .completionTokensTotal(5)
                .totalTokensTotal(4)
                .build();
        run.start(NOW);

        Assert.assertEquals(Integer.valueOf(ExecutionLedgerConstants.STATUS_RUNNING), run.getStatus());
        Assert.assertEquals(Integer.valueOf(0), run.getLlmCallCount());
        Assert.assertEquals(Integer.valueOf(0), run.getToolCallCount());
        Assert.assertEquals(Integer.valueOf(0), run.getArtifactCount());
        Assert.assertEquals(Integer.valueOf(0), run.getPromptTokensTotal());
        Assert.assertEquals(Integer.valueOf(0), run.getCompletionTokensTotal());
        Assert.assertEquals(Integer.valueOf(0), run.getTotalTokensTotal());
        Assert.assertEquals(NOW, run.getStartedAt());
        Assert.assertFalse(run.isTerminal());
        Assert.assertTrue(run.isRunning());
    }

    @Test
    public void finishSuccessShouldSetTerminalStateAndDuration() {
        DialogueRun run = DialogueRun.builder().requestId("req-1").build();
        run.start(NOW);
        run.finishSuccess("完成", NOW.plusSeconds(5));

        Assert.assertEquals(Integer.valueOf(ExecutionLedgerConstants.STATUS_SUCCESS), run.getStatus());
        Assert.assertEquals("完成", run.getFinalSummaryText());
        Assert.assertEquals(NOW.plusSeconds(5), run.getFinishedAt());
        Assert.assertEquals(Long.valueOf(5000L), run.getDurationMs());
        Assert.assertTrue(run.isTerminal());
        Assert.assertTrue(run.isFinished());
    }

    @Test
    public void finishFailedShouldCarryErrorInfo() {
        DialogueRun run = DialogueRun.builder().requestId("req-1").build();
        run.start(NOW);
        run.finishFailed("E_500", "内部错误", NOW.plusSeconds(1));

        Assert.assertEquals(Integer.valueOf(ExecutionLedgerConstants.STATUS_FAILED), run.getStatus());
        Assert.assertEquals("E_500", run.getErrorCode());
        Assert.assertEquals("内部错误", run.getErrorMsg());
        Assert.assertTrue(run.isTerminal());
    }

    @Test
    public void finishStoppedShouldMarkUserStop() {
        DialogueRun run = DialogueRun.builder().requestId("req-1").build();
        run.start(NOW);
        run.finishStopped("用户停止本轮对话", NOW.plusSeconds(2));

        Assert.assertEquals(Integer.valueOf(ExecutionLedgerConstants.STATUS_STOPPED), run.getStatus());
        Assert.assertEquals("USER_STOP", run.getErrorCode());
        Assert.assertEquals("用户停止本轮对话", run.getErrorMsg());
        Assert.assertTrue(run.isTerminal());
    }

    @Test
    public void finishTwiceShouldThrow() {
        DialogueRun run = DialogueRun.builder().requestId("req-1").build();
        run.start(NOW);
        run.finishSuccess("完成", NOW);
        try {
            run.finishFailed("E", "again", NOW);
            Assert.fail("终态 run 不能重复结束");
        } catch (IllegalStateException expected) {
            // 预期
        }
    }

    @Test
    public void waitingInputShouldNotBeTerminal() {
        DialogueRun run = DialogueRun.builder()
                .requestId("req-1")
                .status(ExecutionLedgerConstants.STATUS_WAITING_INPUT)
                .build();
        Assert.assertFalse(run.isTerminal());
        Assert.assertFalse(run.isRunning());
    }

    @Test
    public void nullStatusShouldNotBeTerminal() {
        DialogueRun run = DialogueRun.builder().requestId("req-1").build();
        Assert.assertFalse(run.isTerminal());
    }
}
