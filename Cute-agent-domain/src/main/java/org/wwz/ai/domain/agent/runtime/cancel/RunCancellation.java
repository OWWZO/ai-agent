package org.wwz.ai.domain.agent.runtime.cancel;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 单轮 Agent run 的协作式取消标志（用户主动点击停止）。
 * 在途模型请求或阻塞调用通过 {@link #registerAbort(Runnable)} 挂上中止钩子，cancel 时立刻执行。
 */
public class RunCancellation {

    public static final String REASON_USER_STOP = "user_stop";

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final ConcurrentLinkedQueue<Runnable> abortHooks = new ConcurrentLinkedQueue<>();
    private volatile String reason;
    private volatile long cancelledAtMs;

    public boolean cancel(String reason) {
        if (!cancelled.compareAndSet(false, true)) {
            return false;
        }
        this.reason = reason == null ? REASON_USER_STOP : reason;
        this.cancelledAtMs = System.currentTimeMillis();
        Runnable hook;
        while ((hook = abortHooks.poll()) != null) {
            runAbort(hook);
        }
        return true;
    }

    /**
     * 注册在途工作的中止回调。已取消时立刻执行。
     * 返回值用于正常结束后注销，避免下一次 cancel 误伤已完成的请求。
     * 钩子必须可重复执行（dispose / future.cancel）。
     */
    public Runnable registerAbort(Runnable hook) {
        if (hook == null) {
            return () -> {
            };
        }
        if (isCancelled()) {
            runAbort(hook);
            return () -> {
            };
        }
        abortHooks.add(hook);
        if (isCancelled() && abortHooks.remove(hook)) {
            runAbort(hook);
        }
        return () -> abortHooks.remove(hook);
    }

    private static void runAbort(Runnable hook) {
        try {
            hook.run();
        } catch (RuntimeException ignored) {
            // 单个在途请求中止失败不能挡住其余钩子。
        }
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
