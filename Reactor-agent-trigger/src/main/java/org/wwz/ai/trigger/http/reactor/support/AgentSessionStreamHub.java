package org.wwz.ai.trigger.http.reactor.support;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.application.agent.run.AgentRunFollowApplicationService;
import org.wwz.ai.application.agent.run.FollowAttachResult;
import org.wwz.ai.application.agent.stream.AgentResponseProjectionStream;
import org.wwz.ai.application.agent.stream.AgentSessionEventBus;
import org.wwz.ai.domain.agent.reactor.model.response.GptProcessResult;
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
 * 会话级 SSE 总线：订阅、回放闸门、心跳、按 visitor 限流。
 * 超时/断开只取消订阅，不终止 run。
 */
@Slf4j
@Component
public class AgentSessionStreamHub implements AgentSessionEventBus {

    public static final int MAX_STREAMS_PER_OWNER = 3;
    private static final long FOLLOW_PARK_INTERVAL_MS = 200L;
    private static final int FOLLOW_PARK_MAX_TICKS = 50;

    private final Map<String, CopyOnWriteArrayList<Conn>> bySession = new ConcurrentHashMap<>();
    private final Map<SseEmitter, Conn> byEmitter = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> ownerCounts = new ConcurrentHashMap<>();

    @Resource
    private AgentRunFollowApplicationService agentRunFollowApplicationService;

    @Resource
    private AgentExecutorProperties agentExecutorProperties;

    @Resource
    @Qualifier(AgentExecutorNames.HEARTBEAT_SCHEDULER)
    private TaskScheduler heartbeatScheduler;

    @Override
    public void publish(String sessionId, Object frame) {
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

    public StreamReservation reserve(String ownerKey) {
        acquireSlot(ownerKey);
        return new StreamReservation(ownerKey);
    }

    public SseEmitter open(String sessionId, StreamReservation reservation, long lastEventSeq) {
        String ownerKey = reservation.transfer();
        Conn created = null;
        try {
            SseEmitter emitter = SseLifecycleSupport.createLongLivedEmitter();
            SseEmitterAgentSessionStream stream = new SseEmitterAgentSessionStream(emitter);
            final Conn conn = new Conn(sessionId, emitter, stream, ownerKey);
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
                    AgentResponseProjectionStream.buildHeartbeat(sessionId)
            );
            SseLifecycleSupport.registerLifecycle(emitter, sessionId, heartbeatFuture, log);
            conn.attachHeartbeat(heartbeatFuture);
            // 客户端断开可能只触发协议适配器的 abort 回调，不一定触发 emitter completion。
            // 无论连接当前处于 pending 还是 attached，都必须回收 owner 并发槽位。
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
            if (created != null) {
                created.close(true);
            } else {
                releaseSlot(ownerKey);
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

    private void acquireSlot(String ownerKey) {
        if (ownerKey == null) {
            return;
        }
        AtomicBoolean acquired = new AtomicBoolean(false);
        ownerCounts.compute(ownerKey, (key, existing) -> {
            AtomicInteger counter = existing == null ? new AtomicInteger() : existing;
            if (counter.get() < MAX_STREAMS_PER_OWNER) {
                counter.incrementAndGet();
                acquired.set(true);
            }
            return counter;
        });
        if (!acquired.get()) {
            throw new AgentStreamLimitException(
                    "并发对话流已达上限（" + MAX_STREAMS_PER_OWNER + "），请关闭其它对话页后重试");
        }
    }

    private void releaseSlot(String ownerKey) {
        if (ownerKey == null) {
            return;
        }
        ownerCounts.computeIfPresent(ownerKey, (key, counter) -> {
            if (counter.decrementAndGet() <= 0) {
                return null;
            }
            return counter;
        });
    }

    private static void cancelPark(AtomicReference<ScheduledFuture<?>> futureRef) {
        ScheduledFuture<?> future = futureRef.getAndSet(null);
        if (future != null) {
            future.cancel(false);
        }
    }

    public final class StreamReservation {
        private final String ownerKey;
        private final AtomicBoolean transferred = new AtomicBoolean(false);

        private StreamReservation(String ownerKey) {
            this.ownerKey = ownerKey;
        }

        private String transfer() {
            if (!transferred.compareAndSet(false, true)) {
                throw new IllegalStateException("stream reservation already used");
            }
            return ownerKey;
        }

        public void close() {
            if (transferred.compareAndSet(false, true)) {
                releaseSlot(ownerKey);
            }
        }
    }

    private final class Conn {
        private final String sessionId;
        private final SseEmitter emitter;
        private final SseEmitterAgentSessionStream stream;
        private final String ownerKey;
        private final Object lock = new Object();
        private final ArrayDeque<Object> queued = new ArrayDeque<>();
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final AtomicReference<ScheduledFuture<?>> parkedAttach = new AtomicReference<>();
        private boolean live;
        private long lastSeq;
        private ScheduledFuture<?> heartbeat;

        private Conn(String sessionId,
                     SseEmitter emitter,
                     SseEmitterAgentSessionStream stream,
                     String ownerKey) {
            this.sessionId = sessionId;
            this.emitter = emitter;
            this.stream = stream;
            this.ownerKey = ownerKey;
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

        private void offer(Object frame) {
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

        private void sendDirect(Object frame) throws Exception {
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
                    Object frame = queued.removeFirst();
                    if (frameSeq(frame) <= lastSeq) {
                        continue;
                    }
                    sendLocked(frame);
                }
            }
        }

        private void sendLocked(Object frame) {
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
            releaseSlot(ownerKey);
            if (completeEmitter) {
                try {
                    stream.complete();
                } catch (Exception ignored) {
                    // already completed
                }
            }
        }
    }

    private static long frameSeq(Object frame) {
        if (frame instanceof GptProcessResult result) {
            return result.getEventSeq();
        }
        return 0L;
    }
}
