package org.wwz.ai.application.agent.run;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * submit 只登记 run；真正 dispatch 等首个观察者订阅或超时后再 launch。
 */
@Slf4j
@Service
public class AgentRunLaunchGate {

    public static final long AUTO_LAUNCH_MS = 2000L;

    private final ConcurrentHashMap<String, Pending> byRequest = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionToRequest = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final long autoLaunchMs;

    public AgentRunLaunchGate() {
        this(AUTO_LAUNCH_MS);
    }

    public AgentRunLaunchGate(long autoLaunchMs) {
        this.autoLaunchMs = autoLaunchMs;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "agent-run-launch-gate");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void defer(String requestId, String sessionId, Runnable launch) {
        if (StringUtils.isBlank(requestId) || launch == null) {
            return;
        }
        if (autoLaunchMs <= 0) {
            launch.run();
            return;
        }
        cancel(requestId);
        ScheduledFuture<?> timeout = scheduler.schedule(
                () -> launchNow(requestId), autoLaunchMs, TimeUnit.MILLISECONDS);
        Pending pending = new Pending(sessionId, launch, timeout);
        byRequest.put(requestId, pending);
        if (StringUtils.isNotBlank(sessionId)) {
            sessionToRequest.put(sessionId, requestId);
        }
    }

    public void launchBySession(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return;
        }
        String requestId = sessionToRequest.get(sessionId);
        if (requestId != null) {
            launchNow(requestId);
        }
    }

    public void launchNow(String requestId) {
        if (StringUtils.isBlank(requestId)) {
            return;
        }
        Pending pending = byRequest.remove(requestId);
        if (pending == null) {
            return;
        }
        if (StringUtils.isNotBlank(pending.sessionId)) {
            sessionToRequest.remove(pending.sessionId, requestId);
        }
        if (pending.timeout != null) {
            pending.timeout.cancel(false);
        }
        try {
            pending.launch.run();
        } catch (RuntimeException e) {
            log.warn("deferred launch failed requestId={}", requestId, e);
            throw e;
        }
    }

    /**
     * @return true 表示 launch 仍在等待，已被取消，后续不会再启动
     */
    public boolean cancel(String requestId) {
        if (StringUtils.isBlank(requestId)) {
            return false;
        }
        Pending pending = byRequest.remove(requestId);
        if (pending == null) {
            return false;
        }
        if (StringUtils.isNotBlank(pending.sessionId)) {
            sessionToRequest.remove(pending.sessionId, requestId);
        }
        if (pending.timeout != null) {
            pending.timeout.cancel(false);
        }
        return true;
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
        byRequest.clear();
        sessionToRequest.clear();
    }

    private static final class Pending {
        private final String sessionId;
        private final Runnable launch;
        private final ScheduledFuture<?> timeout;

        private Pending(String sessionId, Runnable launch, ScheduledFuture<?> timeout) {
            this.sessionId = sessionId;
            this.launch = launch;
            this.timeout = timeout;
        }
    }
}
