package org.wwz.ai.domain.agent.runtime.cancel;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 单轮 Agent run 的协作式取消标志（用户点停止 / SSE 断开）。
 */
public class RunCancellation {

    public static final String REASON_USER_STOP = "user_stop";
    public static final String REASON_CLIENT_DISCONNECT = "client_disconnect";

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile String reason;
    private volatile long cancelledAtMs;

    public boolean cancel(String reason) {
        if (!cancelled.compareAndSet(false, true)) {
            return false;
        }
        this.reason = reason == null ? REASON_USER_STOP : reason;
        this.cancelledAtMs = System.currentTimeMillis();
        return true;
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public String getReason() {
        return reason;
    }

    public long getCancelledAtMs() {
        return cancelledAtMs;
    }
}
