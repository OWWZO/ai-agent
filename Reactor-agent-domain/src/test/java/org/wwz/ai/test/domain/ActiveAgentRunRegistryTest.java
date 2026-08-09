package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.adapter.port.AgentMessageStream;
import org.wwz.ai.domain.agent.runtime.cancel.ActiveAgentRunRegistry;
import org.wwz.ai.domain.agent.runtime.cancel.RunCancellation;
import org.wwz.ai.types.agent.exception.AgentConcurrentRunException;

/**
 * 活跃 Agent run 与观察流绑定关系的回归测试。
 */
public class ActiveAgentRunRegistryTest {

    @Test
    public void shouldDetachStreamWithoutCancellingRunWhenClientDisconnects() {
        ActiveAgentRunRegistry registry = new ActiveAgentRunRegistry();
        registry.begin("req-disconnect", "session-1", "visitor-1");
        AbortableStream stream = new AbortableStream();

        registry.bindStream("req-disconnect", stream);
        stream.abort();

        ActiveAgentRunRegistry.ActiveRun run = registry.find("req-disconnect").orElseThrow();
        Assert.assertFalse("客户端断开不应取消后台 run", run.getCancellation().isCancelled());
        Assert.assertNull("客户端断开后应解绑观察流", run.getStream());
    }

    @Test
    public void shouldNotLetOldStreamAbortDetachNewStream() {
        ActiveAgentRunRegistry registry = new ActiveAgentRunRegistry();
        registry.begin("req-rebind", "session-1", "visitor-1");
        AbortableStream oldStream = new AbortableStream();
        AbortableStream newStream = new AbortableStream();

        registry.bindStream("req-rebind", oldStream);
        registry.bindStream("req-rebind", newStream);
        oldStream.abort();

        Assert.assertSame("旧连接断开不应清理新连接", newStream,
                registry.find("req-rebind").orElseThrow().getStream());
    }

    @Test
    public void shouldCancelRunWhenUserExplicitlyStops() {
        ActiveAgentRunRegistry registry = new ActiveAgentRunRegistry();
        registry.begin("req-stop", "session-1", "visitor-1");

        Assert.assertTrue(registry.cancel("req-stop", RunCancellation.REASON_USER_STOP));
        Assert.assertTrue("显式停止仍应取消后台 run", registry.isCancelled("req-stop"));
    }

    @Test
    public void shouldRejectSecondRunForSameVisitor() {
        ActiveAgentRunRegistry registry = new ActiveAgentRunRegistry();
        registry.begin("req-a", "session-a", "visitor-1");

        try {
            registry.begin("req-b", "session-b", "visitor-1");
            Assert.fail("同一 visitor 第二路 run 应被拒绝");
        } catch (AgentConcurrentRunException e) {
            Assert.assertEquals("req-a", e.getActiveRequestId());
            Assert.assertEquals("session-a", e.getActiveSessionId());
            Assert.assertTrue(e.getMessage().contains("已有任务"));
        }

        Assert.assertTrue(registry.find("req-a").isPresent());
        Assert.assertFalse(registry.find("req-b").isPresent());
    }

    @Test
    public void shouldAllowAnotherVisitorConcurrently() {
        ActiveAgentRunRegistry registry = new ActiveAgentRunRegistry();
        registry.begin("req-a", "session-a", "visitor-1");
        registry.begin("req-b", "session-b", "visitor-2");

        Assert.assertTrue(registry.find("req-a").isPresent());
        Assert.assertTrue(registry.find("req-b").isPresent());
    }

    @Test
    public void shouldAllowNewRunAfterEnd() {
        ActiveAgentRunRegistry registry = new ActiveAgentRunRegistry();
        registry.begin("req-a", "session-a", "visitor-1");
        registry.end("req-a");
        registry.begin("req-b", "session-b", "visitor-1");

        Assert.assertFalse(registry.find("req-a").isPresent());
        Assert.assertTrue(registry.find("req-b").isPresent());
        Assert.assertEquals("req-b", registry.findByVisitorId("visitor-1").orElseThrow().getRequestId());
    }

    private static class AbortableStream implements AgentMessageStream {

        private Runnable abortHandler;

        @Override
        public void send(Object payload) {
        }

        @Override
        public void complete() {
        }

        @Override
        public void completeWithError(Throwable throwable) {
        }

        @Override
        public void onAbort(Runnable abortHandler) {
            this.abortHandler = abortHandler;
        }

        private void abort() {
            if (abortHandler != null) {
                abortHandler.run();
            }
        }
    }
}
