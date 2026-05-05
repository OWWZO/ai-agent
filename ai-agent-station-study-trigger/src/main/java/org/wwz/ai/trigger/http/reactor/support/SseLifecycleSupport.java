package org.wwz.ai.trigger.http.reactor.support;

import org.slf4j.Logger;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * trigger 侧统一管理 SSE 生命周期，避免心跳、超时和异常处理散落在 domain。
 */
public final class SseLifecycleSupport {

    private SseLifecycleSupport() {
    }

    public static SseEmitter createEmitter(long timeoutMillis) {
        return new SseEmitterUtf8(timeoutMillis);
    }

    public static ScheduledFuture<?> startHeartbeat(ScheduledExecutorService executor,
                                                    SseEmitter emitter,
                                                    String requestId,
                                                    long heartbeatIntervalMillis,
                                                    Logger log) {
        return executor.scheduleAtFixedRate(() -> {
            try {
                log.info("{} send heartbeat", requestId);
                emitter.send("heartbeat");
            } catch (Exception e) {
                if (SseClientDisconnectDetector.isClientDisconnected(e)) {
                    log.info("{} heartbeat stopped because SSE client disconnected", requestId);
                    emitter.complete();
                    return;
                }
                log.warn("{} heartbeat failed, closing connection", requestId, e);
                emitter.completeWithError(e);
            }
        }, heartbeatIntervalMillis, heartbeatIntervalMillis, TimeUnit.MILLISECONDS);
    }

    public static void registerLifecycle(SseEmitter emitter,
                                         String requestId,
                                         ScheduledFuture<?> heartbeatFuture,
                                         Logger log) {
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
}
