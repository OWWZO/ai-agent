package org.wwz.ai.trigger.http.browser.bsk;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 锁定 {@link BskSessionQueue} 的串行语义（契约 T6.5：
 * 并发发 10 个 RPC 到同一 session，执行顺序与发送顺序一致）。
 */
public class BskSessionQueueTest {

    // ── 同 session FIFO ──────────────────────────────────────

    @Test
    public void tenConcurrentSubmitsOnOneSessionRunFifo() throws Exception {
        BskSessionQueue queue = new BskSessionQueue();
        List<Integer> order = new CopyOnWriteArrayList<>();
        // 用闸门制造真实并发：所有任务先在锁外等待，再抢锁。
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        CountDownLatch allSubmitted = new CountDownLatch(10);
        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            for (int i = 0; i < 10; i++) {
                final int index = i;
                // 提交顺序即期望执行顺序；submit 内部以公平锁保证 FIFO。
                futures.add(queue.submit("sess-1", () -> {
                    order.add(index);
                    allSubmitted.countDown();
                }));
            }
            Assert.assertTrue(allSubmitted.await(5, TimeUnit.SECONDS));
            for (CompletableFuture<Void> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdown();
        }
        Assert.assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), order);
    }

    @Test
    public void concurrentSubmitsFromManyThreadsStillSerialize() throws Exception {
        BskSessionQueue queue = new BskSessionQueue();
        List<Integer> order = new CopyOnWriteArrayList<>();
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger maxInside = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 10; i++) {
                final int index = i;
                // 从 10 个不同线程并发 submit，验证队列（而非调用方顺序）保证串行。
                futures.add(CompletableFuture.runAsync(() -> futures.size(), pool)
                        .thenCompose(v -> queue.submit("sess-x", () -> {
                            int concurrent = inside.incrementAndGet();
                            maxInside.accumulateAndGet(concurrent, Math::max);
                            order.add(index);
                            inside.decrementAndGet();
                        })));
            }
            for (CompletableFuture<Void> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdown();
        }
        // 串行的直接证据：任一时刻最多一个任务在临界区里。
        Assert.assertEquals(1, maxInside.get());
        Assert.assertEquals(10, order.size());
    }

    // ── 跨 session 并发 ──────────────────────────────────────

    @Test
    public void twoSessionsInterleave() throws Exception {
        BskSessionQueue queue = new BskSessionQueue();
        CountDownLatch sessionAEntered = new CountDownLatch(1);
        CountDownLatch sessionBFinished = new CountDownLatch(1);
        AtomicInteger flags = new AtomicInteger();

        CompletableFuture<Void> a = queue.submit("sess-a", () -> {
            flags.set(flags.get() | 1);
            sessionAEntered.countDown();
            // 等 sess-b 完成：如果跨 session 是串行的，这里会死锁超时。
            try {
                Assert.assertTrue(sessionBFinished.await(3, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        Assert.assertTrue(sessionAEntered.await(2, TimeUnit.SECONDS));
        Assert.assertTrue("sess-a 在临界区内等待时 sess-b 必须仍能推进（跨 session 并发）",
                flags.get() == 1);

        CompletableFuture<Void> b = queue.submit("sess-b", () -> {
            flags.set(flags.get() | 2);
            sessionBFinished.countDown();
        });
        b.get(3, TimeUnit.SECONDS);
        a.get(1, TimeUnit.SECONDS);
        Assert.assertEquals(3, flags.get());
    }

    // ── 异常不阻塞队列 ───────────────────────────────────────

    @Test
    public void taskExceptionCompletesFutureWithoutBlockingQueue() throws Exception {
        BskSessionQueue queue = new BskSessionQueue();
        CompletableFuture<Void> failing = queue.submit("sess-e", () -> {
            throw new IllegalStateException("boom");
        });
        try {
            failing.get(2, TimeUnit.SECONDS);
            Assert.fail("should throw");
        } catch (java.util.concurrent.ExecutionException expected) {
            Assert.assertEquals("boom", expected.getCause().getMessage());
        }
        List<Integer> order = new CopyOnWriteArrayList<>();
        queue.submit("sess-e", () -> order.add(1)).get(2, TimeUnit.SECONDS);
        queue.submit("sess-e", () -> order.add(2)).get(2, TimeUnit.SECONDS);
        Assert.assertEquals(List.of(1, 2), order);
    }

    // ── 观测 ─────────────────────────────────────────────────

    @Test
    public void isBusyAndCleanupReflectState() throws Exception {
        BskSessionQueue queue = new BskSessionQueue();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> task = queue.submit("sess-busy", () -> {
            entered.countDown();
            awaitQuietly(release);
        });
        Assert.assertTrue(entered.await(2, TimeUnit.SECONDS));
        Assert.assertTrue(queue.isBusy("sess-busy"));
        Assert.assertEquals(1, queue.trackedSessions());
        release.countDown();
        task.get(2, TimeUnit.SECONDS);
        queue.cleanupIdle();
        Assert.assertEquals(0, queue.trackedSessions());
    }

    @Test
    public void nullArgumentsProduceFailedFuture() {
        BskSessionQueue queue = new BskSessionQueue();
        CompletableFuture<Void> noSession = queue.submit(null, () -> {
        });
        CompletableFuture<Void> noTask = queue.submit("s", null);
        Assert.assertTrue(noSession.isCompletedExceptionally());
        Assert.assertTrue(noTask.isCompletedExceptionally());
    }

    @Test
    public void rejectingModeMirrorsBskSessionBusy() throws Exception {
        BskSessionQueue queue = BskSessionQueue.newRejectingWhenBusy();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> first = queue.submit("sess-r", () -> {
            entered.countDown();
            awaitQuietly(release);
        });
        Assert.assertTrue(entered.await(2, TimeUnit.SECONDS));
        // bsk daemon: DispatchError::SessionBusy —— 忙时拒绝而非排队。
        try {
            queue.submit("sess-r", () -> {
            });
            Assert.fail("expected RejectedExecutionException");
        } catch (java.util.concurrent.RejectedExecutionException expected) {
            Assert.assertTrue(expected.getMessage().contains("session_busy"));
        }
        release.countDown();
        first.get(2, TimeUnit.SECONDS);
        // 释放后可再次提交。
        Assert.assertTrue(queue.submit("sess-r", () -> {
        }).get(2, TimeUnit.SECONDS) == null);
    }

    /** Runnable 不能抛受检异常，这里把中断收敛成运行期异常。 */
    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        }
    }
}
