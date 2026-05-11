package org.wwz.ai.application.agent.visitor;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.ledger.IExecutionLedgerReadRepository;
import org.wwz.ai.domain.agent.ledger.IExecutionLedgerWriteRepository;
import org.wwz.ai.domain.agent.ledger.entity.DialogueSession;
import org.wwz.ai.domain.agent.ledger.model.DialogueSessionUpsertRecord;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;

import java.time.LocalDateTime;

/**
 * 会话归属应用服务。
 */
@Service
@RequiredArgsConstructor
public class ConversationSessionOwnershipApplicationService {

    private final IExecutionLedgerReadRepository executionLedgerReadRepository;
    private final IExecutionLedgerWriteRepository executionLedgerWriteRepository;

    /**
     * 首次访问时绑定 session 归属，已有归属时校验是否仍属于当前访客。
     */
    public DialogueSession ensureSessionAccessible(String visitorId, String sessionId, String queryText) {
        return ensureSessionAccessible(visitorId, sessionId, queryText, true);
    }

    /**
     * 只校验既有会话的归属，不允许因为探测或详情请求自动创建空会话。
     */
    public DialogueSession ensureExistingSessionAccessible(String visitorId, String sessionId) {
        return ensureSessionAccessible(visitorId, sessionId, null, false);
    }

    private DialogueSession ensureSessionAccessible(String visitorId,
                                                   String sessionId,
                                                   String queryText,
                                                   boolean allowBindWhenMissing) {
        if (StringUtils.isAnyBlank(visitorId, sessionId)) {
            throw new IllegalArgumentException("visitorId 和 sessionId 不能为空");
        }
        DialogueSession existing = executionLedgerReadRepository.querySessionEntity(sessionId);
        if (existing == null) {
            if (!allowBindWhenMissing) {
                throw new SessionOwnershipDeniedException("当前会话不存在");
            }
            LocalDateTime now = LocalDateTime.now();
            executionLedgerWriteRepository.upsertSession(DialogueSessionUpsertRecord.builder()
                    .sessionId(sessionId)
                    .visitorId(visitorId)
                    .title(resolveSessionTitle(queryText))
                    .status(ExecutionLedgerConstants.STATUS_RUNNING)
                    .runCount(0)
                    .finishedRunCount(0)
                    .failedRunCount(0)
                    .startedAt(now)
                    .lastActiveAt(now)
                    .build());
            return executionLedgerWriteRepository.querySessionBySessionId(sessionId);
        }
        if (StringUtils.isBlank(existing.getVisitorId())) {
            executionLedgerWriteRepository.upsertSession(DialogueSessionUpsertRecord.builder()
                    .sessionId(existing.getSessionId())
                    .visitorId(visitorId)
                    .title(StringUtils.defaultIfBlank(existing.getTitle(), resolveSessionTitle(queryText)))
                    .status(existing.getStatus())
                    .latestRequestId(existing.getLatestRequestId())
                    .latestQueryText(existing.getLatestQueryText())
                    .latestSummaryText(existing.getLatestSummaryText())
                    .runCount(existing.getRunCount())
                    .finishedRunCount(existing.getFinishedRunCount())
                    .failedRunCount(existing.getFailedRunCount())
                    .startedAt(existing.getStartedAt())
                    .lastActiveAt(existing.getLastActiveAt())
                    .build());
            return executionLedgerWriteRepository.querySessionBySessionId(sessionId);
        }
        if (!StringUtils.equals(existing.getVisitorId(), visitorId)) {
            throw new SessionOwnershipDeniedException("当前访客无权访问该会话");
        }
        return existing;
    }

    /**
     * 对话标题与账本 recorder 保持同一套收口逻辑，避免首次绑定与首次 run 的标题规则漂移。
     */
    private String resolveSessionTitle(String queryText) {
        String normalized = StringUtils.trimToEmpty(queryText);
        if (normalized.isEmpty()) {
            return "新对话";
        }
        return normalized.length() <= 30 ? normalized : normalized.substring(0, 30);
    }
}
