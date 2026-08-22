package org.wwz.ai.test.stream;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.application.agent.query.GptQueryApplicationService;
import org.wwz.ai.application.agent.stream.AgentResponseProjectionStream;
import org.wwz.ai.application.agent.stream.AgentSessionPrinter;
import org.wwz.ai.application.agent.stream.AgentSessionStream;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.reactor.model.response.AgentResponse;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tasklist.RuntimeBackgroundTask;
import org.wwz.ai.domain.agent.runtime.tasklist.SessionBackgroundTaskHub;
import org.wwz.ai.domain.agent.runtime.tool.common.AgentDispatchTool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 后台任务未完成时主 result 不得关闭 SSE；stream_settle 才关闭。
 */
public class BackgroundStreamSettleTest {

    @Test
    public void rootResultDefersFinishWhileBackgroundRunning() {
        String sessionId = "sess-bg-defer-" + System.nanoTime();
        SessionBackgroundTaskHub.evict(sessionId);
        try {
            SessionBackgroundTaskHub.getOrCreate(sessionId, null)
                    .registerLocalAgent("后台探查", "Explore", "scan");

            CapturingStream stream = new CapturingStream();
            AgentRequest request = new AgentRequest();
            request.setRequestId("req-bg-defer");
            request.setSessionId(sessionId);
            AgentSessionPrinter printer = new AgentSessionPrinter(stream, request, 5);

            HashMap<String, Object> summary = new HashMap<>();
            summary.put("taskSummary", "主结论");
            printer.send("result", summary);

            Assert.assertEquals(1, stream.payloads.size());
            AgentResponse response = (AgentResponse) stream.payloads.get(0);
            Assert.assertEquals("result", response.getMessageType());
            Assert.assertFalse(Boolean.TRUE.equals(response.getFinish()));
            Assert.assertFalse(stream.completed.get());
        } finally {
            SessionBackgroundTaskHub.evict(sessionId);
        }
    }

    @Test
    public void streamSettleFinishesEvenWithNoBackground() {
        String sessionId = "sess-bg-settle-" + System.nanoTime();
        SessionBackgroundTaskHub.evict(sessionId);
        try {
            CapturingStream stream = new CapturingStream();
            AgentRequest request = new AgentRequest();
            request.setRequestId("req-bg-settle");
            request.setSessionId(sessionId);
            AgentSessionPrinter printer = new AgentSessionPrinter(stream, request, 5);

            HashMap<String, Object> settle = new HashMap<>();
            settle.put("reason", "background_idle");
            printer.send("stream_settle", settle);

            Assert.assertEquals(1, stream.payloads.size());
            AgentResponse response = (AgentResponse) stream.payloads.get(0);
            Assert.assertEquals("stream_settle", response.getMessageType());
            Assert.assertTrue(Boolean.TRUE.equals(response.getFinish()));
        } finally {
            SessionBackgroundTaskHub.evict(sessionId);
        }
    }

    @Test
    public void hasRunningReflectsRegistry() {
        String sessionId = "sess-bg-running-" + System.nanoTime();
        SessionBackgroundTaskHub.evict(sessionId);
        try {
            Assert.assertFalse(SessionBackgroundTaskHub.hasRunning(sessionId));
            RuntimeBackgroundTask task = SessionBackgroundTaskHub.getOrCreate(sessionId, null)
                    .registerLocalAgent("t", "Explore", "p");
            Assert.assertTrue(SessionBackgroundTaskHub.hasRunning(sessionId));
            SessionBackgroundTaskHub.getOrCreate(sessionId, null).complete(task.getId(), null);
            Assert.assertFalse(SessionBackgroundTaskHub.hasRunning(sessionId));
        } finally {
            SessionBackgroundTaskHub.evict(sessionId);
        }
    }

