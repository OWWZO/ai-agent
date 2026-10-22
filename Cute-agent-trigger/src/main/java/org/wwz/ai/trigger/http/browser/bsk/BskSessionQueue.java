package org.wwz.ai.trigger.http.browser.bsk;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;

/**
 * 按 {@code sessionId} 串行化的转发队列（契约 T6.5 / §6.3 "保证同 sessionId 串行"）。
 *
 * <p><b>与 bsk daemon 的语义差异（有意为之）</b>：bsk 自己的
 * {@code crates/bsk-cli/src/daemon/queue.rs} 对同一 session 的并发提交
 * <b>不排队而是直接拒绝</b>（{@code DispatchError::SessionBusy} →
 * {@code timeout} + {@code data.reason="session_busy"}）。021 spec 的验收
 * 是"并发发 10 个 RPC 到同一 session，执行顺序与发送顺序一致"，即
 * <b>排队并串行</b>。本类按 spec 实现排队串行；如果要与 bsk daemon 行为
 * 完全对齐，改用 {@link #newRejectingWhenBusy()} 构造即可获得
 * {@code session_busy} 拒绝语义。</p>
 *
 * <p>顺序保证用<b>显式 FIFO 队列</b>而不是"公平锁 + 提交即调度"：
 * 后者只能保证锁的公平性，无法保证线程池调度顺序，实测会得到
 * {@code 0,2,1,...}。这里把任务按提交顺序入队，只有队首被执行，
 * 因此顺序严格等于 {@code submit()} 的调用顺序。</p>
 *
 * <p>不同 session 之间完全并发，互不阻塞。</p>
 */
public class BskSessionQueue {

    /** 每 session 一条待执行队列。 */
    private final ConcurrentHashMap<String, SessionLane> lanes = new ConcurrentHashMap<>();

    private final boolean rejectWhenBusy;
    private final Executor executor;

    /** 默认：排队并串行（021 spec 语义）。 */
    public BskSessionQueue() {
        this(false, ForkJoinPool.commonPool());
    }

    /** 指定执行器的构造，测试用。 */
    public BskSessionQueue(Executor executor) {
        this(false, executor);
    }

    /**
     * bsk daemon 对齐模式：session 忙时新提交立即以
     * {@code session_busy} 拒绝，而不是排队。
     */
    public static BskSessionQueue newRejectingWhenBusy() {
        return new BskSessionQueue(true, ForkJoinPool.commonPool());
    }

    private BskSessionQueue(boolean rejectWhenBusy, Executor executor) {
        this.rejectWhenBusy = rejectWhenBusy;
        this.executor = executor;
    }

    /**
     * 提交一个任务到 {@code sessionId} 的串行队列。
     *
     * <p>返回的 future 在任务执行完毕（或异常）时完成；异常会记入 future
     * 而不是打断队列后续任务。</p>
     *
     * @throws RejectedExecutionException {@code rejectWhenBusy} 模式下该 session 已有任务在跑
     */
    public CompletableFuture<Void> submit(String sessionId, Runnable task) {
        if (sessionId == null || task == null) {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalArgumentException("sessionId and task are required"));
            return failed;
        }
        SessionLane lane = lanes.computeIfAbsent(sessionId, k -> new SessionLane());
        CompletableFuture<Void> future = new CompletableFuture<>();
        boolean shouldSchedule;
        synchronized (lane) {
            if (rejectWhenBusy && lane.running) {
                throw new RejectedExecutionException("session_busy: session already has an unfinished command");
            }
            lane.queue.addLast(new Job(task, future));
            shouldSchedule = !lane.running;
            if (shouldSchedule) {
                lane.running = true;
            }
        }
        if (shouldSchedule) {
            scheduleDrain(sessionId, lane);
        }
        return future;
    }

    /** 当前该 session 是否有任务正在执行（观测用）。 */
    public boolean isBusy(String sessionId) {
        SessionLane lane = lanes.get(sessionId);
        if (lane == null) {
            return false;
        }
        synchronized (lane) {
            return lane.running;
        }
    }

    /** 清掉空闲 session 的队列，防止长生命周期 map 无界增长。 */
    public void cleanupIdle() {
        lanes.entrySet().removeIf(entry -> {
            SessionLane lane = entry.getValue();
            synchronized (lane) {
                return !lane.running && lane.queue.isEmpty();
            }
        });
    }

    /** 测试与观测用：当前登记的 session 数。 */
    public int trackedSessions() {
        return lanes.size();
    }

    /** 供诊断：session 名到 lane 的只读视图。 */
    Map<String, SessionLane> lanesView() {
        return Map.copyOf(lanes);
    }

    private void scheduleDrain(String sessionId, SessionLane lane) {
        executor.execute(() -> {
            while (true) {
                Job job;
                synchronized (lane) {
                    job = lane.queue.pollFirst();
                    if (job == null) {
                        lane.running = false;
                        return;
                    }
                }
                try {
                    job.task.run();
                    job.future.complete(null);
                } catch (Throwable t) {
                    job.future.completeExceptionally(t);
                }
                // 继续取下一个，保持同一 session 的 FIFO 与串行。
            }
        });
    }

    /** 一条 session 的串行车道。 */
    static final class SessionLane {
        final Deque<Job> queue = new ArrayDeque<>();
        boolean running;

        /** 当前排队长度（观测用）。 */
        int queued() {
            synchronized (this) {
                return queue.size();
            }
        }
    }

    /** 一个排队任务。 */
    private record Job(Runnable task, CompletableFuture<Void> future) {
    }
}
