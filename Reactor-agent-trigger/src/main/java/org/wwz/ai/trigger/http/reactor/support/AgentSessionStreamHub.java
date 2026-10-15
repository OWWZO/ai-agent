package org.wwz.ai.trigger.http.reactor.support;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.application.agent.run.AgentRunFollowApplicationService;
import org.wwz.ai.application.agent.run.FollowAttachResult;
import org.wwz.ai.application.agent.stream.AgentStreamProjection;
import org.wwz.ai.application.agent.stream.AgentSessionStreamFrame;
import org.wwz.ai.application.agent.stream.AgentSessionEventBus;
import org.wwz.ai.types.agent.config.AgentExecutorNames;
import org.wwz.ai.types.agent.config.AgentExecutorProperties;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 会话级 SSE 总线：订阅、回放闸门、心跳。
 * 超时/断开只取消订阅，不终止 run。
 * <p>
 * <b>不做按 userId 的并发连接数限制</b>：连接数由客户端行为决定（开了几个对话页、
 * 打开几个子 Agent 详情面板），限流只会把正常操作变成报错，而它并不能真正保护什么——
 * 每条连接的成本是一个 emitter + 一条心跳任务，真正的资源闸门在别处：
 * run 侧的并发闸门、`FOLLOW_PARK_MAX_TICKS`（挂起 attach 最多等 10s）、
 * 以及 emitter 自身的长连接超时。
 */
@Slf4j
@Component
public class AgentSessionStreamHub implements AgentSessionEventBus {

    private static final long FOLLOW_PARK_INTERVAL_MS = 200L;
    private static final int FOLLOW_PARK_MAX_TICKS = 50;

    private final Map<String, CopyOnWriteArrayList<Conn>> bySession = new ConcurrentHashMap<>();
    private final Map<SseEmitter, Conn> byEmitter = new ConcurrentHashMap<>();

    @Resource
    private AgentRunFollowApplicationService agentRunFollowApplicationService;

    @Resource
    private AgentExecutorProperties agentExecutorProperties;

    @Resource
    @Qualifier(AgentExecutorNames.HEARTBEAT_SCHEDULER)
    private TaskScheduler heartbeatScheduler;

    @Override
    public void publish(String sessionId, AgentSessionStreamFrame frame) {
        if (StringUtils.isBlank(sessionId) || frame == null) {
            return;
        }
        CopyOnWriteArrayList<Conn> conns = bySession.get(sessionId);
        if (conns == null || conns.isEmpty()) {
            return;
        }
        for (Conn conn : conns) {
            conn.offer(frame);
        }
    }

