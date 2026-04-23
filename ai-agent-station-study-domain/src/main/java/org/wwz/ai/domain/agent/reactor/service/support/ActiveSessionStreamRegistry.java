package org.wwz.ai.domain.agent.reactor.service.support;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import okhttp3.Call;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 活跃会话流注册表。
 * 使用 Guava Cache 实现自动过期清理，防止异常中断导致内存泄漏。
 */
@Component
public class ActiveSessionStreamRegistry {

    /**
     * 默认过期时间：30 分钟。若请求超过此时间未完成，自动清理。
     */
    private static final long DEFAULT_EXPIRE_MINUTES = 30;

    private final Cache<String, ActiveSessionStream> activeStreams = CacheBuilder.newBuilder()
            .expireAfterWrite(DEFAULT_EXPIRE_MINUTES, TimeUnit.MINUTES)
            .build();

    public void register(String sessionId,
                         String requestId,
                         Long messageId,
                         Call call,
                         SseEmitter emitter) {
        activeStreams.put(requestId, ActiveSessionStream.builder()
                .sessionId(sessionId)
                .requestId(requestId)
                .messageId(messageId)
                .call(call)
                .emitter(emitter)
                .stopRequested(new AtomicBoolean(false))
                .build());
    }

    public void unregister(String requestId) {
        if (requestId == null) {
            return;
        }
        activeStreams.invalidate(requestId);
    }

    public boolean requestStop(String requestId) {
        ActiveSessionStream activeStream = activeStreams.getIfPresent(requestId);
        if (activeStream == null) {
            return false;
        }
        activeStream.getStopRequested().set(true);
        if (activeStream.getCall() != null) {
            activeStream.getCall().cancel();
        }
        return true;
    }

    public boolean isStopRequested(String requestId) {
        ActiveSessionStream activeStream = activeStreams.getIfPresent(requestId);
        return activeStream != null && activeStream.getStopRequested().get();
    }

    /**
     * 获取当前活跃流数量（主要用于监控和调试）。
     * 注意：cleanUp() 是异步清理的启发式触发，不保证立即完成，size() 返回的是近似值。
     */
    public long getActiveCount() {
        activeStreams.cleanUp();
        return activeStreams.size();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActiveSessionStream {
        private String sessionId;
        private String requestId;
        private Long messageId;
        private Call call;
        private SseEmitter emitter;
        private AtomicBoolean stopRequested;
    }
}
