package org.wwz.ai.config.executor;

import lombok.extern.slf4j.Slf4j;
import org.wwz.ai.domain.agent.runtime.executor.AgentWorkExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 带显式 in-flight 上限的虚拟线程执行器。
 *
 * <p>虚拟线程负责降低阻塞等待的线程成本，Semaphore 负责保护模型、HTTP 和数据库等下游容量。</p>
 */
@Slf4j
public final class BoundedVirtualThreadExecutor implements AgentWorkExecutor, AutoCloseable {

    private final String name;
    private final int maxConcurrency;
    private final Semaphore permits;
    private final ExecutorService delegate;
    private final Set<TrackedTask> pendingTasks = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean shutdown = new AtomicBoolean();
    private final AtomicInteger runningTasks = new AtomicInteger();
    private final AtomicLong submittedTasks = new AtomicLong();
    private final AtomicLong completedTasks = new AtomicLong();
    private final AtomicLong rejectedTasks = new AtomicLong();
    private final AtomicLong cancelledTasks = new AtomicLong();
    private final AtomicLong permitWaitNanos = new AtomicLong();
    private final AtomicLong taskDurationNanos = new AtomicLong();

    public BoundedVirtualThreadExecutor(String name, int maxConcurrency, String threadNamePrefix) {
        this.name = requireText(name, "name");
        if (maxConcurrency <= 0) {
            throw new IllegalArgumentException("maxConcurrency must be positive");
        }
        this.maxConcurrency = maxConcurrency;
        this.permits = new Semaphore(maxConcurrency);
        ThreadFactory threadFactory = Thread.ofVirtual()
                .name(requireText(threadNamePrefix, "threadNamePrefix"), 0)
                .factory();
        this.delegate = Executors.newThreadPerTaskExecutor(threadFactory);
        log.info("Agent executor started: name={}, mode=virtual, capacity={}, rejection=RejectedExecutionException",
                name, maxConcurrency);
    }

    @Override
    public void execute(Runnable command, String scene, String requestId) {
        Objects.requireNonNull(command, "command must not be null");
        long admissionStarted = System.nanoTime();
        if (shutdown.get() || !permits.tryAcquire()) {
            rejectedTasks.incrementAndGet();
            log.warn("Agent executor rejected: name={}, scene={}, requestId={}, capacity={}, running={}, shutdown={}",
                    name, scene, requestId, maxConcurrency, runningTasks.get(), shutdown.get());
            throw new RejectedExecutionException("Agent executor is full: " + name);
        }

        permitWaitNanos.addAndGet(System.nanoTime() - admissionStarted);
        submittedTasks.incrementAndGet();
        TrackedTask task = new TrackedTask(command, scene, requestId);
        pendingTasks.add(task);
        try {
            delegate.execute(task);
        } catch (RejectedExecutionException e) {
            task.cancelBeforeStart();
            rejectedTasks.incrementAndGet();
            throw e;
        }
    }

    public String getName() {
        return name;
    }

    public boolean isVirtual() {
        return true;
    }

    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    public int getRunningTasks() {
        return runningTasks.get();
    }

    public ExecutorSnapshot snapshot() {
        return new ExecutorSnapshot(
                name,
                true,
                maxConcurrency,
                runningTasks.get(),
                submittedTasks.get(),
                completedTasks.get(),
                rejectedTasks.get(),
                cancelledTasks.get(),
                permitWaitNanos.get(),
                taskDurationNanos.get()
        );
    }

    public void shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            delegate.shutdown();
        }
    }

    public List<Runnable> shutdownNow() {
        shutdown.set(true);
        List<Runnable> notStarted = new ArrayList<>();
        for (TrackedTask task : pendingTasks) {
            if (task.cancelBeforeStart()) {
                notStarted.add(task);
            }
        }
        notStarted.addAll(delegate.shutdownNow());
        return notStarted;
    }

    public boolean isShutdown() {
        return shutdown.get();
    }

    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    @Override
    public void close() {
        shutdown();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public record ExecutorSnapshot(
            String name,
            boolean virtual,
            int capacity,
            int runningTasks,
            long submittedTasks,
            long completedTasks,
            long rejectedTasks,
            long cancelledTasks,
            long permitWaitNanos,
            long taskDurationNanos) {
    }

    private final class TrackedTask implements Runnable {

        private final Runnable command;
        private final String scene;
        private final String requestId;
        private final AtomicBoolean started = new AtomicBoolean();
        private final AtomicBoolean released = new AtomicBoolean();

        private TrackedTask(Runnable command, String scene, String requestId) {
            this.command = command;
            this.scene = scene;
            this.requestId = requestId;
        }

        @Override
        public void run() {
            if (!started.compareAndSet(false, true)) {
                return;
            }
            pendingTasks.remove(this);
            if (released.get()) {
                return;
            }
            long taskStarted = System.nanoTime();
            runningTasks.incrementAndGet();
            try {
                if (Thread.currentThread().isInterrupted()) {
                    cancelledTasks.incrementAndGet();
                    return;
                }
                command.run();
                if (command instanceof Future<?> future && future.isCancelled()) {
                    cancelledTasks.incrementAndGet();
                } else {
                    completedTasks.incrementAndGet();
                }
            } catch (Throwable error) {
                log.debug("Agent executor task failed: name={}, scene={}, requestId={}",
                        name, scene, requestId, error);
                if (error instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (error instanceof Error fatalError) {
                    throw fatalError;
                }
                throw new RuntimeException(error);
            } finally {
                runningTasks.decrementAndGet();
                taskDurationNanos.addAndGet(System.nanoTime() - taskStarted);
                releasePermit();
            }
        }

        private boolean cancelBeforeStart() {
            if (!started.compareAndSet(false, true)) {
                return false;
            }
            pendingTasks.remove(this);
            cancelledTasks.incrementAndGet();
            releasePermit();
            return true;
        }

        private void releasePermit() {
            if (released.compareAndSet(false, true)) {
                permits.release();
            }
        }
    }
}
