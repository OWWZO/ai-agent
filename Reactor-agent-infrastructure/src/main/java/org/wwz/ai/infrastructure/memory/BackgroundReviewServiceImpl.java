package org.wwz.ai.infrastructure.memory;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.memory.ltm.BackgroundReviewService;
import org.wwz.ai.domain.agent.memory.ltm.CuratedMemoryStore;
import org.wwz.ai.domain.agent.memory.ltm.LtmAgentForkSupport;
import org.wwz.ai.domain.agent.memory.ltm.LtmOwner;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.dto.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Hermes 对齐：同 runtime 全量 messages 重放 + 仅 memory 工具的后台 fork。
 */
@Slf4j
@Service
public class BackgroundReviewServiceImpl implements BackgroundReviewService {

    private final ObjectProvider<ReactorRuntimeDependencies> runtimeDependenciesProvider;
    private final ObjectProvider<CuratedMemoryStore> curatedMemoryStoreProvider;

    private final Map<String, AtomicInteger> turnCounters = new ConcurrentHashMap<>();
    private final Map<String, Boolean> inFlight = new ConcurrentHashMap<>();

    private volatile boolean enabled = false;
    private volatile int nudgeInterval = 10;
    private volatile int maxSteps = 6;
    private volatile long timeoutSeconds = 90L;

    public BackgroundReviewServiceImpl(ObjectProvider<ReactorRuntimeDependencies> runtimeDependenciesProvider,
                                       ObjectProvider<CuratedMemoryStore> curatedMemoryStoreProvider) {
        this.runtimeDependenciesProvider = runtimeDependenciesProvider;
        this.curatedMemoryStoreProvider = curatedMemoryStoreProvider;
    }

    public void configure(boolean enabled, int nudgeInterval, long timeoutSeconds, int maxSteps) {
        this.enabled = enabled;
        this.nudgeInterval = Math.max(1, nudgeInterval);
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 90L;
        this.maxSteps = Math.max(1, maxSteps);
        log.info("background-review configured enabled={} nudgeInterval={} timeoutSec={} maxSteps={}",
                this.enabled, this.nudgeInterval, this.timeoutSeconds, this.maxSteps);
    }

    /** 兼容旧 configure 签名 */
    public void configure(boolean enabled, int nudgeInterval, int materialMaxChars, long timeoutSeconds) {
        configure(enabled, nudgeInterval, timeoutSeconds, 6);
    }

    @Override
    public void maybeScheduleAfterSuccessTurn(String sessionId,
                                              String requestId,
                                              LtmOwner owner,
                                              String userQuery,
                                              String assistantSummary,
                                              List<Message> conversationSnapshot,
                                              String parentSystemPrompt) {
        if (!enabled) {
            log.info("background-review skip reason=disabled sessionId={}", sessionId);
            return;
        }
        if (owner == null) {
            log.info("background-review skip reason=null-owner sessionId={}", sessionId);
            return;
        }
        if (StringUtils.isBlank(sessionId)) {
            log.info("background-review skip reason=blank-sessionId");
            return;
        }
        int count = turnCounters.computeIfAbsent(sessionId, k -> new AtomicInteger(0)).incrementAndGet();
        log.info("background-review tick sessionId={} count={}/{} requestId={} snapshotMsgs={}",
                sessionId, count, nudgeInterval, requestId,
                conversationSnapshot == null ? 0 : conversationSnapshot.size());
        if (count % nudgeInterval != 0) {
            log.info("background-review skip reason=interval sessionId={} count={} needMultipleOf={}",
                    sessionId, count, nudgeInterval);
            return;
        }
        if (inFlight.putIfAbsent(sessionId, Boolean.TRUE) != null) {
            log.info("background-review skip reason=in-flight sessionId={}", sessionId);
            return;
        }

        List<Message> snapshot = conversationSnapshot == null
                ? List.of()
                : new ArrayList<>(conversationSnapshot);
        String system = parentSystemPrompt;

        Thread t = new Thread(() -> {
            try {
                runReviewFork(sessionId, requestId, owner, snapshot, system);
            } catch (Exception e) {
                log.warn("background-review fork error sessionId={}: {}", sessionId, e.toString(), e);
            } finally {
                inFlight.remove(sessionId);
            }
        }, "ltm-bg-review-" + sessionId);
        t.setDaemon(true);
        t.start();
        log.info("background-review scheduled on daemon thread sessionId={}", sessionId);
    }

    private void runReviewFork(String sessionId,
                               String requestId,
                               LtmOwner owner,
                               List<Message> snapshot,
                               String parentSystemPrompt) {
        CuratedMemoryStore store = curatedMemoryStoreProvider.getIfAvailable();
        ReactorRuntimeDependencies deps = runtimeDependenciesProvider.getIfAvailable();
        if (store == null || deps == null) {
            log.warn("background-review abort storeOrDepsNull sessionId={} store={} deps={}",
                    sessionId, store != null, deps != null);
            return;
        }
        log.info("background-review fork start sessionId={} snapshotMsgs={}", sessionId,
                snapshot == null ? 0 : snapshot.size());
        int applied = LtmAgentForkSupport.runMemoryOnlyFork(
                deps,
                store,
                owner,
                sessionId,
                requestId,
                parentSystemPrompt,
                snapshot,
                LtmAgentForkSupport.REVIEW_DIRECTIVE,
                maxSteps,
                timeoutSeconds,
                "bg-review");
        log.info("background-review fork done sessionId={} appliedApprox={}", sessionId, applied);
    }
}
