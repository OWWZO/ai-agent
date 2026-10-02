package org.wwz.ai.infrastructure.adapter.port;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.adapter.port.BrowserOperationKind;
import org.wwz.ai.domain.agent.adapter.port.BrowserOperationLockTimeoutException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class InProcessBrowserOperationLockAdapterTest {

    @Test
    public void shouldTimeoutWithoutRunningActionAndThenRelease() throws Exception {
        InProcessBrowserOperationLockAdapter lock = new InProcessBrowserOperationLockAdapter();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> lock.execute(BrowserOperationKind.USER_BROWSER, "visitor-1", 1_000L, () -> {
            entered.countDown();
            if (!release.await(2, TimeUnit.SECONDS)) {
                throw new AssertionError("holder was not released");
            }
            return null;
        }));
        holder.start();
        Assert.assertTrue(entered.await(1, TimeUnit.SECONDS));
        Assert.assertEquals(1, lock.trackedKeyCount());

        AtomicBoolean ran = new AtomicBoolean();
        try {
            lock.execute(BrowserOperationKind.USER_BROWSER, "visitor-1", 50L, () -> {
                ran.set(true);
                return null;
            });
            Assert.fail("lock wait should time out");
        } catch (BrowserOperationLockTimeoutException expected) {
            Assert.assertFalse(ran.get());
        }

        release.countDown();
        holder.join(1_000L);
        Assert.assertFalse(holder.isAlive());
        AtomicBoolean after = new AtomicBoolean();
        lock.execute(BrowserOperationKind.USER_BROWSER, "visitor-1", 500L, () -> {
            after.set(true);
            return null;
        });
        Assert.assertTrue(after.get());
        Assert.assertEquals(0, lock.trackedKeyCount());
    }

    @Test
    public void shouldReleaseLockWhenActionThrows() {
        InProcessBrowserOperationLockAdapter lock = new InProcessBrowserOperationLockAdapter();
        try {
            lock.execute(BrowserOperationKind.KERNEL_BROWSER, "visitor-1", 500L, () -> {
                throw new IllegalStateException("start failed");
            });
            Assert.fail("action exception should propagate");
        } catch (IllegalStateException expected) {
            Assert.assertEquals("start failed", expected.getMessage());
        }
        AtomicBoolean ran = new AtomicBoolean();
        lock.execute(BrowserOperationKind.KERNEL_BROWSER, "visitor-1", 500L, () -> {
            ran.set(true);
            return null;
        });
        Assert.assertTrue(ran.get());
        Assert.assertEquals(0, lock.trackedKeyCount());
    }

    @Test
    public void shouldKeepDifferentResourcesIndependent() throws Exception {
        InProcessBrowserOperationLockAdapter lock = new InProcessBrowserOperationLockAdapter();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        CountDownLatch bothEntered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        Thread userBrowser = new Thread(() -> lock.execute(BrowserOperationKind.USER_BROWSER, "visitor-1", 1_000L,
                () -> hold(active, maxActive, bothEntered, release)));
        Thread kernelBrowser = new Thread(() -> lock.execute(BrowserOperationKind.KERNEL_BROWSER, "visitor-1", 1_000L,
                () -> hold(active, maxActive, bothEntered, release)));
        userBrowser.start();
        kernelBrowser.start();
        try {
            Assert.assertTrue(bothEntered.await(1, TimeUnit.SECONDS));
            Assert.assertEquals(2, maxActive.get());
        } finally {
            release.countDown();
        }
        userBrowser.join(1_000L);
        kernelBrowser.join(1_000L);
        Assert.assertEquals(0, lock.trackedKeyCount());
    }

    private static Object hold(AtomicInteger active,
                               AtomicInteger maxActive,
                               CountDownLatch entered,
                               CountDownLatch release) throws InterruptedException {
        int current = active.incrementAndGet();
        maxActive.updateAndGet(previous -> Math.max(previous, current));
        entered.countDown();
        try {
            if (!release.await(2, TimeUnit.SECONDS)) {
                throw new AssertionError("hold was not released");
            }
            return null;
        } finally {
            active.decrementAndGet();
        }
    }
}
