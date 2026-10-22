package org.wwz.ai.config.executor;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 保留原拒绝策略，同时为平台线程执行器累计拒绝次数。
 */
public final class CountingRejectedExecutionHandler implements RejectedExecutionHandler {

    private final RejectedExecutionHandler delegate;
    private final AtomicLong rejectedTasks = new AtomicLong();

    public CountingRejectedExecutionHandler(RejectedExecutionHandler delegate) {
        this.delegate = delegate;
    }

    @Override
    public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
        rejectedTasks.incrementAndGet();
        delegate.rejectedExecution(runnable, executor);
    }

    public long getRejectedTasks() {
        return rejectedTasks.get();
    }

    public RejectedExecutionHandler getDelegate() {
        return delegate;
    }
}
