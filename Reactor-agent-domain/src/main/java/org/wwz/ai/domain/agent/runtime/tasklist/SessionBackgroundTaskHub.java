package org.wwz.ai.domain.agent.runtime.tasklist;

import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内按 session 共享后台任务 registry。
 * <p>
 * 同一 JVM 内跨 request 轮次可复用 Future / 内存态，避免「上一轮 Agent 后台跑着、
 * 下一轮 TaskOutput 新建空 registry → not_found」。
 * 跨进程/重启仍依赖 {@link TasklistPersistencePort}。
 */
public final class SessionBackgroundTaskHub {

    private static final ConcurrentHashMap<String, RuntimeBackgroundTaskRegistry> BY_SESSION =
            new ConcurrentHashMap<>();

    private SessionBackgroundTaskHub() {
    }

    public static RuntimeBackgroundTaskRegistry getOrCreate(String sessionId,
                                                            TasklistPersistencePort persistence) {
        String key = StringUtils.defaultIfBlank(sessionId, "default").trim();
        return BY_SESSION.computeIfAbsent(key, sid -> new RuntimeBackgroundTaskRegistry(sid, persistence));
    }

    /** 测试或会话清理用 */
    public static void evict(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return;
        }
        BY_SESSION.remove(sessionId.trim());
    }

    public static void clearAll() {
        BY_SESSION.clear();
    }
}
