package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.runtime.cancel.RunCancellation;

import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class RunCancellationAbortTest {

    @Test
    public void shouldRunAbortHookImmediatelyWhenCancelled() {
        RunCancellation cancellation = new RunCancellation();
        AtomicInteger runs = new AtomicInteger();

        cancellation.cancel(RunCancellation.REASON_USER_STOP);
        Runnable unregister = cancellation.registerAbort(runs::incrementAndGet);

        Assert.assertEquals(1, runs.get());
        unregister.run();
        cancellation.cancel("again");
        Assert.assertEquals("注销后的钩子不能再次执行", 1, runs.get());
    }

    @Test
    public void shouldAbortInFlightWorkWhenCancelArrives() {
        RunCancellation cancellation = new RunCancellation();
        AtomicInteger runs = new AtomicInteger();
        Runnable unregister = cancellation.registerAbort(runs::incrementAndGet);

        Assert.assertTrue(cancellation.cancel(RunCancellation.REASON_USER_STOP));
        Assert.assertEquals(1, runs.get());
        unregister.run();
        Assert.assertFalse(cancellation.cancel(RunCancellation.REASON_USER_STOP));
        Assert.assertEquals(1, runs.get());
    }

    @Test
    public void shouldTreatCancellationAsStoppedAndTimeoutAsTimeout() {
        Assert.assertEquals(ExecutionLedgerConstants.STATUS_STOPPED,
                ExecutionLedgerConstants.resolveFailureStatus(new CancellationException("user_stop")));
        Assert.assertEquals(ExecutionLedgerConstants.STATUS_STOPPED,
                ExecutionLedgerConstants.resolveFailureStatus(new RuntimeException(new InterruptedException("stop"))));
        Assert.assertEquals(ExecutionLedgerConstants.STATUS_TIMEOUT,
                ExecutionLedgerConstants.resolveFailureStatus(new TimeoutException("llm")));
        Assert.assertEquals(ExecutionLedgerConstants.STATUS_FAILED,
                ExecutionLedgerConstants.resolveFailureStatus(new IOException("reset")));
    }
}
