package org.wwz.ai.infrastructure.adapter.port;

import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.adapter.port.BrowserOperationKind;
import org.wwz.ai.domain.agent.adapter.port.BrowserOperationLockPort;
import org.wwz.ai.domain.agent.adapter.port.BrowserOperationLockTimeoutException;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class InProcessBrowserOperationLockAdapter implements BrowserOperationLockPort {

    private final ConcurrentHashMap<Key, Guard> guards = new ConcurrentHashMap<>();

    @Override
    public <T> T execute(BrowserOperationKind kind, String owner, long waitMillis, Callable<T> action) {
        if (kind == null || owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("浏览器锁键不能为空");
        }
        if (action == null) {
            throw new IllegalArgumentException("浏览器操作不能为空");
        }
        Key key = new Key(kind, owner);
        Guard guard = guards.compute(key, (ignored, existing) -> {
            Guard current = existing == null ? new Guard() : existing;
            current.refs.incrementAndGet();
            return current;
        });
        boolean acquired = false;
        try {
            try {
                acquired = guard.lock.tryLock(Math.max(0L, waitMillis), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BrowserOperationLockTimeoutException("等待浏览器锁时被中断");
            }
            if (!acquired) {
                throw new BrowserOperationLockTimeoutException("等待浏览器锁超时");
            }
            try {
                return action.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        } finally {
            if (acquired) {
                guard.lock.unlock();
            }
            guard.refs.decrementAndGet();
            // 只在没有持有者、没有等待者时摘掉条目，避免和正在登记的线程删掉同一把锁。
            guards.compute(key, (ignored, existing) -> {
                if (existing == guard
                        && existing.refs.get() == 0
                        && !existing.lock.isLocked()
                        && !existing.lock.hasQueuedThreads()) {
                    return null;
                }
                return existing;
            });
        }
    }

    int trackedKeyCount() {
        return guards.size();
    }

    private static final class Guard {
        private final ReentrantLock lock = new ReentrantLock(true);
        private final AtomicInteger refs = new AtomicInteger();
    }

    private record Key(BrowserOperationKind kind, String owner) {
    }
}
