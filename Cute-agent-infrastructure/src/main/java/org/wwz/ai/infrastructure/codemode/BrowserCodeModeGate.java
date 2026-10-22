package org.wwz.ai.infrastructure.codemode;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 同一用户的浏览器命令串行闸门。
 * <p>
 * Browser Relay 侧没有命令级队列，同一用户并发下发命令会互相踩页面状态，
 * 因此 Code Mode 内的 browser 调用按 userId 串行；不同用户之间互不影响。
 * </p>
 */
public final class BrowserCodeModeGate {

    private static final Map<String, Object> LOCKS = new ConcurrentHashMap<>();

    private BrowserCodeModeGate() {
    }

    public static <T> T withUserLock(String userId, java.util.function.Supplier<T> supplier) {
        Object lock = LOCKS.computeIfAbsent(userId == null || userId.isBlank() ? "anonymous" : userId,
                key -> new Object());
        synchronized (lock) {
            return supplier.get();
        }
    }
}
