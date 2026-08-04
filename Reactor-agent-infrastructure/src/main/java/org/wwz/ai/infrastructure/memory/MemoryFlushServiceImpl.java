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
import org.wwz.ai.domain.agent.memory.ltm.LtmOwner;
import org.wwz.ai.domain.agent.memory.ltm.LtmOwnerResolver;
import org.wwz.ai.domain.agent.memory.ltm.MemoryFlushPolicy;
import org.wwz.ai.domain.agent.memory.ltm.MemoryFlushService;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.dto.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hermes 对齐：压缩前独立 fork，全量（截断后的）窗口消息 + 仅 memory 工具。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryFlushServiceImpl implements MemoryFlushService {

    private final ObjectProvider<ReactorRuntimeDependencies> runtimeDependenciesProvider;
    private final ObjectProvider<CuratedMemoryStore> curatedMemoryStoreProvider;
    private final ObjectProvider<IExecutionLedgerReadRepository> ledgerReadRepositoryProvider;

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
            return 0;
        }
        LtmOwner resolvedOwner = owner != null ? owner : resolveOwner(sessionId);
        if (resolvedOwner == null) {
            return 0;
        }
        String reqKey = StringUtils.defaultIfBlank(requestId, sessionId);
        if (StringUtils.isNotBlank(reqKey) && !flushedRequests.add(reqKey)) {
            return 0;
        }
        int userTurns = MemoryFlushPolicy.countUserTurns(messagesAboutToCompact);
        if (!MemoryFlushPolicy.shouldFlush(userTurns, flushMinTurns, true)) {
            return 0;
        }
        CuratedMemoryStore store = curatedMemoryStoreProvider.getIfAvailable();
        ReactorRuntimeDependencies deps = runtimeDependenciesProvider.getIfAvailable();
        if (store == null || deps == null) {
            return 0;
        }

        // 全量窗口重放（截断单条过长 content，保留前缀结构以利 cache）
        List<Message> snapshot = truncateSnapshot(messagesAboutToCompact, materialMaxMessages, materialMaxCharsPerMsg);
        log.info("memory-flush fork start sessionId={} userTurns={} snapshotMsgs={}",
                sessionId, userTurns, snapshot.size());
        int applied = LtmAgentForkSupport.runMemoryOnlyFork(
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
        log.info("memory-flush fork done sessionId={} appliedApprox={}", sessionId, applied);
        return applied;
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
