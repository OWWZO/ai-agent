package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.application.agent.run.AgentRunFollowApplicationService;
import org.wwz.ai.application.agent.run.FollowAttachResult;
import org.wwz.ai.application.agent.stream.AgentStreamProjection;
import org.wwz.ai.application.agent.stream.SessionProjectionRegistry;
import org.wwz.ai.domain.agent.runtime.command.AgentExecutionCommand;
import org.wwz.ai.trigger.http.cute.support.AgentSessionStreamHub;
import org.wwz.ai.trigger.http.cute.support.SseEmitterAgentSessionStream;
import org.wwz.ai.types.agent.config.AgentExecutorProperties;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 会话观察流的连接生命周期回归测试。
 * <p>
 * 注意：这里断言的是 <b>connectionCount</b>（连接是否被回收），不是并发配额——
 * 按 userId 的连接数上限已按需求移除（见 {@link AgentSessionStreamHub} 类注释），
 * 但「连接该关的时候必须关」仍然是硬要求，否则 emitter 与心跳任务会一直泄漏。
 */
public class AgentSessionStreamHubTest {

    /** 与旧配额无关，只是「多开几条」的样本量。 */
    private static final int SAMPLE_CONNECTIONS = 3;

    @Test
    public void shouldCloseStreamWhenLastSessionProjectionCompletes() {
        AgentRunFollowApplicationService followService = mock(AgentRunFollowApplicationService.class);
        when(followService.authorizeAndAttach(anyString(), anyLong(), any()))
                .thenReturn(FollowAttachResult.ATTACHED);

        TaskScheduler scheduler = mock(TaskScheduler.class);
        when(scheduler.scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class)))
                .thenReturn(mock(ScheduledFuture.class));

        AgentSessionStreamHub hub = new AgentSessionStreamHub();
        ReflectionTestUtils.setField(hub, "agentRunFollowApplicationService", followService);
        ReflectionTestUtils.setField(hub, "agentExecutorProperties", new AgentExecutorProperties());
        ReflectionTestUtils.setField(hub, "heartbeatScheduler", scheduler);

        String sessionId = "session-serial";
        SessionProjectionRegistry projectionRegistry = new SessionProjectionRegistry();
        for (int i = 0; i < SAMPLE_CONNECTIONS; i++) {
            hub.open(sessionId, 0L);

            AgentExecutionCommand request = new AgentExecutionCommand();
            request.setRequestId("request-" + i);
            request.setSessionId(sessionId);
            AgentStreamProjection projection =
                    new AgentStreamProjection(null, request, Map.of(), hub)
                            .bindRegistry(projectionRegistry);
            if (i == 0) {
                AgentExecutionCommand overlappingRequest = new AgentExecutionCommand();
                overlappingRequest.setRequestId("request-overlapping");
                overlappingRequest.setSessionId(sessionId);
                AgentStreamProjection overlappingProjection =
                        new AgentStreamProjection(null, overlappingRequest, Map.of(), hub)
                                .bindRegistry(projectionRegistry);
                projection.complete();
                Assert.assertEquals("仍有活投影时不能提前关闭观察流", 1, hub.connectionCount());
                overlappingProjection.complete();
            } else {
                projection.complete();
            }
        }

        Assert.assertEquals("最后一个 run 完成后应关闭观察流", 0, hub.connectionCount());
    }

    @Test
    public void shouldDropConnectionWhenAttachedStreamIsAborted() {
        AgentRunFollowApplicationService followService = mock(AgentRunFollowApplicationService.class);
        when(followService.authorizeAndAttach(anyString(), anyLong(), any()))
                .thenReturn(FollowAttachResult.ATTACHED);

        TaskScheduler scheduler = mock(TaskScheduler.class);
        when(scheduler.scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class)))
                .thenReturn(mock(ScheduledFuture.class));

        AgentSessionStreamHub hub = new AgentSessionStreamHub();
        ReflectionTestUtils.setField(hub, "agentRunFollowApplicationService", followService);
        ReflectionTestUtils.setField(hub, "agentExecutorProperties", new AgentExecutorProperties());
        ReflectionTestUtils.setField(hub, "heartbeatScheduler", scheduler);

        for (int i = 0; i < SAMPLE_CONNECTIONS; i++) {
            hub.open("session-" + i, 0L);
            abortLatestStream(hub);
        }

        Assert.assertEquals("已断开的观察流不应继续占用连接", 0, hub.connectionCount());
    }

    /**
     * 不再有配额，所以「同一会话反复订阅」必须每次都真的建出一条新连接，
     * 不能因为曾经有上限就出现静默失败。
     */
    @Test
    public void shouldOpenEveryConnectionWithoutQuota() {
        AgentRunFollowApplicationService followService = mock(AgentRunFollowApplicationService.class);
        when(followService.authorizeAndAttach(anyString(), anyLong(), any()))
                .thenReturn(FollowAttachResult.ATTACHED);

        TaskScheduler scheduler = mock(TaskScheduler.class);
        when(scheduler.scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class)))
                .thenReturn(mock(ScheduledFuture.class));

        AgentSessionStreamHub hub = new AgentSessionStreamHub();
        ReflectionTestUtils.setField(hub, "agentRunFollowApplicationService", followService);
        ReflectionTestUtils.setField(hub, "agentExecutorProperties", new AgentExecutorProperties());
        ReflectionTestUtils.setField(hub, "heartbeatScheduler", scheduler);

        int beyondOldLimit = 12;
        for (int i = 0; i < beyondOldLimit; i++) {
            hub.open("session-" + i, 0L);
        }

        Assert.assertEquals("连接数不应再受上限约束", beyondOldLimit, hub.connectionCount());
    }

    @SuppressWarnings("unchecked")
    private static void abortLatestStream(AgentSessionStreamHub hub) {
        Map<SseEmitter, Object> byEmitter =
                (Map<SseEmitter, Object>) ReflectionTestUtils.getField(hub, "byEmitter");
        Object conn = byEmitter.values().iterator().next();
        SseEmitterAgentSessionStream stream =
                (SseEmitterAgentSessionStream) ReflectionTestUtils.getField(conn, "stream");
        ReflectionTestUtils.invokeMethod(stream, "markAborted");
    }
}
