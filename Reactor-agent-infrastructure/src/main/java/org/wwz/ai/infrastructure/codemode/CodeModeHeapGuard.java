package org.wwz.ai.infrastructure.codemode;

import org.graalvm.polyglot.Context;
import org.wwz.ai.types.agent.config.CodeModeProperties;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Code Mode 的堆水位熔断器。
 * <p>
 * 每 {@code heapCheckIntervalMs} 采样一次<b>进程级</b>堆使用量，超过
 * {@code maxHeapUsedRatio × maxMemory} 时 {@code close(true)} 当前 Context。
 * </p>
 * <p>
 * <b>这是熔断器不是隔离器</b>：采样基于全局堆，无法区分内存由哪个 Context 分配，
 * 也无法拦截采样窗口内的瞬时大分配。真正的隔离需要独立 Runner JVM。
 * </p>
 */
public final class CodeModeHeapGuard {

    private static final CodeModeHeapGuard SHARED = new CodeModeHeapGuard();

    private final AtomicLong trippedAtMs = new AtomicLong(0L);
    private final ScheduledExecutorService sampler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "code-mode-heap-guard");
        thread.setDaemon(true);
        return thread;
    });

    private CodeModeHeapGuard() {
    }

    public static CodeModeHeapGuard shared() {
        return SHARED;
    }

    /** 熔断冷却期内返回 true，调用方应直接拒绝执行。 */
    public boolean coolingDown(long cooldownMs) {
        long trippedAt = trippedAtMs.get();
        if (trippedAt <= 0L) {
            return false;
        }
        return System.currentTimeMillis() - trippedAt < cooldownMs;
    }

    /** 清除熔断状态；仅供测试与运维手动恢复使用。 */
    public void reset() {
        trippedAtMs.set(0L);
    }

    /** 剩余冷却毫秒；未熔断或已过冷却期返回 0。 */
    public long remainingCooldownMs(long cooldownMs) {
        long trippedAt = trippedAtMs.get();
        if (trippedAt <= 0L) {
            return 0L;
        }
        long remaining = cooldownMs - (System.currentTimeMillis() - trippedAt);
        return Math.max(0L, remaining);
    }

    /**
     * 开始采样。返回的句柄必须在执行结束时 {@link Handle#close()}。
     * 阈值不合法时返回一个不会触发的句柄，保持调用方无需判空。
     */
    public Handle watch(Context context, CodeModeProperties properties) {
        long maxHeap = Runtime.getRuntime().maxMemory();
        double ratio = properties.getMaxHeapUsedRatio();
        long interval = properties.getHeapCheckIntervalMs();
        if (context == null || ratio <= 0D || maxHeap <= 0L || interval <= 0L) {
            return Handle.inactive();
        }
        long threshold = (long) (maxHeap * Math.min(ratio, 1D));
        AtomicBoolean fired = new AtomicBoolean(false);
        ScheduledFuture<?> task = sampler.scheduleAtFixedRate(() -> {
            if (fired.get() || usedHeapBytes() <= threshold) {
                return;
            }
            if (fired.compareAndSet(false, true)) {
                trippedAtMs.set(System.currentTimeMillis());
                closeQuietly(context);
            }
        }, interval, interval, TimeUnit.MILLISECONDS);
        return new Handle(task, fired);
    }

    /** 采样任务句柄；{@link #close()} 幂等。 */
    public static final class Handle {

        private static final Handle INACTIVE = new Handle(null, new AtomicBoolean(false));

        private final ScheduledFuture<?> task;
        private final AtomicBoolean fired;

        private Handle(ScheduledFuture<?> task, AtomicBoolean fired) {
            this.task = task;
            this.fired = fired;
        }

        static Handle inactive() {
            return INACTIVE;
        }

        /** 本次执行是否被堆水位熔断。 */
        public boolean tripped() {
            return fired.get();
        }

        public void close() {
            if (task != null) {
                task.cancel(false);
            }
        }
    }

    private static long usedHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static void closeQuietly(Context context) {
        try {
            context.close(true);
        } catch (RuntimeException ignore) {
            // 关闭失败也要保证采样线程继续存活
        }
    }
}
