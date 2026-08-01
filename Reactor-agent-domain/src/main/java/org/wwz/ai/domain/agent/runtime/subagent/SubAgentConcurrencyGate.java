package org.wwz.ai.domain.agent.runtime.subagent;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 同步子 Agent 全局并发闸门。
 * 限制同时阻塞在 SubAgentRunner 中的嵌套 Agent 数量，避免打满 task/tool 池。
 */
public final class SubAgentConcurrencyGate {

    public static final int DEFAULT_MAX_CONCURRENT = 4;
    public static final long DEFAULT_ACQUIRE_TIMEOUT_SECONDS = 30L;

    private final Semaphore semaphore;
    private final int maxConcurrent;
    private final long acquireTimeoutSeconds;

    public SubAgentConcurrencyGate(int maxConcurrent, long acquireTimeoutSeconds) {
        this.maxConcurrent = Math.max(1, maxConcurrent);
        this.acquireTimeoutSeconds = Math.max(0L, acquireTimeoutSeconds);
        this.semaphore = new Semaphore(this.maxConcurrent, true);
    }

    public static SubAgentConcurrencyGate defaults() {
        return new SubAgentConcurrencyGate(DEFAULT_MAX_CONCURRENT, DEFAULT_ACQUIRE_TIMEOUT_SECONDS);
    }

    public int getMaxConcurrent() {
        return maxConcurrent;
    }

    public long getAcquireTimeoutSeconds() {
        return acquireTimeoutSeconds;
    }

    public int availablePermits() {
        return semaphore.availablePermits();
    }

    /**
     * 在许可内执行；拿不到许可时返回 null（由调用方转失败结果）。
     */
    public <T> T runWithPermit(Supplier<T> action) throws InterruptedException {
        boolean acquired = acquireTimeoutSeconds == 0L
                ? semaphore.tryAcquire()
                : semaphore.tryAcquire(acquireTimeoutSeconds, TimeUnit.SECONDS);
        if (!acquired) {
            return null;
        }
        try {
            return action.get();
        } finally {
            semaphore.release();
        }
    }
}
