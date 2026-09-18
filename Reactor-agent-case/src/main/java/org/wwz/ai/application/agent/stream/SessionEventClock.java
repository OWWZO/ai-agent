package org.wwz.ai.application.agent.stream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.ledger.IExecutionLedgerReadRepository;
import org.wwz.ai.domain.agent.ledger.IExecutionLedgerWriteRepository;
import org.wwz.ai.domain.agent.ledger.entity.DialogueSession;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 会话内单调 SSE seq：内存占号（含 delta），高水位回写 session.event_seq。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionEventClock {

    private final ConcurrentHashMap<String, AtomicLong> bySession = new ConcurrentHashMap<>();
    private final IExecutionLedgerReadRepository executionLedgerReadRepository;
    private final IExecutionLedgerWriteRepository executionLedgerWriteRepository;

    public long next(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return 0L;
        }
        return counter(sessionId.trim()).incrementAndGet();
    }

    public void persist(String sessionId, long eventSeq) {
        if (StringUtils.isBlank(sessionId) || eventSeq <= 0) {
            return;
        }
        try {
            executionLedgerWriteRepository.bumpSessionEventSeq(sessionId.trim(), eventSeq);
        } catch (RuntimeException e) {
            log.warn("persist session event_seq failed sessionId={} seq={}", sessionId, eventSeq, e);
        }
    }

    private AtomicLong counter(String sessionId) {
        return bySession.computeIfAbsent(sessionId, this::loadWatermark);
    }

    private AtomicLong loadWatermark(String sessionId) {
        try {
            DialogueSession session = executionLedgerReadRepository.querySessionEntity(sessionId);
            long watermark = session == null || session.getEventSeq() == null ? 0L : session.getEventSeq();
            return new AtomicLong(Math.max(watermark, 0L));
        } catch (RuntimeException e) {
            log.warn("load session event_seq failed sessionId={}", sessionId, e);
            return new AtomicLong(0L);
        }
    }
}