    @Test
    public void requestIdFallbackUsesSameBackgroundTaskKey() {
        String requestId = "req-bg-key-" + System.nanoTime();
        SessionBackgroundTaskHub.evict(requestId);
        try {
            RuntimeBackgroundTask task = SessionBackgroundTaskHub.getOrCreate(requestId, null)
                    .registerLocalAgent("后台探查", "Explore", "scan");

            Assert.assertTrue(SessionBackgroundTaskHub.hasRunning(null, requestId));
            SessionBackgroundTaskHub.getOrCreate(requestId, null).complete(task.getId(), null);
            Assert.assertFalse(SessionBackgroundTaskHub.hasRunning(null, requestId));
        } finally {
            SessionBackgroundTaskHub.evict(requestId);
        }
    }

    @Test
    public void backgroundIdleDoesNotSettleWhileParentTurnOpen() {
        String sessionId = "sess-bg-parent-open-" + System.nanoTime();
        SessionBackgroundTaskHub.evict(sessionId);
        try {
            AgentContext parent = AgentContext.builder()
                    .requestId("req-bg-parent-open")
                    .sessionId(sessionId)
                    .build();
            Assert.assertFalse(AgentDispatchTool.shouldSettleParentStream(parent));

            RuntimeBackgroundTask task = SessionBackgroundTaskHub.getOrCreate(sessionId, null)
                    .registerLocalAgent("后台探查", "Explore", "scan");
            SessionBackgroundTaskHub.getOrCreate(sessionId, null).complete(task.getId(), null);
            Assert.assertFalse("父 run 未 finish 时不得 settle",
                    AgentDispatchTool.shouldSettleParentStream(parent));
        } finally {
            SessionBackgroundTaskHub.evict(sessionId);
        }
    }

    @Test
    public void backgroundIdleSettlesAfterParentTurnClosed() {
        String sessionId = "sess-bg-parent-closed-" + System.nanoTime();
        SessionBackgroundTaskHub.evict(sessionId);
        try {
            AgentContext parent = AgentContext.builder()
                    .requestId("req-bg-parent-closed")
                    .sessionId(sessionId)
                    .build();
            RuntimeBackgroundTask task = SessionBackgroundTaskHub.getOrCreate(sessionId, null)
                    .registerLocalAgent("后台探查", "Explore", "scan");
            parent.markTurnClosed();
            Assert.assertFalse(AgentDispatchTool.shouldSettleParentStream(parent));

            SessionBackgroundTaskHub.getOrCreate(sessionId, null).complete(task.getId(), null);
            Assert.assertTrue(AgentDispatchTool.shouldSettleParentStream(parent));
        } finally {
            SessionBackgroundTaskHub.evict(sessionId);
        }
    }

    @Test
    public void gptQueryDefersProjectionCompleteWhileBackgroundRunning() {
        String sessionId = "sess-bg-gpt-defer-" + System.nanoTime();
        SessionBackgroundTaskHub.evict(sessionId);
        try {
            RuntimeBackgroundTask task = SessionBackgroundTaskHub.getOrCreate(sessionId, null)
                    .registerLocalAgent("后台探查", "Explore", "scan");

            CapturingStream downstream = new CapturingStream();
            AgentRequest request = new AgentRequest();
            request.setRequestId("req-bg-gpt-defer");
            request.setSessionId(sessionId);
            AgentResponseProjectionStream projecting =
                    new AgentResponseProjectionStream(downstream, request, Map.of());

            GptQueryApplicationService.completeProjectionUnlessBackgroundRunning(request, projecting);

            Assert.assertFalse("后台运行时不得关闭投影流", downstream.completed.get());

            SessionBackgroundTaskHub.getOrCreate(sessionId, null).complete(task.getId(), null);
            GptQueryApplicationService.completeProjectionUnlessBackgroundRunning(request, projecting);
            Assert.assertTrue("后台结束后应关闭投影流", downstream.completed.get());
        } finally {
            SessionBackgroundTaskHub.evict(sessionId);
        }
    }

    private static final class CapturingStream implements AgentSessionStream {
        private final List<Object> payloads = new ArrayList<>();
        private final AtomicBoolean completed = new AtomicBoolean(false);

        @Override
        public void send(Object payload) {
            payloads.add(payload);
        }

        @Override
        public void complete() {
            completed.set(true);
        }

        @Override
        public void completeWithError(Throwable throwable) {
            completed.set(true);
        }
    }
}
