package org.wwz.ai.test.application.agent.run;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.application.agent.run.AgentRunLaunchGate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class AgentRunLaunchGateTest {

    @Test
    public void launchBySessionRunsOnce() {
        AgentRunLaunchGate gate = new AgentRunLaunchGate(60_000L);
        AtomicInteger launches = new AtomicInteger();
        try {
            gate.defer("req-1", "sess-1", launches::incrementAndGet);
            gate.launchBySession("sess-1");
            gate.launchBySession("sess-1");
            gate.launchNow("req-1");
            Assert.assertEquals(1, launches.get());
        } finally {
            gate.shutdown();
        }
    }

    @Test
    public void immediateGateLaunchesOnDefer() {
        AgentRunLaunchGate gate = new AgentRunLaunchGate(0);
        AtomicInteger launches = new AtomicInteger();
        try {
            gate.defer("req-2", "sess-2", launches::incrementAndGet);
            Assert.assertEquals(1, launches.get());
        } finally {
            gate.shutdown();
        }
    }

    @Test
    public void cancelPreventsLaunch() throws Exception {
        AgentRunLaunchGate gate = new AgentRunLaunchGate(50L);
        CountDownLatch latch = new CountDownLatch(1);
        try {
            gate.defer("req-3", "sess-3", latch::countDown);
            gate.cancel("req-3");
            Assert.assertFalse(latch.await(120, TimeUnit.MILLISECONDS));
        } finally {
            gate.shutdown();
        }
    }
}
