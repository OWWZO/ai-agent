package org.wwz.ai.domain.agent.runtime.tool.cli;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/** 只覆盖一个 capture root 的目录级互斥资源。 */
public final class CliArtifactCaptureLease implements AutoCloseable {

    private static final CliArtifactCaptureLease NOOP =
            new CliArtifactCaptureLease(null, null, null, null, true);

    private final String captureId;
    private final String captureRoot;
    private final String captureRootAbsolute;
    private final ReentrantLock lock;
    private final Runnable releaser;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final boolean noop;

    CliArtifactCaptureLease(String captureId,
                            String captureRoot,
                            String captureRootAbsolute,
                            ReentrantLock lock,
                            boolean noop) {
        this(captureId, captureRoot, captureRootAbsolute, lock, noop, null);
    }

    CliArtifactCaptureLease(String captureId,
                            String captureRoot,
                            String captureRootAbsolute,
                            ReentrantLock lock,
                            boolean noop,
                            Runnable releaser) {
        this.captureId = captureId;
        this.captureRoot = captureRoot;
        this.captureRootAbsolute = captureRootAbsolute;
        this.lock = lock;
        this.noop = noop;
        this.releaser = releaser;
    }

    public static CliArtifactCaptureLease noop() {
        return NOOP;
    }

    public String getCaptureId() {
        return captureId;
    }

    public String getCaptureRoot() {
        return captureRoot;
    }

    public String getCaptureRootAbsolute() {
        return captureRootAbsolute;
    }

    public boolean isNoop() {
        return noop;
    }

    @Override
    public void close() {
        if (noop || !closed.compareAndSet(false, true)) {
            return;
        }
        if (releaser != null) {
            releaser.run();
            return;
        }
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
