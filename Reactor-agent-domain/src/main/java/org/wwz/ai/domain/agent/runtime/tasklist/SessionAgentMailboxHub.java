package org.wwz.ai.domain.agent.runtime.tasklist;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.cancel.PendingInjectMessage;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;
import java.util.Collection;

/**
 * 进程内按 session+agentId 的子 Agent inject 邮箱（主→子运行中指导）。
 * 同步阻塞派发期间主 Agent 无法发信；仅后台/仍在跑的子 Agent 可投递。
 */
public final class SessionAgentMailboxHub {

    private static final ConcurrentHashMap<String, Mailbox> BOXES = new ConcurrentHashMap<>();
    static final int MAX_QUEUE_SIZE = 256;
    static final long IDLE_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(30);

    private SessionAgentMailboxHub() {
    }

    public static ConcurrentLinkedQueue<PendingInjectMessage> queue(String sessionId, String agentId) {
        cleanupExpired();
        return box(sessionId, agentId).queue;
    }

    public static void markActive(String sessionId, String agentId, boolean active) {
        if (StringUtils.isBlank(agentId)) {
            return;
        }
        String key = key(sessionId, agentId);
        Mailbox mailbox = box(sessionId, agentId);
        mailbox.active.set(active);
        mailbox.touch();
        if (!active) {
            mailbox.queue.clear();
            BOXES.remove(key, mailbox);
        }
    }

    public static boolean isActive(String sessionId, String agentId) {
        if (StringUtils.isBlank(agentId)) {
            return false;
        }
        cleanupExpired();
        Mailbox box = BOXES.get(key(sessionId, agentId));
        return box != null && box.active.get();
    }

    /**
     * @return 投递后的队列长度；返回 -1 表示邮箱已满且消息未投递
     */
    public static int offer(String sessionId, String agentId, PendingInjectMessage message) {
        if (message == null || StringUtils.isBlank(message.getText())) {
            return 0;
        }
        cleanupExpired();
        Mailbox box = box(sessionId, agentId);
        box.touch();
        return box.queue.offer(message) ? box.queue.size() : -1;
    }

    public static void evict(String sessionId, String agentId) {
        Mailbox mailbox = BOXES.remove(key(sessionId, agentId));
        if (mailbox != null) {
            mailbox.queue.clear();
        }
    }

    public static void clearAll() {
        BOXES.values().forEach(mailbox -> mailbox.queue.clear());
        BOXES.clear();
    }

    private static Mailbox box(String sessionId, String agentId) {
        return BOXES.computeIfAbsent(key(sessionId, agentId), k -> new Mailbox());
    }

    private static void cleanupExpired() {
        long now = System.currentTimeMillis();
        BOXES.forEach((key, mailbox) -> {
            if (!mailbox.active.get() && now - mailbox.lastAccessMs > IDLE_TIMEOUT_MS
                    && BOXES.remove(key, mailbox)) {
                mailbox.queue.clear();
            }
        });
    }

    private static String key(String sessionId, String agentId) {
        String sid = StringUtils.defaultIfBlank(sessionId, "default").trim();
        String aid = StringUtils.defaultIfBlank(agentId, "unknown").trim();
        return sid + ":" + aid;
    }

    private static final class Mailbox {
        private final ConcurrentLinkedQueue<PendingInjectMessage> queue = new BoundedQueue<>(MAX_QUEUE_SIZE);
        private final AtomicBoolean active = new AtomicBoolean(false);
        private volatile long lastAccessMs = System.currentTimeMillis();

        private void touch() {
            lastAccessMs = System.currentTimeMillis();
        }
    }

    private static final class BoundedQueue<E> extends ConcurrentLinkedQueue<E> {
        private final int capacity;

        private BoundedQueue(int capacity) {
            this.capacity = capacity;
        }

        @Override
        public synchronized boolean offer(E element) {
            if (size() >= capacity) {
                return false;
            }
            return super.offer(element);
        }

        @Override
        public synchronized boolean add(E element) {
            return offer(element);
        }

        @Override
        public synchronized boolean addAll(Collection<? extends E> elements) {
            if (elements == this) {
                throw new IllegalArgumentException("cannot add a queue to itself");
            }
            boolean changed = false;
            for (E element : elements) {
                if (!offer(element)) {
                    break;
                }
                changed = true;
            }
            return changed;
        }
    }
}
