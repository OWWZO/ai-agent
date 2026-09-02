package org.wwz.ai.infrastructure.memory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.ledger.IExecutionLedgerReadRepository;
import org.wwz.ai.domain.agent.ledger.entity.DialogueSession;
import org.wwz.ai.domain.agent.memory.ltm.CuratedMemoryStore;
import org.wwz.ai.domain.agent.memory.ltm.LtmAgentForkSupport;
import org.wwz.ai.domain.agent.memory.ltm.LtmForkExecutionEvent;
import org.wwz.ai.domain.agent.memory.ltm.LtmForkParity;
import org.wwz.ai.domain.agent.memory.ltm.LtmForkRunResult;
import org.wwz.ai.domain.agent.memory.ltm.LtmOwner;
import org.wwz.ai.domain.agent.memory.ltm.LtmOwnerResolver;
import org.wwz.ai.domain.agent.memory.ltm.MemoryFlushPolicy;
import org.wwz.ai.domain.agent.memory.ltm.MemoryFlushService;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.infrastructure.dao.reactor.ILtmForkExecutionDao;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 压缩前 flush：父 system/tools 对齐 + 全量窗口重放（不改写早期 content）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryFlushServiceImpl implements MemoryFlushService {

    private final ObjectProvider<ReactorRuntimeDependencies> runtimeDependenciesProvider;
    private final ObjectProvider<CuratedMemoryStore> curatedMemoryStoreProvider;
    private final ObjectProvider<IExecutionLedgerReadRepository> ledgerReadRepositoryProvider;
    private final ObjectProvider<ILtmForkExecutionDao> ltmForkExecutionDaoProvider;

    private final Set<String> flushedRequests = ConcurrentHashMap.newKeySet();

    private volatile boolean enabled = true;
    private volatile int flushMinTurns = 6;
    private volatile int materialMaxMessages = 80;
    private volatile long timeoutSeconds = 45L;
    private volatile int maxSteps = 5;

    public void configure(boolean enabled, int flushMinTurns, int materialMaxMessages,
                          int materialMaxCharsPerMsg, long timeoutSeconds) {
        this.enabled = enabled;
        this.flushMinTurns = flushMinTurns;
        this.materialMaxMessages = materialMaxMessages;
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 45L;
    }

    public void configureMaxSteps(int maxSteps) {
        this.maxSteps = Math.max(1, maxSteps);
    }

    @Override
    public int flushBeforeCompact(String sessionId,
                                  String requestId,
                                  LtmOwner owner,
                                  List<Message> messagesAboutToCompact,
                                  String parentSystemPrompt,
                                  ToolCollection parentTools) {
        if (!enabled || messagesAboutToCompact == null || messagesAboutToCompact.isEmpty()) {
            recordSkip(sessionId, requestId, owner, messagesAboutToCompact,
                    !enabled ? "disabled" : "empty-messages", 0);
            return 0;
        }
        if (StringUtils.isNotBlank(requestId)
                && (requestId.contains("-flush") || requestId.contains("-bg-review"))) {
            recordSkip(sessionId, requestId, owner, messagesAboutToCompact, "nested-fork", 0);
            return 0;
        }
        LtmOwner resolvedOwner = owner != null ? owner : resolveOwner(sessionId);
        if (resolvedOwner == null) {
            recordSkip(sessionId, requestId, null, messagesAboutToCompact, "null-owner", 0);
            return 0;
        }
        String sessionKey = "s:" + StringUtils.defaultIfBlank(sessionId, "unknown");
        String reqKey = StringUtils.defaultIfBlank(requestId, sessionId);
        if (StringUtils.isNotBlank(reqKey) && !flushedRequests.add(reqKey)) {
            recordSkip(sessionId, requestId, resolvedOwner, messagesAboutToCompact, "duplicate-request", 0);
            return 0;
        }
        if (!flushedRequests.add(sessionKey)) {
            recordSkip(sessionId, requestId, resolvedOwner, messagesAboutToCompact, "session-in-flight", 0);
            return 0;
        }
        try {
            int userTurns = MemoryFlushPolicy.countUserTurns(messagesAboutToCompact);
            if (!MemoryFlushPolicy.shouldFlush(userTurns, flushMinTurns, true)) {
                recordSkip(sessionId, requestId, resolvedOwner, messagesAboutToCompact, "min-turns", userTurns);
                return 0;
            }
            CuratedMemoryStore store = curatedMemoryStoreProvider.getIfAvailable();
            ReactorRuntimeDependencies deps = runtimeDependenciesProvider.getIfAvailable();
            if (store == null || deps == null) {
                recordSkip(sessionId, requestId, resolvedOwner, messagesAboutToCompact,
                        store == null ? "store-null" : "deps-null", userTurns);
                return 0;
            }

            // 同模型路径：保留完整窗口前缀；仅在极端超长时裁掉最早消息（不改写 content）
            List<Message> snapshot = trimOldestIfNeeded(messagesAboutToCompact, materialMaxMessages);
            LtmForkParity parity = LtmForkParity.forFlush(parentSystemPrompt, parentTools, snapshot);
            log.info("memory-flush fork start sessionId={} userTurns={} snapshotMsgs={} paritySystem={} parityTools={}",
                    sessionId, userTurns, snapshot.size(), parity.hasSystemPrompt(), parity.hasParentTools());
            LtmForkRunResult result = LtmAgentForkSupport.runParityFork(
                    deps,
                    store,
                    resolvedOwner,
                    sessionId,
                    requestId,
                    parity,
                    LtmAgentForkSupport.FLUSH_DIRECTIVE,
                    maxSteps,
                    timeoutSeconds,
                    "flush");
            log.info("memory-flush fork done sessionId={} status={} appliedApprox={} durationMs={}",
                    sessionId, result.getStatus(), result.appliedOrZero(), result.getDurationMs());
            recordResult(sessionId, requestId, resolvedOwner, userTurns, snapshot.size(), result);
            return result.appliedOrZero();
        } finally {
            flushedRequests.remove(sessionKey);
        }
    }

    private void recordSkip(String sessionId,
                            String requestId,
                            LtmOwner owner,
                            List<Message> messages,
                            String reason,
                            int userTurns) {
        LtmForkExecutionEvent event = baseEvent(sessionId, requestId, owner)
                .forkKind(LtmForkExecutionEvent.KIND_FLUSH)
                .status(LtmForkExecutionEvent.STATUS_SKIPPED)
                .skipReason(reason)
                .userTurns(userTurns)
                .snapshotMessageCount(messages == null ? 0 : messages.size())
                .maxSteps(maxSteps)
                .timeoutSeconds(timeoutSeconds)
                .appliedCount(0)
                .build();
        persist(event);
    }

    private void recordResult(String sessionId,
                              String requestId,
                              LtmOwner owner,
                              int userTurns,
                              int snapshotMsgs,
                              LtmForkRunResult result) {
        LtmForkExecutionEvent event = baseEvent(sessionId, requestId, owner)
                .forkKind(LtmForkExecutionEvent.KIND_FLUSH)
                .forkRequestId(result.getForkRequestId())
                .status(result.getStatus())
                .skipReason(result.getSkipReason())
                .userTurns(userTurns)
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
            log.warn("record ltm fork flush event failed sessionId={}: {}",
                    event == null ? null : event.getSessionId(), e.toString());
        }
    }

    /** 只裁最早消息，不改写剩余 content，避免破坏 prompt-cache 前缀。 */
    private static List<Message> trimOldestIfNeeded(List<Message> messages, int maxMessages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        int maxMsg = Math.max(1, maxMessages);
        if (messages.size() <= maxMsg) {
            return new ArrayList<>(messages);
        }
        int from = messages.size() - maxMsg;
        return new ArrayList<>(messages.subList(from, messages.size()));
    }

    private LtmOwner resolveOwner(String sessionId) {
        try {
            IExecutionLedgerReadRepository ledger = ledgerReadRepositoryProvider.getIfAvailable();
            if (ledger != null && StringUtils.isNotBlank(sessionId)) {
                DialogueSession session = ledger.querySessionEntity(sessionId);
                if (session != null && StringUtils.isNotBlank(session.getVisitorId())) {
                    return LtmOwnerResolver.resolve(session.getVisitorId(), null);
                }
            }
        } catch (Exception e) {
            log.debug("resolveOwner failed sessionId={}: {}", sessionId, e.toString());
        }
        return LtmOwnerResolver.resolve(null, null);
    }
}
