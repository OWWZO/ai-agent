package org.wwz.ai.domain.agent.runtime.tasklist;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.cancel.PendingInjectMessage;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 进程内按 session+agentId 的子 Agent inject 邮箱（主→子运行中指导）。
 * 同步阻塞派发期间主 Agent 无法发信；仅后台/仍在跑的子 Agent 可投递。
 */
public final class SessionAgentMailboxHub {

    private static final ConcurrentHashMap<String, Mailbox> BOXES = new ConcurrentHashMap<>();

    private SessionAgentMailboxHub() {
    }

    public static ConcurrentLinkedQueue<PendingInjectMessage> queue(String sessionId, String agentId) {
        return box(sessionId, agentId).queue;
    }

    public static void markActive(String sessionId, String agentId, boolean active) {
        if (StringUtils.isBlank(agentId)) {
            return;
        }
        box(sessionId, agentId).active.set(active);
    }

    public static boolean isActive(String sessionId, String agentId) {
        if (StringUtils.isBlank(agentId)) {
            return false;
        }
        Mailbox box = BOXES.get(key(sessionId, agentId));
        return box != null && box.active.get();
    }

    /**
     * @return 队列长度；目标不存在时创建后投递
     */
    public static int offer(String sessionId, String agentId, PendingInjectMessage message) {
        if (message == null || StringUtils.isBlank(message.getText())) {
            return 0;
        }
        Mailbox box = box(sessionId, agentId);
        box.queue.offer(message);
        return box.queue.size();
    }

    public static void evict(String sessionId, String agentId) {
        BOXES.remove(key(sessionId, agentId));
    }

    public static void clearAll() {
        BOXES.clear();
    }

    private static Mailbox box(String sessionId, String agentId) {
        return BOXES.computeIfAbsent(key(sessionId, agentId), k -> new Mailbox());
    }

    private static String key(String sessionId, String agentId) {
        String sid = StringUtils.defaultIfBlank(sessionId, "default").trim();
        String aid = StringUtils.defaultIfBlank(agentId, "unknown").trim();
        return sid + ":" + aid;
    }

    private static final class Mailbox {
        private final ConcurrentLinkedQueue<PendingInjectMessage> queue = new ConcurrentLinkedQueue<>();
        private final AtomicBoolean active = new AtomicBoolean(false);
    }
}
