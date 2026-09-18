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

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<AgentResponseProjectionStream>> bySession =
            new ConcurrentHashMap<>();

    public void register(String sessionId, AgentResponseProjectionStream stream) {
        if (StringUtils.isBlank(sessionId) || stream == null) {
            return;
        }
        bySession.computeIfAbsent(sessionId.trim(), key -> new CopyOnWriteArrayList<>()).addIfAbsent(stream);
    }

    public void unregister(String sessionId, AgentResponseProjectionStream stream) {
        if (StringUtils.isBlank(sessionId) || stream == null) {
            return;
        }
        String key = sessionId.trim();
        CopyOnWriteArrayList<AgentResponseProjectionStream> streams = bySession.get(key);
        if (streams == null) {
            return;
        }
        streams.remove(stream);
        if (streams.isEmpty()) {
            bySession.remove(key, streams);
        }
    }

    public List<AgentResponseProjectionStream> listLive(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return List.of();
        }
        CopyOnWriteArrayList<AgentResponseProjectionStream> streams = bySession.get(sessionId.trim());
        if (streams == null || streams.isEmpty()) {
            return List.of();
        }
        List<AgentResponseProjectionStream> live = new ArrayList<>();
        for (AgentResponseProjectionStream stream : streams) {
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
