package org.wwz.ai.infrastructure.memory;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.memory.ltm.BackgroundReviewService;
import org.wwz.ai.domain.agent.memory.ltm.CuratedMemoryStore;
import org.wwz.ai.domain.agent.memory.ltm.LtmAgentForkSupport;
import org.wwz.ai.domain.agent.memory.ltm.LtmForkExecutionEvent;
import org.wwz.ai.domain.agent.memory.ltm.LtmForkRunResult;
import org.wwz.ai.domain.agent.memory.ltm.LtmOwner;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.infrastructure.dao.reactor.ILtmForkExecutionDao;

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
    private final ObjectProvider<ILtmForkExecutionDao> ltmForkExecutionDaoProvider;

    private final Map<String, AtomicInteger> turnCounters = new ConcurrentHashMap<>();
    private final Map<String, Boolean> inFlight = new ConcurrentHashMap<>();

    private volatile boolean enabled = false;
    private volatile int nudgeInterval = 10;
    private volatile int maxSteps = 6;
    private volatile long timeoutSeconds = 90L;

    public BackgroundReviewServiceImpl(ObjectProvider<ReactorRuntimeDependencies> runtimeDependenciesProvider,
                                       ObjectProvider<CuratedMemoryStore> curatedMemoryStoreProvider,
                                       ObjectProvider<ILtmForkExecutionDao> ltmForkExecutionDaoProvider) {
        this.runtimeDependenciesProvider = runtimeDependenciesProvider;
        this.curatedMemoryStoreProvider = curatedMemoryStoreProvider;
        this.ltmForkExecutionDaoProvider = ltmForkExecutionDaoProvider;
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
            recordSkip(sessionId, requestId, null, conversationSnapshot, "null-owner");
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
            recordSkip(sessionId, requestId, owner, conversationSnapshot, "in-flight");
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
                recordSkip(sessionId, requestId, owner, snapshot, "thread-error");
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
            recordSkip(sessionId, requestId, owner, snapshot,
                    store == null ? "store-null" : "deps-null");
            return;
        }
        log.info("background-review fork start sessionId={} snapshotMsgs={}", sessionId,
                snapshot == null ? 0 : snapshot.size());
        LtmForkRunResult result = LtmAgentForkSupport.runMemoryOnlyFork(
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
        log.info("background-review fork done sessionId={} status={} appliedApprox={} durationMs={}",
                sessionId, result.getStatus(), result.appliedOrZero(), result.getDurationMs());
        recordResult(sessionId, requestId, owner, snapshot == null ? 0 : snapshot.size(), result);
    }

    private void recordSkip(String sessionId,
                            String requestId,
                            LtmOwner owner,
                            List<Message> snapshot,
                            String reason) {
        LtmForkExecutionEvent event = baseEvent(sessionId, requestId, owner)
                .forkKind(LtmForkExecutionEvent.KIND_BG_REVIEW)
                .status(LtmForkExecutionEvent.STATUS_SKIPPED)
                .skipReason(reason)
                .snapshotMessageCount(snapshot == null ? 0 : snapshot.size())
                .maxSteps(maxSteps)
                .timeoutSeconds(timeoutSeconds)
                .appliedCount(0)
                .build();
        persist(event);
    }

    private void recordResult(String sessionId,
                              String requestId,
                              LtmOwner owner,
                              int snapshotMsgs,
                              LtmForkRunResult result) {
        LtmForkExecutionEvent event = baseEvent(sessionId, requestId, owner)
                .forkKind(LtmForkExecutionEvent.KIND_BG_REVIEW)
                .forkRequestId(result.getForkRequestId())
                .status(result.getStatus())
                .skipReason(result.getSkipReason())
                .snapshotMessageCount(snapshotMsgs)
                .maxSteps(maxSteps)
                .timeoutSeconds(timeoutSeconds)
                .durationMs(result.getDurationMs())
                .entriesBefore(result.getEntriesBefore())
                .entriesAfter(result.getEntriesAfter())
                .appliedCount(result.appliedOrZero())
                .errorMessage(StringUtils.left(result.getErrorMessage(), 1000))
                .detailJson(StringUtils.left(result.getWrittenEntriesJson(), 500_000))
                .build();
        persist(event);
    }

    private LtmForkExecutionEvent.LtmForkExecutionEventBuilder baseEvent(String sessionId,
                                                                         String requestId,
                                                                         LtmOwner owner) {
        return LtmForkExecutionEvent.builder()
                .sessionId(StringUtils.defaultIfBlank(sessionId, "unknown"))
                .triggerRequestId(StringUtils.defaultIfBlank(requestId, "unknown"))
                .ownerType(owner == null || owner.getType() == null ? null : owner.getType().name())
                .ownerId(owner == null ? null : owner.getId())
                .deleted(0);
    }

    private void persist(LtmForkExecutionEvent event) {
        try {
            ILtmForkExecutionDao dao = ltmForkExecutionDaoProvider.getIfAvailable();
            if (dao == null || event == null) {
                return;
            }
            dao.insertEvent(event);
        } catch (Exception e) {
            log.warn("record ltm fork review event failed sessionId={}: {}",
                    event == null ? null : event.getSessionId(), e.toString());
        }
    }
}
