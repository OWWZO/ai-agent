package org.wwz.ai.domain.agent.runtime.agent;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.cancel.RunCancellation;
import org.wwz.ai.domain.agent.runtime.enums.AgentState;

import java.util.concurrent.atomic.AtomicInteger;

public class ReActCancellationBoundaryTest {

    @Test
    public void shouldSkipActWhenThinkIsCancelled() {
        AtomicInteger acts = new AtomicInteger();
        ProbeAgent agent = new ProbeAgent(false, acts);
        RunCancellation cancellation = new RunCancellation();
        cancellation.cancel(RunCancellation.REASON_USER_STOP);
        agent.setContext(AgentContext.builder()
                .requestId("think-cancelled")
                .runCancellation(cancellation)
                .build());

        Assert.assertEquals("Terminated: User stopped", agent.step());
        Assert.assertEquals(0, acts.get());
        Assert.assertEquals(AgentState.FINISHED, agent.getState());
    }

    @Test
    public void shouldRecordSkipWhenCancelledAfterThinkBeforeAct() {
        AtomicInteger acts = new AtomicInteger();
        ProbeAgent agent = new ProbeAgent(true, acts);
        RunCancellation cancellation = new RunCancellation();
        agent.setContext(AgentContext.builder()
                .requestId("between-think-and-act")
                .runCancellation(cancellation)
                .build());
        cancellation.cancel(RunCancellation.REASON_USER_STOP);

        Assert.assertEquals("Terminated: User stopped", agent.step());
        Assert.assertEquals("工具尚未开始时仍要走 act 写取消结果", 1, acts.get());
        Assert.assertEquals(AgentState.FINISHED, agent.getState());
    }

    private static final class ProbeAgent extends ReActAgent {
        private final boolean thinkResult;
        private final AtomicInteger acts;

        private ProbeAgent(boolean thinkResult, AtomicInteger acts) {
            this.thinkResult = thinkResult;
            this.acts = acts;
        }

        @Override
        public boolean think() {
            return thinkResult;
        }

        @Override
        public String act() {
            acts.incrementAndGet();
            return "acted";
        }
    }
}
