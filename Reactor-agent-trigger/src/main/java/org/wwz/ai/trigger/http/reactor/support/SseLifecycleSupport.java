package org.wwz.ai.trigger.http.reactor.support;

import org.slf4j.Logger;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.application.agent.stream.AgentSessionStream;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * trigger 侧统一管理 SSE 生命周期，避免心跳、超时和异常处理散落在 domain。
 */
public final class SseLifecycleSupport {

    /**
     * 主聊天 / follow / 问数等长连接 SSE 超时。
     * 客户端主动断开仍走 abort；此值只避免服务端因“空闲超时”提前掐流。
     */
    public static final long LONG_LIVED_SSE_TIMEOUT_MS = TimeUnit.DAYS.toMillis(7);

    private SseLifecycleSupport() {
    }

    public static SseEmitter createEmitter(long timeoutMillis) {
        return new SseEmitterUtf8(timeoutMillis);
    }

    public static SseEmitter createLongLivedEmitter() {
        return createEmitter(LONG_LIVED_SSE_TIMEOUT_MS);
    }

    /**
     * 向浏览器主聊天路径发送结构化心跳（如 GptProcessResult），避免前端 JSON.parse 失败。
     * 心跳必须走 {@code sender}，与业务帧串行化，禁止直接 {@code emitter.send}。
     */
    public static ScheduledFuture<?> startHeartbeat(TaskScheduler scheduler,
                                                    SseEmitter emitter,
                                                    AgentSessionStream sender,
                                                    String requestId,
                                                    long heartbeatIntervalMillis,
                                                    Logger log,
                                                    Object heartbeatPayload) {
        // 心跳只是连接保活，不参与 Agent 业务；客户端断开或发送失败时必须关闭 emitter 让调度资源回收。
        return scheduler.scheduleAtFixedRate(() -> {
            try {
                if (sender != null && sender.isAborted()) {
                    log.info("{} heartbeat stopped because SSE stream is aborted", requestId);
                    try {
                        emitter.complete();
                    } catch (Exception ignored) {
                        // already completed
                    }
                    return;
                }
                log.info("{} send heartbeat", requestId);
                if (sender != null) {
                    sender.send(heartbeatPayload);
                } else {
                    emitter.send(heartbeatPayload);
                }
                if (sender != null && sender.isAborted()) {
                    try {
                        emitter.complete();
                    } catch (Exception ignored) {
                        // already completed
                    }
                }
            } catch (Exception e) {
                if (SseClientDisconnectDetector.isClientDisconnected(e)
                        || isEmitterAlreadyCompleted(e)) {
                    log.info("{} heartbeat stopped because SSE emitter is closed", requestId);
                    try {
                        emitter.complete();
                    } catch (Exception ignored) {
                        // already completed
                    }
                    return;
                }
                log.warn("{} heartbeat send failed, will retry next interval", requestId, e);
            }
        }, Instant.now().plusMillis(heartbeatIntervalMillis), Duration.ofMillis(heartbeatIntervalMillis));
    }

    public static void registerLifecycle(SseEmitter emitter,
                                         String requestId,
                                         ScheduledFuture<?> heartbeatFuture,
                                         Logger log) {
        // 三类生命周期回调共享同一个 heartbeatFuture，保证正常结束、超时、异常都不会留下后台定时任务。
        emitter.onCompletion(() -> {
            log.info("{} SSE connection completed normally", requestId);
            cancelHeartbeat(heartbeatFuture);
        });

        emitter.onTimeout(() -> {
            log.info("{} SSE connection timed out", requestId);
            cancelHeartbeat(heartbeatFuture);
            emitter.complete();
        });

        emitter.onError((ex) -> {
            if (SseClientDisconnectDetector.isClientDisconnected(ex)) {
                log.info("{} SSE client disconnected", requestId);
            } else {
                log.warn("{} SSE connection error", requestId, ex);
            }
            cancelHeartbeat(heartbeatFuture);
        });
    }

    private static void cancelHeartbeat(ScheduledFuture<?> heartbeatFuture) {
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(true);
        }
    }

    private static boolean isEmitterAlreadyCompleted(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof IllegalStateException
                    && current.getMessage() != null
                    && current.getMessage().contains("already completed")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
