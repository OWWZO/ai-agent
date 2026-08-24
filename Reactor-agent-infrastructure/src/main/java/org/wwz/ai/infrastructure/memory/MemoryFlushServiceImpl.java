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
import org.wwz.ai.domain.agent.memory.ltm.LtmForkRunResult;
import org.wwz.ai.domain.agent.memory.ltm.LtmOwner;
import org.wwz.ai.domain.agent.memory.ltm.LtmOwnerResolver;
import org.wwz.ai.domain.agent.memory.ltm.MemoryFlushPolicy;
import org.wwz.ai.domain.agent.memory.ltm.MemoryFlushService;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.infrastructure.dao.reactor.ILtmForkExecutionDao;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 压缩前独立 fork，全量（截断后的）窗口消息 + 仅 memory 工具。
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
    private volatile int materialMaxMessages = 40;
    private volatile int materialMaxCharsPerMsg = 1200;
    private volatile long timeoutSeconds = 45L;
    private volatile int maxSteps = 5;

    public void configure(boolean enabled, int flushMinTurns, int materialMaxMessages,
                          int materialMaxCharsPerMsg, long timeoutSeconds) {
        this.enabled = enabled;
        this.flushMinTurns = flushMinTurns;
        this.materialMaxMessages = materialMaxMessages;
        this.materialMaxCharsPerMsg = materialMaxCharsPerMsg;
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 45L;
    }

    public void configureMaxSteps(int maxSteps) {
        this.maxSteps = Math.max(1, maxSteps);
    }

    @Override
    public int flushBeforeCompact(String sessionId,
                                  String requestId,
                                  LtmOwner owner,
                                  List<Message> messagesAboutToCompact) {
        if (!enabled || messagesAboutToCompact == null || messagesAboutToCompact.isEmpty()) {
            recordSkip(sessionId, requestId, owner, messagesAboutToCompact,
                    !enabled ? "disabled" : "empty-messages", 0);
            return 0;
        }
        // 防止 flush fork 自身 mid-run compact 再触发 flush（历史上会变成 -flush-flush-... 风暴）
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
        // request 级去重 + session 级 in-flight，避免并发 compact 同时拉起多个 flush
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

            // 全量窗口重放（截断单条过长 content，保留前缀结构以利 cache）
            List<Message> snapshot = truncateSnapshot(messagesAboutToCompact, materialMaxMessages, materialMaxCharsPerMsg);
            log.info("memory-flush fork start sessionId={} userTurns={} snapshotMsgs={}",
                    sessionId, userTurns, snapshot.size());
            LtmForkRunResult result = LtmAgentForkSupport.runMemoryOnlyFork(
                    deps,
                    store,
                    resolvedOwner,
                    sessionId,
                    requestId,
                    null, // 使用 React 默认 system + fork directive；无父 system 时仍可跑
                    snapshot,
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

    private static List<Message> truncateSnapshot(List<Message> messages, int maxMessages, int maxCharsPerMsg) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        int maxMsg = Math.max(1, maxMessages);
        int maxChars = Math.max(200, maxCharsPerMsg);
        int from = Math.max(0, messages.size() - maxMsg);
        List<Message> out = new ArrayList<>(messages.size() - from);
        for (int i = from; i < messages.size(); i++) {
            Message m = messages.get(i);
            if (m == null) {
                continue;
            }
            String c = m.getContent();
            if (c != null && c.length() > maxChars) {
                c = c.substring(0, maxChars) + "...";
                out.add(Message.builder()
                        .role(m.getRole())
                        .content(c)
                        .toolCallId(m.getToolCallId())
                        .toolCalls(m.getToolCalls())
                        .base64Image(m.getBase64Image())
                        .build());
            } else {
                out.add(m);
            }
        }
        return out;
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
