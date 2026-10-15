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
 * 会话级 SSE 总线：订阅、回放闸门、心跳、按 userId 限流。
 * 超时/断开只取消订阅，不终止 run。
 */
@Slf4j
@Component
public class AgentSessionStreamHub implements AgentSessionEventBus {

    public static final int MAX_STREAMS_PER_OWNER = 3;
    /**
     * 子会话观察流的独立配额。
     * <p>
     * 子 Agent 详情面板是<b>只读观察通道</b>，由已经占着一条主对话流的页面派生出来。
     * 与主对话流共用同一份 3 条配额时，「开两个对话页 + 打开一个子 Agent 详情」就会打满，
     * 第 4 条请求（无论来自对话页还是详情面板）直接 429 —— 详情面板本可优雅降级，
     * 却把用户的对话流挤掉了。两者生命周期、代价、可降级性都不同，因此分桶计数。
     * <p>
     * 上限取 8：单个 run 的并发子代理闸门是 4，留一倍余量给「多个对话页各开一个详情面板」，
     * 同时仍然封住「一次派发 N 个子代理就开 N 条长连接」的无界增长。
     */
    public static final int MAX_OBSERVE_STREAMS_PER_OWNER = 8;

    /**
     * 观察流配额分桶。{@code bucketSuffix} 参与计数键，保证两个桶互不干扰。
     */
    public enum StreamKind {
        CONVERSATION("", MAX_STREAMS_PER_OWNER, "并发对话流", "请关闭其它对话页后重试"),
        SUB_AGENT("#observe", MAX_OBSERVE_STREAMS_PER_OWNER, "并发子会话观察流",
                "请关闭其它子 Agent 详情面板后重试");

        private final String bucketSuffix;
        private final int limit;
        private final String label;
        private final String advice;

        StreamKind(String bucketSuffix, int limit, String label, String advice) {
            this.bucketSuffix = bucketSuffix;
            this.limit = limit;
            this.label = label;
            this.advice = advice;
        }

        public int limit() {
            return limit;
        }

        public String limitMessage() {
            return label + "已达上限（" + limit + "），" + advice;
        }

        private String bucketKey(String ownerKey) {
            return ownerKey + bucketSuffix;
        }
    }

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

    public StreamReservation reserve(String ownerKey, StreamKind kind) {
        acquireSlot(ownerKey, kind);
        return new StreamReservation(ownerKey, kind);
    }

    public SseEmitter open(String sessionId, StreamReservation reservation, long lastEventSeq) {
        // transfer 之后槽位由 Conn 持有，这里的值已经是计数桶键（ownerKey + kind 后缀）。
        String bucketKey = reservation.transfer();
        Conn created = null;
        try {
            SseEmitter emitter = SseLifecycleSupport.createLongLivedEmitter();
            SseEmitterAgentSessionStream stream = new SseEmitterAgentSessionStream(emitter);
            final Conn conn = new Conn(sessionId, emitter, stream, bucketKey);
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
                releaseSlot(bucketKey);
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

    private void acquireSlot(String ownerKey, StreamKind kind) {
        if (ownerKey == null) {
            return;
        }
        String bucketKey = kind.bucketKey(ownerKey);
        AtomicBoolean acquired = new AtomicBoolean(false);
        ownerCounts.compute(bucketKey, (key, existing) -> {
            AtomicInteger counter = existing == null ? new AtomicInteger() : existing;
            if (counter.get() < kind.limit()) {
                counter.incrementAndGet();
                acquired.set(true);
            }
            return counter;
        });
        if (!acquired.get()) {
            throw new AgentStreamLimitException(kind.limitMessage());
        }
    }

    private void releaseSlot(String bucketKey) {
        if (bucketKey == null) {
            return;
        }
        ownerCounts.computeIfPresent(bucketKey, (key, counter) -> {
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
        /** 计数桶键（= ownerKey + kind 后缀），transfer 之后由 Conn 持有。 */
        private final String bucketKey;
        private final AtomicBoolean transferred = new AtomicBoolean(false);

        private StreamReservation(String ownerKey, StreamKind kind) {
            this.bucketKey = ownerKey == null ? null : kind.bucketKey(ownerKey);
        }

        private String transfer() {
            if (!transferred.compareAndSet(false, true)) {
                throw new IllegalStateException("stream reservation already used");
            }
            return bucketKey;
        }

        public void close() {
            if (transferred.compareAndSet(false, true)) {
                releaseSlot(bucketKey);
            }
        }
    }

    private final class Conn {
        private final String sessionId;
        private final SseEmitter emitter;
        private final SseEmitterAgentSessionStream stream;
        private final String bucketKey;
        private final Object lock = new Object();
        private final ArrayDeque<AgentSessionStreamFrame> queued = new ArrayDeque<>();
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final AtomicReference<ScheduledFuture<?>> parkedAttach = new AtomicReference<>();
        private boolean live;
        private long lastSeq;
        private ScheduledFuture<?> heartbeat;

        private Conn(String sessionId,
                     SseEmitter emitter,
                     SseEmitterAgentSessionStream stream,
                     String bucketKey) {
            this.sessionId = sessionId;
            this.emitter = emitter;
            this.stream = stream;
            this.bucketKey = bucketKey;
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
            releaseSlot(bucketKey);
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
