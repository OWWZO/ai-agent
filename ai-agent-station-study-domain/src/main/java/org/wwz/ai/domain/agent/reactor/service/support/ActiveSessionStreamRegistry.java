package org.wwz.ai.domain.agent.reactor.service.support;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import okhttp3.Call;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 活跃会话流注册表
 */
@Component
public class ActiveSessionStreamRegistry {

    private final ConcurrentMap<String, ActiveSessionStream> activeStreams = new ConcurrentHashMap<>();

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
        activeStreams.remove(requestId);
    }

    public boolean requestStop(String requestId) {
        ActiveSessionStream activeStream = activeStreams.get(requestId);
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
        ActiveSessionStream activeStream = activeStreams.get(requestId);
        return activeStream != null && activeStream.getStopRequested().get();
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
