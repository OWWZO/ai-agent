package org.wwz.ai.trigger.http.reactor.support;

import org.slf4j.Logger;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

/**
 * trigger 侧统一管理 SSE 生命周期，避免心跳、超时和异常处理散落在 domain。
 */
public final class SseLifecycleSupport {

    private SseLifecycleSupport() {
    }

    public static SseEmitter createEmitter(long timeoutMillis) {
        return new SseEmitterUtf8(timeoutMillis);
    }

    public static ScheduledFuture<?> startHeartbeat(TaskScheduler scheduler,
                                                    SseEmitter emitter,
                                                    String requestId,
                                                    long heartbeatIntervalMillis,
                                                    Logger log) {
        return startHeartbeat(scheduler, emitter, requestId, heartbeatIntervalMillis, log, "heartbeat");
    }

    /**
     * 向浏览器主聊天路径发送结构化心跳（如 GptProcessResult），避免前端 JSON.parse 失败。
     */
    public static ScheduledFuture<?> startHeartbeat(TaskScheduler scheduler,
                                                    SseEmitter emitter,
                                                    String requestId,
                                                    long heartbeatIntervalMillis,
                                                    Logger log,
                                                    Object heartbeatPayload) {
        // 心跳只是连接保活，不参与 Agent 业务；客户端断开或发送失败时必须关闭 emitter 让调度资源回收。
        return scheduler.scheduleAtFixedRate(() -> {
            try {
                log.info("{} send heartbeat", requestId);
                emitter.send(heartbeatPayload);
            } catch (Exception e) {
                if (SseClientDisconnectDetector.isClientDisconnected(e)) {
                    log.info("{} heartbeat stopped because SSE client disconnected", requestId);
                    emitter.complete();
                    return;
                }
                log.warn("{} heartbeat failed, closing connection", requestId, e);
                emitter.completeWithError(e);
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
}
