package org.wwz.ai.domain.agent.runtime.util;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;

import java.util.concurrent.*;

/**
 * 延期保留的线程工具类。
 * <p>主链路优先使用 AgentExecutorSupport 的受控执行器，本类仅服务历史调用方。</p>
 */
public class ThreadUtil {
    private static ThreadPoolExecutor executor = null;

    private ThreadUtil() {
    }

    public static synchronized void initPool(int poolSize) {
        if (executor == null) {
            ThreadFactory threadFactory = (new BasicThreadFactory.Builder()).namingPattern("exe-pool-%d").daemon(true).build();
            RejectedExecutionHandler handler = (r, executor) -> {
            };
            int maxPoolSize = Math.max(poolSize, 1000);
            executor = new ThreadPoolExecutor(poolSize, maxPoolSize, 60000L, TimeUnit.MILLISECONDS, new SynchronousQueue(), threadFactory, handler);
        }

    }

    public static void execute(Runnable runnable) {
        if (executor == null) {
            initPool(100);
        }

        executor.execute(runnable);
    }

    public static CountDownLatch getCountDownLatch(int count) {
        return new CountDownLatch(count);
    }

    public static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (Exception var2) {
        }
    }

    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException var3) {
        }
    }


}
