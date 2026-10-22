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
 * 后台子 Agent 结算与父 SSE 流收口（2026-10-16 定稿：业务态与传输层分离）。
 * <p>
 * 两层语义：
 * <ul>
 *   <li><b>业务态（无条件）</b>：root result 帧恒 {@code finish=true}，前端 loading 立即收口，
 *       与后台是否在飞无关；</li>
 *   <li><b>传输层（默认 deferBackgroundSettle=true）</b>：后台在飞则投影不 complete、父流保持
 *       打开直到 {@code stream_settle}，结算 tool_result 经活连接推送（ZCode delta 推送等价物）；
 *       {@code false} 时全解耦，root result 即关流，父卡走前端冷路径。</li>
 * </ul>
 * 默认断言覆盖传输层挂起，{@code *WithoutDeferFlag} 变体覆盖全解耦，灰度期可一键切换。
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
    public void rootResultAlwaysFinishesRegardlessOfBackground() {
        for (boolean defer : new boolean[] {true, false}) {
            withDeferBackgroundSettle(defer, () -> {
                String sessionId = "sess-bg-finish-" + System.nanoTime();
                SessionBackgroundTaskHub.evict(sessionId);
                try {
                    SessionBackgroundTaskHub.getOrCreate(sessionId, null)
                            .registerLocalAgent("后台探查", "general-purpose", "scan");

                    CapturingStream stream = new CapturingStream();
                    AgentExecutionCommand request = new AgentExecutionCommand();
                    request.setRequestId("req-bg-finish");
                    request.setSessionId(sessionId);
                    AgentSessionPrinter printer = new AgentSessionPrinter(stream, request, 5);

                    HashMap<String, Object> summary = new HashMap<>();
                    summary.put("taskSummary", "主结论");
                    printer.send("result", summary);

                    Assert.assertEquals(1, stream.payloads.size());
                    AgentStreamEvent response = (AgentStreamEvent) stream.payloads.get(0);
                    Assert.assertEquals("result", response.getMessageType());
                    // 业务态无条件解绑：后台在飞 root result 也恒 finish=true，
                    // 前端据此立即收口 loading（传输层是否挂起由 StreamSettlePolicy 单独管）。
                    Assert.assertTrue(Boolean.TRUE.equals(response.getFinish()));
                    Assert.assertFalse(stream.completed.get());
                } finally {
                    SessionBackgroundTaskHub.evict(sessionId);
                }
            });
        }
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
    public void backgroundIdleSettlesAfterParentTurnClosed() {
        String sessionId = "sess-bg-parent-closed-" + System.nanoTime();
        SessionBackgroundTaskHub.evict(sessionId);
        try {
            AgentContext parent = AgentContext.builder()
                    .requestId("req-bg-parent-closed")
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
    }

    @Test
    public void backgroundIdleNeverSettlesWithoutDeferFlag() {
        withDeferBackgroundSettle(false, () -> {
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
                // 全解耦：root result 后父流已关，不需要也没有 settle 帧
                Assert.assertFalse(AgentDispatchTool.shouldSettleParentStream(parent));
                SessionBackgroundTaskHub.getOrCreate(sessionId, null).complete(task.getId(), null);
                Assert.assertFalse(AgentDispatchTool.shouldSettleParentStream(parent));
            } finally {
                SessionBackgroundTaskHub.evict(sessionId);
            }
        });
    }

    @Test
    public void gptQueryDefersProjectionCompleteWhileBackgroundRunning() {
        String sessionId = "sess-bg-gpt-defer-" + System.nanoTime();
        SessionBackgroundTaskHub.evict(sessionId);
        try {
            RuntimeBackgroundTask task = SessionBackgroundTaskHub.getOrCreate(sessionId, null)
                    .registerLocalAgent("后台探查", "general-purpose", "scan");

            CapturingStream downstream = new CapturingStream();
            AgentExecutionCommand request = new AgentExecutionCommand();
            request.setRequestId("req-bg-gpt-defer");
            request.setSessionId(sessionId);
            AgentStreamProjection projecting =
                    new AgentStreamProjection(downstream, request, Map.of());

            GptQueryApplicationService.completeProjectionUnlessBackgroundRunning(request, projecting);

            // 传输层默认挂起：后台运行时投影保持打开，结算帧才有活连接可推
            Assert.assertFalse(downstream.completed.get());

            SessionBackgroundTaskHub.getOrCreate(sessionId, null).complete(task.getId(), null);
            GptQueryApplicationService.completeProjectionUnlessBackgroundRunning(request, projecting);
            Assert.assertTrue("后台结束后应关闭投影流", downstream.completed.get());
        } finally {
            SessionBackgroundTaskHub.evict(sessionId);
        }
    }

    @Test
    public void gptQueryCompletesProjectionWithoutDeferFlag() {
        withDeferBackgroundSettle(false, () -> {
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

                // 全解耦：后台在飞也直接关闭投影流，父卡走前端冷路径
                Assert.assertTrue(downstream.completed.get());

                SessionBackgroundTaskHub.getOrCreate(sessionId, null).complete(task.getId(), null);
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
    public void completeProjectionKeepsActiveRunWhileBackgroundRunning() {
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

            // 默认挂起：投影不关（等结算帧推送），但 ActiveRun 占用照常释放
            Assert.assertFalse(downstream.completed.get());
            Assert.assertFalse(registry.find("req-keep-run").isPresent());
            SessionBackgroundTaskHub.getOrCreate(sessionId, null).complete(task.getId(), null);
        } finally {
            SessionBackgroundTaskHub.evict(sessionId);
        }
    }

    @Test
    public void completeProjectionEndsActiveRunWhileBackgroundRunningWithoutDeferFlag() {
        withDeferBackgroundSettle(false, () -> {
            String sessionId = "sess-keep-run-nodefer-" + System.nanoTime();
            SessionBackgroundTaskHub.evict(sessionId);
            ActiveAgentRunRegistry registry = new ActiveAgentRunRegistry();
            try {
                registry.begin("req-keep-run-nodefer", sessionId, null);
                RuntimeBackgroundTask task = SessionBackgroundTaskHub.getOrCreate(sessionId, null)
                        .registerLocalAgent("后台探查", "general-purpose", "scan");
                CapturingStream downstream = new CapturingStream();
                AgentExecutionCommand request = new AgentExecutionCommand();
                request.setRequestId("req-keep-run-nodefer");
                request.setSessionId(sessionId);
                AgentStreamProjection projecting =
                        new AgentStreamProjection(downstream, request, Map.of());

                GptQueryApplicationService.completeProjectionUnlessBackgroundRunning(
                        request, projecting, registry);

                Assert.assertTrue(downstream.completed.get());
                Assert.assertFalse(registry.find("req-keep-run-nodefer").isPresent());
                SessionBackgroundTaskHub.getOrCreate(sessionId, null).complete(task.getId(), null);
            } finally {
                SessionBackgroundTaskHub.evict(sessionId);
            }
        });
    }

    @Test
    public void occupancyReleaseAllowsNewRunWhileBackgroundRunning() {
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

            // 默认挂起：投影保持存活，占用释放后可起新 run
            Assert.assertFalse(projecting.isClosed());
            Assert.assertFalse(registry.find("req-parent").isPresent());
            registry.begin("req-next", sessionId, "user-1");
            Assert.assertTrue(registry.find("req-next").isPresent());
        } finally {
            SessionBackgroundTaskHub.evict(sessionId);
        }
    }

    @Test
    public void projectionClosesAllowsNewRunWithoutDeferFlag() {
        withDeferBackgroundSettle(false, () -> {
            String sessionId = "sess-occupy-nodefer-" + System.nanoTime();
            SessionBackgroundTaskHub.evict(sessionId);
            ActiveAgentRunRegistry registry = new ActiveAgentRunRegistry();
            try {
                registry.begin("req-parent-nodefer", sessionId, "user-1");
                SessionBackgroundTaskHub.getOrCreate(sessionId, null)
                        .registerLocalAgent("后台探查", "general-purpose", "scan");
                AgentExecutionCommand request = new AgentExecutionCommand();
                request.setRequestId("req-parent-nodefer");
                request.setSessionId(sessionId);
                AgentStreamProjection projecting =
                        new AgentStreamProjection(new CapturingStream(), request, Map.of());

                GptQueryApplicationService.completeProjectionUnlessBackgroundRunning(
                        request, projecting, registry);

                Assert.assertTrue(projecting.isClosed());
                Assert.assertFalse(registry.find("req-parent-nodefer").isPresent());
                registry.begin("req-next-nodefer", sessionId, "user-1");
                Assert.assertTrue(registry.find("req-next-nodefer").isPresent());
            } finally {
                SessionBackgroundTaskHub.evict(sessionId);
            }
        });
    }

    @Test
    public void liveProjectionSurvivesOccupancyRelease() {
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

            // 默认挂起：投影活着等结算帧；complete 后正常注销
            Assert.assertTrue(projections.hasLive(sessionId));
            Assert.assertFalse(projecting.isClosed());
            projecting.complete();
            Assert.assertFalse(projections.hasLive(sessionId));
        } finally {
            SessionBackgroundTaskHub.evict(sessionId);
        }
    }

    @Test
    public void projectionUnregistersAfterRootResultWithoutDeferFlag() {
        withDeferBackgroundSettle(false, () -> {
            String sessionId = "sess-proj-nodefer-" + System.nanoTime();
            SessionBackgroundTaskHub.evict(sessionId);
            SessionProjectionRegistry projections = new SessionProjectionRegistry();
            try {
                SessionBackgroundTaskHub.getOrCreate(sessionId, null)
                        .registerLocalAgent("后台探查", "general-purpose", "scan");
                AgentExecutionCommand request = new AgentExecutionCommand();
                request.setRequestId("req-proj-nodefer");
                request.setSessionId(sessionId);
                AgentStreamProjection projecting =
                        new AgentStreamProjection(new CapturingStream(), request, Map.of())
                                .bindRegistry(projections);

                GptQueryApplicationService.completeProjectionUnlessBackgroundRunning(request, projecting);

                Assert.assertFalse(projections.hasLive(sessionId));
                Assert.assertTrue(projecting.isClosed());
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
