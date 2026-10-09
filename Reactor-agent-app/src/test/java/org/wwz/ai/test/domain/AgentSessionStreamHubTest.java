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
import org.wwz.ai.trigger.http.reactor.support.AgentSessionStreamHub;
import org.wwz.ai.trigger.http.reactor.support.SseEmitterAgentSessionStream;
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
 * 会话观察流并发槽位回收回归测试。
 */
public class AgentSessionStreamHubTest {

    @Test
    public void shouldReleaseSlotWhenLastSessionProjectionCompletes() {
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

        String ownerKey = "user-serial";
        String sessionId = "session-serial";
        SessionProjectionRegistry projectionRegistry = new SessionProjectionRegistry();
        for (int i = 0; i < AgentSessionStreamHub.MAX_STREAMS_PER_OWNER; i++) {
            hub.open(sessionId, hub.reserve(ownerKey), 0L);

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

        hub.reserve(ownerKey).close();
        Assert.assertEquals("最后一个 run 完成后应关闭观察流", 0, hub.connectionCount());
    }

    @Test
    public void shouldReleaseSlotWhenAttachedStreamIsAborted() {
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

        String ownerKey = "user-1";
        for (int i = 0; i < AgentSessionStreamHub.MAX_STREAMS_PER_OWNER; i++) {
            hub.open("session-" + i, hub.reserve(ownerKey), 0L);
            abortLatestStream(hub);
        }

        hub.reserve(ownerKey).close();
        Assert.assertEquals("已断开的观察流不应继续占用连接槽位", 0, hub.connectionCount());
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
