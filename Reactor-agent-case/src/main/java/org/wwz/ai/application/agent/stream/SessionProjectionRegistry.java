package org.wwz.ai.application.agent.stream;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 会话内活投影索引。占用（ActiveRun）释放后，后台子 Agent 仍可通过投影发帧；
 * GET observe 按 lastEventSeq 回放全部活投影，不依赖父 run 占用槽。
 */
@Component
public class SessionProjectionRegistry {

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<AgentStreamProjection>> bySession =
            new ConcurrentHashMap<>();

    public void register(String sessionId, AgentStreamProjection stream) {
        if (StringUtils.isBlank(sessionId) || stream == null) {
            return;
        }
        String key = sessionId.trim();
        bySession.compute(key, (ignored, streams) -> {
            CopyOnWriteArrayList<AgentStreamProjection> current =
                    streams == null ? new CopyOnWriteArrayList<>() : streams;
            current.addIfAbsent(stream);
            return current;
        });
    }

    public void unregister(String sessionId, AgentStreamProjection stream) {
        unregister(sessionId, stream, () -> { });
    }

    public void unregister(String sessionId,
                           AgentStreamProjection stream,
                           Runnable onSessionIdle) {
        if (StringUtils.isBlank(sessionId) || stream == null) {
            return;
        }
        String key = sessionId.trim();
        bySession.compute(key, (ignored, streams) -> {
            if (streams == null) {
                onSessionIdle.run();
                return null;
            }
            streams.remove(stream);
            if (streams.isEmpty()) {
                onSessionIdle.run();
                return null;
            }
            return streams;
        });
    }

    public List<AgentStreamProjection> listLive(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return List.of();
        }
        CopyOnWriteArrayList<AgentStreamProjection> streams = bySession.get(sessionId.trim());
        if (streams == null || streams.isEmpty()) {
            return List.of();
        }
        List<AgentStreamProjection> live = new ArrayList<>();
        for (AgentStreamProjection stream : streams) {
            if (stream != null && !stream.isClosed()) {
                live.add(stream);
            }
        }
        return live;
    }

    public boolean hasLive(String sessionId) {
        return !listLive(sessionId).isEmpty();
    }
}