    @Override
    public void completeSession(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return;
        }
        CopyOnWriteArrayList<Conn> conns = bySession.get(sessionId);
        if (conns == null) {
            return;
        }
        for (Conn conn : conns) {
            conn.close(true);
        }
    }

    public SseEmitter open(String sessionId, long lastEventSeq) {
        Conn created = null;
        try {
            SseEmitter emitter = SseLifecycleSupport.createLongLivedEmitter();
            SseEmitterAgentSessionStream stream = new SseEmitterAgentSessionStream(emitter);
            final Conn conn = new Conn(sessionId, emitter, stream);
            created = conn;
            byEmitter.put(emitter, conn);
            bySession.computeIfAbsent(sessionId, key -> new CopyOnWriteArrayList<>()).add(conn);

            ScheduledFuture<?> heartbeatFuture = SseLifecycleSupport.startHeartbeat(
                    heartbeatScheduler,
                    emitter,
                    stream,
                    sessionId,
                    agentExecutorProperties.getHeartbeat().getIntervalMillis(),
                    log,
                    AgentStreamProjection.buildHeartbeat(sessionId)
            );
            SseLifecycleSupport.registerLifecycle(emitter, sessionId, heartbeatFuture, log);
            conn.attachHeartbeat(heartbeatFuture);
            // 客户端断开可能只触发协议适配器的 abort 回调，不一定触发 emitter completion。
            conn.stream.onAbort(() -> conn.close(false));

            emitter.onCompletion(() -> conn.close(false));
            emitter.onTimeout(() -> conn.close(true));
            emitter.onError(throwable -> conn.close(false));

            FollowAttachResult result = agentRunFollowApplicationService.authorizeAndAttach(
                    sessionId, lastEventSeq, conn::sendDirect);
            if (result == FollowAttachResult.IDLE) {
                agentRunFollowApplicationService.completeIdle(stream, sessionId);
                conn.close(false);
                return emitter;
            }
            if (result == FollowAttachResult.PENDING) {
                parkAttach(sessionId, lastEventSeq, conn);
            } else {
                conn.release();
            }
            return emitter;
        } catch (RuntimeException e) {
            // 建流中途失败：把已经登记进 byEmitter / bySession 的半成品连接收干净，
            // 否则它会一直占着心跳任务且永远收不到帧。
            if (created != null) {
                created.close(true);
            }
            throw e;
        }
    }

    public int connectionCount() {
        return byEmitter.size();
    }

    private void parkAttach(String sessionId, long lastEventSeq, Conn conn) {
        AtomicInteger ticks = new AtomicInteger();
        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();
        ScheduledFuture<?> future = heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (conn.stream.isAborted()) {
                cancelPark(futureRef);
                return;
            }
            FollowAttachResult result = agentRunFollowApplicationService.attachSession(
                    sessionId, lastEventSeq, conn::sendDirect);
            if (result == FollowAttachResult.ATTACHED) {
                conn.release();
                cancelPark(futureRef);
                return;
            }
            if (result == FollowAttachResult.IDLE || ticks.incrementAndGet() >= FOLLOW_PARK_MAX_TICKS) {
                if (result == FollowAttachResult.IDLE) {
                    agentRunFollowApplicationService.completeIdle(conn.stream, sessionId);
                } else {
                    agentRunFollowApplicationService.completePending(conn.stream, sessionId);
                }
                conn.close(false);
                cancelPark(futureRef);
            }
        }, Instant.now().plusMillis(FOLLOW_PARK_INTERVAL_MS), Duration.ofMillis(FOLLOW_PARK_INTERVAL_MS));
        futureRef.set(future);
        conn.attachPark(future);
        conn.stream.onAbort(() -> cancelPark(futureRef));
    }

    private static void cancelPark(AtomicReference<ScheduledFuture<?>> futureRef) {
        ScheduledFuture<?> future = futureRef.getAndSet(null);
        if (future != null) {
            future.cancel(false);
        }
    }

    private final class Conn {
        private final String sessionId;
        private final SseEmitter emitter;
        private final SseEmitterAgentSessionStream stream;
        private final Object lock = new Object();
        private final ArrayDeque<AgentSessionStreamFrame> queued = new ArrayDeque<>();
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final AtomicReference<ScheduledFuture<?>> parkedAttach = new AtomicReference<>();
        private boolean live;
        private long lastSeq;
        private ScheduledFuture<?> heartbeat;

        private Conn(String sessionId,
                     SseEmitter emitter,
                     SseEmitterAgentSessionStream stream) {
            this.sessionId = sessionId;
            this.emitter = emitter;
            this.stream = stream;
        }

        private void attachHeartbeat(ScheduledFuture<?> heartbeat) {
            this.heartbeat = heartbeat;
        }

        private void attachPark(ScheduledFuture<?> park) {
            if (closed.get()) {
                park.cancel(false);
                return;
            }
            parkedAttach.set(park);
            if (closed.get()) {
                cancelPark(parkedAttach);
            }
        }

        private void offer(AgentSessionStreamFrame frame) {
            synchronized (lock) {
                if (closed.get()) {
                    return;
                }
                if (!live) {
                    queued.addLast(frame);
                    return;
                }
                sendLocked(frame);
            }
        }

        private void sendDirect(AgentSessionStreamFrame frame) throws Exception {
            synchronized (lock) {
                if (closed.get()) {
                    return;
                }
                sendLocked(frame);
            }
        }

        private void release() {
            synchronized (lock) {
                live = true;
                while (!queued.isEmpty()) {
                    AgentSessionStreamFrame frame = queued.removeFirst();
                    if (frameSeq(frame) <= lastSeq) {
                        continue;
                    }
                    sendLocked(frame);
                }
            }
        }

        private void sendLocked(AgentSessionStreamFrame frame) {
            if (stream.isAborted()) {
                close(false);
                return;
            }
            try {
                stream.send(frame);
                long seq = frameSeq(frame);
                if (seq > lastSeq) {
                    lastSeq = seq;
                }
            } catch (Exception e) {
                log.debug("session sse send failed sessionId={}", sessionId, e);
                close(false);
            }
        }

        private void close(boolean completeEmitter) {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            CopyOnWriteArrayList<Conn> conns = bySession.get(sessionId);
            if (conns != null) {
                conns.remove(this);
                if (conns.isEmpty()) {
                    bySession.remove(sessionId, conns);
                }
            }
            byEmitter.remove(emitter, this);
            cancelPark(parkedAttach);
            if (heartbeat != null) {
                heartbeat.cancel(false);
            }
            if (completeEmitter) {
                try {
                    stream.complete();
                } catch (Exception ignored) {
                    // already completed
                }
            }
        }
    }

    private static long frameSeq(AgentSessionStreamFrame frame) {
        if (frame instanceof AgentSessionStreamFrame result) {
            return result.getEventSeq();
        }
        return 0L;
    }
}
