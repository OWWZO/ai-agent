package org.wwz.ai.test.stream;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.application.agent.query.GptQueryApplicationService;
import org.wwz.ai.application.agent.stream.AgentStreamProjection;
import org.wwz.ai.application.agent.stream.AgentSessionPrinter;
import org.wwz.ai.application.agent.stream.AgentSessionStream;
import org.wwz.ai.application.agent.stream.SessionProjectionRegistry;
import org.wwz.ai.domain.agent.runtime.command.AgentExecutionCommand;
import org.wwz.ai.domain.agent.runtime.stream.AgentStreamEvent;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.cancel.ActiveAgentRunRegistry;
import org.wwz.ai.domain.agent.runtime.tasklist.RuntimeBackgroundTask;
import org.wwz.ai.domain.agent.runtime.tasklist.SessionBackgroundTaskHub;
import org.wwz.ai.domain.agent.runtime.tasklist.StreamSettlePolicy;
import org.wwz.ai.domain.agent.runtime.tool.common.AgentDispatchTool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 后台子 Agent 结算与父 SSE 流收口。
 * <p>
 * 默认（{@link StreamSettlePolicy#deferBackgroundSettle() = false}，对齐 ZCode「账本权威、流可断」）：
 * root result 即 finished、投影即 complete，后台终态只落账本，父卡走 history 重拉。
 * {@code deferBackgroundSettle=true} 为旧行为：后台在飞时父流挂着等 stream_settle。
 * 两类断言都在，灰度期可一键回滚验证。
 */
public class BackgroundStreamSettleTest {

    private static void withDeferBackgroundSettle(boolean value, Runnable body) {
        boolean previous = StreamSettlePolicy.deferBackgroundSettle();
        StreamSettlePolicy.setDeferBackgroundSettle(value);
        try {
            body.run();
        } finally {
            StreamSettlePolicy.setDeferBackgroundSettle(previous);
        }
    }

    @Test
    public void rootResultFinishesWhileBackgroundRunning() {
        String sessionId = "sess-bg-defer-" + System.nanoTime();
        SessionBackgroundTaskHub.evict(sessionId);
        try {
            SessionBackgroundTaskHub.getOrCreate(sessionId, null)
                    .registerLocalAgent("后台探查", "general-purpose", "scan");

            CapturingStream stream = new CapturingStream();
            AgentExecutionCommand request = new AgentExecutionCommand();
            request.setRequestId("req-bg-defer");
            request.setSessionId(sessionId);
            AgentSessionPrinter printer = new AgentSessionPrinter(stream, request, 5);

            HashMap<String, Object> summary = new HashMap<>();
            summary.put("taskSummary", "主结论");
            printer.send("result", summary);

            Assert.assertEquals(1, stream.payloads.size());
            AgentStreamEvent response = (AgentStreamEvent) stream.payloads.get(0);
            Assert.assertEquals("result", response.getMessageType());
            // 对齐 ZCode：后台在飞也照常 finished，父流不等子（终态已落账本）
            Assert.assertTrue(Boolean.TRUE.equals(response.getFinish()));
            Assert.assertFalse(stream.completed.get());
        } finally {
            SessionBackgroundTaskHub.evict(sessionId);
        }
    }

    @Test
    public void rootResultDefersFinishWhileBackgroundRunningLegacy() {
        withDeferBackgroundSettle(true, () -> {
            String sessionId = "sess-bg-defer-legacy-" + System.nanoTime();
            SessionBackgroundTaskHub.evict(sessionId);
            try {
                SessionBackgroundTaskHub.getOrCreate(sessionId, null)
                        .registerLocalAgent("后台探查", "general-purpose", "scan");

                CapturingStream stream = new CapturingStream();
                AgentExecutionCommand request = new AgentExecutionCommand();
                request.setRequestId("req-bg-defer-legacy");
                request.setSessionId(sessionId);
                AgentSessionPrinter printer = new AgentSessionPrinter(stream, request, 5);

                HashMap<String, Object> summary = new HashMap<>();
                summary.put("taskSummary", "主结论");
                printer.send("result", summary);

                Assert.assertEquals(1, stream.payloads.size());
                AgentStreamEvent response = (AgentStreamEvent) stream.payloads.get(0);
                Assert.assertEquals("result", response.getMessageType());
                Assert.assertFalse(Boolean.TRUE.equals(response.getFinish()));
                Assert.assertFalse(stream.completed.get());
            } finally {
                SessionBackgroundTaskHub.evict(sessionId);
            }
        });
    }

    @Test
    public void streamSettleFinishesEvenWithNoBackground() {
        String sessionId = "sess-bg-settle-" + System.nanoTime();
        SessionBackgroundTaskHub.evict(sessionId);
        try {
            CapturingStream stream = new CapturingStream();
            AgentExecutionCommand request = new AgentExecutionCommand();
            request.setRequestId("req-bg-settle");
            request.setSessionId(sessionId);
            AgentSessionPrinter printer = new AgentSessionPrinter(stream, request, 5);

            HashMap<String, Object> settle = new HashMap<>();
            settle.put("reason", "background_idle");
            printer.send("stream_settle", settle);

            Assert.assertEquals(1, stream.payloads.size());
            AgentStreamEvent response = (AgentStreamEvent) stream.payloads.get(0);
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
                    .registerLocalAgent("t", "general-purpose", "p");
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
                    .registerLocalAgent("后台探查", "general-purpose", "scan");

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
                    .registerLocalAgent("后台探查", "general-purpose", "scan");
            SessionBackgroundTaskHub.getOrCreate(sessionId, null).complete(task.getId(), null);
            Assert.assertFalse("父 run 未 finish 时不得 settle",
                    AgentDispatchTool.shouldSettleParentStream(parent));
        } finally {
            SessionBackgroundTaskHub.evict(sessionId);
        }
    }

    @Test
    public void backgroundIdleNeverSettlesWithoutDeferFlag() {
        String sessionId = "sess-bg-parent-nodefer-" + System.nanoTime();
        SessionBackgroundTaskHub.evict(sessionId);
        try {
            AgentContext parent = AgentContext.builder()
                    .requestId("req-bg-parent-nodefer")
                    .sessionId(sessionId)
                    .build();
            RuntimeBackgroundTask task = SessionBackgroundTaskHub.getOrCreate(sessionId, null)
                    .registerLocalAgent("后台探查", "general-purpose", "scan");
            parent.markTurnClosed();
            // 对齐 ZCode（默认）：父回合关闭也不需要 settle 帧，终态只落账本
            Assert.assertFalse(AgentDispatchTool.shouldSettleParentStream(parent));
            SessionBackgroundTaskHub.getOrCreate(sessionId, null).complete(task.getId(), null);
            Assert.assertFalse(AgentDispatchTool.shouldSettleParentStream(parent));
        } finally {
            SessionBackgroundTaskHub.evict(sessionId);
        }
    }

    @Test
    public void backgroundIdleSettlesAfterParentTurnClosedLegacy() {
        withDeferBackgroundSettle(true, () -> {
            String sessionId = "sess-bg-parent-closed-legacy-" + System.nanoTime();
            SessionBackgroundTaskHub.evict(sessionId);
            try {
                AgentContext parent = AgentContext.builder()
                        .requestId("req-bg-parent-closed-legacy")
                        .sessionId(sessionId)
                        .build();
                RuntimeBackgroundTask task = SessionBackgroundTaskHub.getOrCreate(sessionId, null)
                        .registerLocalAgent("后台探查", "general-purpose", "scan");
                parent.markTurnClosed();
                Assert.assertFalse(AgentDispatchTool.shouldSettleParentStream(parent));

                SessionBackgroundTaskHub.getOrCreate(sessionId, null).complete(task.getId(), null);
                Assert.assertTrue(AgentDispatchTool.shouldSettleParentStream(parent));
            } finally {
                SessionBackgroundTaskHub.evict(sessionId);
            }
        });
    }

    @Test
    public void gptQueryCompletesProjectionWithBackgroundRunning() {
        String sessionId = "sess-bg-gpt-nodefer-" + System.nanoTime();
        SessionBackgroundTaskHub.evict(sessionId);
        try {
            RuntimeBackgroundTask task = SessionBackgroundTaskHub.getOrCreate(sessionId, null)
                    .registerLocalAgent("后台探查", "general-purpose", "scan");

            CapturingStream downstream = new CapturingStream();
            AgentExecutionCommand request = new AgentExecutionCommand();
            request.setRequestId("req-bg-gpt-nodefer");
            request.setSessionId(sessionId);
            AgentStreamProjection projecting =
                    new AgentStreamProjection(downstream, request, Map.of());

            GptQueryApplicationService.completeProjectionUnlessBackgroundRunning(request, projecting);

            // 对齐 ZCode（默认）：后台在飞也关闭投影流，父卡终态走 history 重拉
            Assert.assertTrue(downstream.completed.get());

            SessionBackgroundTaskHub.getOrCreate(sessionId, null).complete(task.getId(), null);
        } finally {
            SessionBackgroundTaskHub.evict(sessionId);
        }
    }

    @Test
    public void gptQueryDefersProjectionCompleteWhileBackgroundRunningLegacy() {
        withDeferBackgroundSettle(true, () -> {
            String sessionId = "sess-bg-gpt-defer-legacy-" + System.nanoTime();
            SessionBackgroundTaskHub.evict(sessionId);
            try {
                RuntimeBackgroundTask task = SessionBackgroundTaskHub.getOrCreate(sessionId, null)
                        .registerLocalAgent("后台探查", "general-purpose", "scan");

                CapturingStream downstream = new CapturingStream();
                AgentExecutionCommand request = new AgentExecutionCommand();
                request.setRequestId("req-bg-gpt-defer-legacy");
                request.setSessionId(sessionId);
                AgentStreamProjection projecting =
                        new AgentStreamProjection(downstream, request, Map.of());

                GptQueryApplicationService.completeProjectionUnlessBackgroundRunning(request, projecting);

                Assert.assertFalse("后台运行时不得关闭投影流", downstream.completed.get());

                SessionBackgroundTaskHub.getOrCreate(sessionId, null).complete(task.getId(), null);
                GptQueryApplicationService.completeProjectionUnlessBackgroundRunning(request, projecting);
                Assert.assertTrue("后台结束后应关闭投影流", downstream.completed.get());
            } finally {
                SessionBackgroundTaskHub.evict(sessionId);
            }
        });
    }

    @Test
    public void completeProjectionEndsActiveRunAfterStreamClose() {
        String sessionId = "sess-end-run-" + System.nanoTime();
        SessionBackgroundTaskHub.evict(sessionId);
        ActiveAgentRunRegistry registry = new ActiveAgentRunRegistry();
        try {
            registry.begin("req-end-run", sessionId, null);
            CapturingStream downstream = new CapturingStream();
            AgentExecutionCommand request = new AgentExecutionCommand();
            request.setRequestId("req-end-run");
            request.setSessionId(sessionId);
            AgentStreamProjection projecting =
                    new AgentStreamProjection(downstream, request, Map.of());

            GptQueryApplicationService.completeProjectionUnlessBackgroundRunning(
                    request, projecting, registry);

            Assert.assertTrue(downstream.completed.get());
            Assert.assertFalse("关流后才释放 ActiveRun", registry.find("req-end-run").isPresent());
        } finally {
            SessionBackgroundTaskHub.evict(sessionId);
        }
    }

    @Test
    public void completeProjectionEndsActiveRunWhileBackgroundRunning() {
        String sessionId = "sess-keep-run-" + System.nanoTime();
        SessionBackgroundTaskHub.evict(sessionId);
        ActiveAgentRunRegistry registry = new ActiveAgentRunRegistry();
        try {
            registry.begin("req-keep-run", sessionId, null);
            RuntimeBackgroundTask task = SessionBackgroundTaskHub.getOrCreate(sessionId, null)
                    .registerLocalAgent("后台探查", "general-purpose", "scan");
            CapturingStream downstream = new CapturingStream();
            AgentExecutionCommand request = new AgentExecutionCommand();
            request.setRequestId("req-keep-run");
            request.setSessionId(sessionId);
            AgentStreamProjection projecting =
                    new AgentStreamProjection(downstream, request, Map.of());

            GptQueryApplicationService.completeProjectionUnlessBackgroundRunning(
                    request, projecting, registry);

            // 对齐 ZCode（默认）：投影随 root result 关闭，ActiveRun 同步释放
            Assert.assertTrue(downstream.completed.get());
            Assert.assertFalse(registry.find("req-keep-run").isPresent());
            SessionBackgroundTaskHub.getOrCreate(sessionId, null).complete(task.getId(), null);
        } finally {
            SessionBackgroundTaskHub.evict(sessionId);
        }
    }

    @Test
    public void completeProjectionKeepsActiveRunWhileBackgroundRunningLegacy() {
        withDeferBackgroundSettle(true, () -> {
            String sessionId = "sess-keep-run-legacy-" + System.nanoTime();
            SessionBackgroundTaskHub.evict(sessionId);
            ActiveAgentRunRegistry registry = new ActiveAgentRunRegistry();
            try {
                registry.begin("req-keep-run-legacy", sessionId, null);
                RuntimeBackgroundTask task = SessionBackgroundTaskHub.getOrCreate(sessionId, null)
                        .registerLocalAgent("后台探查", "general-purpose", "scan");
                CapturingStream downstream = new CapturingStream();
                AgentExecutionCommand request = new AgentExecutionCommand();
                request.setRequestId("req-keep-run-legacy");
                request.setSessionId(sessionId);
                AgentStreamProjection projecting =
                        new AgentStreamProjection(downstream, request, Map.of());

                GptQueryApplicationService.completeProjectionUnlessBackgroundRunning(
                        request, projecting, registry);

                Assert.assertFalse(downstream.completed.get());
                Assert.assertFalse("父循环结束后释放占用，后台投影继续", registry.find("req-keep-run-legacy").isPresent());
                SessionBackgroundTaskHub.getOrCreate(sessionId, null).complete(task.getId(), null);
            } finally {
                SessionBackgroundTaskHub.evict(sessionId);
            }
        });
    }

    @Test
    public void projectionClosesAllowsNewRunWhileBackgroundRunning() {
        String sessionId = "sess-occupy-" + System.nanoTime();
        SessionBackgroundTaskHub.evict(sessionId);
        ActiveAgentRunRegistry registry = new ActiveAgentRunRegistry();
        try {
            registry.begin("req-parent", sessionId, "user-1");
            SessionBackgroundTaskHub.getOrCreate(sessionId, null)
                    .registerLocalAgent("后台探查", "general-purpose", "scan");
            AgentExecutionCommand request = new AgentExecutionCommand();
            request.setRequestId("req-parent");
            request.setSessionId(sessionId);
            AgentStreamProjection projecting =
                    new AgentStreamProjection(new CapturingStream(), request, Map.of());

            GptQueryApplicationService.completeProjectionUnlessBackgroundRunning(
                    request, projecting, registry);

            // 对齐 ZCode（默认）：投影随 root result 关闭并注销，占用释放后可起新 run
            Assert.assertTrue(projecting.isClosed());
            Assert.assertFalse(registry.find("req-parent").isPresent());
            registry.begin("req-next", sessionId, "user-1");
            Assert.assertTrue(registry.find("req-next").isPresent());
        } finally {
            SessionBackgroundTaskHub.evict(sessionId);
        }
    }

    @Test
    public void occupancyReleaseAllowsNewRunWhileBackgroundRunningLegacy() {
        withDeferBackgroundSettle(true, () -> {
            String sessionId = "sess-occupy-legacy-" + System.nanoTime();
            SessionBackgroundTaskHub.evict(sessionId);
            ActiveAgentRunRegistry registry = new ActiveAgentRunRegistry();
            try {
                registry.begin("req-parent-legacy", sessionId, "user-1");
                SessionBackgroundTaskHub.getOrCreate(sessionId, null)
                        .registerLocalAgent("后台探查", "general-purpose", "scan");
                AgentExecutionCommand request = new AgentExecutionCommand();
                request.setRequestId("req-parent-legacy");
                request.setSessionId(sessionId);
                AgentStreamProjection projecting =
                        new AgentStreamProjection(new CapturingStream(), request, Map.of());

                GptQueryApplicationService.completeProjectionUnlessBackgroundRunning(
                        request, projecting, registry);

                Assert.assertFalse(projecting.isClosed());
                Assert.assertFalse(registry.find("req-parent-legacy").isPresent());
                registry.begin("req-next-legacy", sessionId, "user-1");
                Assert.assertTrue(registry.find("req-next-legacy").isPresent());
            } finally {
                SessionBackgroundTaskHub.evict(sessionId);
            }
        });
    }

    @Test
    public void projectionUnregistersAfterRootResultWhileBackgroundRunning() {
        String sessionId = "sess-proj-" + System.nanoTime();
        SessionBackgroundTaskHub.evict(sessionId);
        SessionProjectionRegistry projections = new SessionProjectionRegistry();
        try {
            SessionBackgroundTaskHub.getOrCreate(sessionId, null)
                    .registerLocalAgent("后台探查", "general-purpose", "scan");
            AgentExecutionCommand request = new AgentExecutionCommand();
            request.setRequestId("req-proj");
            request.setSessionId(sessionId);
            AgentStreamProjection projecting =
                    new AgentStreamProjection(new CapturingStream(), request, Map.of())
                            .bindRegistry(projections);

            GptQueryApplicationService.completeProjectionUnlessBackgroundRunning(request, projecting);

            // 对齐 ZCode（默认）：root result 后投影即关闭注销；后续 follow 走 ledger replay 冷路径
            Assert.assertFalse(projections.hasLive(sessionId));
            Assert.assertTrue(projecting.isClosed());
        } finally {
            SessionBackgroundTaskHub.evict(sessionId);
        }
    }

    @Test
    public void liveProjectionSurvivesOccupancyReleaseLegacy() {
        withDeferBackgroundSettle(true, () -> {
            String sessionId = "sess-proj-legacy-" + System.nanoTime();
            SessionBackgroundTaskHub.evict(sessionId);
            SessionProjectionRegistry projections = new SessionProjectionRegistry();
            try {
                SessionBackgroundTaskHub.getOrCreate(sessionId, null)
                        .registerLocalAgent("后台探查", "general-purpose", "scan");
                AgentExecutionCommand request = new AgentExecutionCommand();
                request.setRequestId("req-proj-legacy");
                request.setSessionId(sessionId);
                AgentStreamProjection projecting =
                        new AgentStreamProjection(new CapturingStream(), request, Map.of())
                                .bindRegistry(projections);

                GptQueryApplicationService.completeProjectionUnlessBackgroundRunning(request, projecting);

                Assert.assertTrue(projections.hasLive(sessionId));
                Assert.assertFalse(projecting.isClosed());
                projecting.complete();
                Assert.assertFalse(projections.hasLive(sessionId));
            } finally {
                SessionBackgroundTaskHub.evict(sessionId);
            }
        });
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
