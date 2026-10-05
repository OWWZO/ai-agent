package org.wwz.ai.application.agent.authorization;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.ledger.IExecutionLedgerReadRepository;
import org.wwz.ai.domain.agent.ledger.IExecutionLedgerWriteRepository;
import org.wwz.ai.domain.agent.ledger.entity.DialogueSession;
import org.wwz.ai.domain.agent.ledger.model.DialogueSessionUpsertRecord;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.memory.ltm.LtmManager;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话访问授权应用服务。
 */
@Service
@RequiredArgsConstructor
public class ConversationSessionAuthorizationService {

    private final IExecutionLedgerReadRepository executionLedgerReadRepository;
    private final IExecutionLedgerWriteRepository executionLedgerWriteRepository;
    private final ObjectProvider<LtmManager> ltmManagerProvider;

    /**
     * 首次访问时绑定 session 归属，已有归属时校验是否仍属于当前用户。
     */
    public DialogueSession ensureSessionAccessible(String userId, String sessionId, String queryText) {
        return ensureSessionAccessible(userId, sessionId, queryText, true);
    }

    /**
     * 只校验既有会话的归属，不允许因为探测或详情请求自动创建空会话。
     */
    public DialogueSession ensureExistingSessionAccessible(String userId, String sessionId) {
        if (StringUtils.isAnyBlank(userId, sessionId)) {
            throw new IllegalArgumentException("userId 和 sessionId 不能为空");
        }
        DialogueSession existing = executionLedgerReadRepository.querySessionOwnership(sessionId);
        if (existing == null) {
            throw new SessionOwnershipDeniedException("当前会话不存在");
        }
        if (StringUtils.isBlank(existing.getUserId())) {
            DialogueSession fullSession = executionLedgerReadRepository.querySessionEntity(sessionId);
            if (fullSession == null) {
                throw new SessionOwnershipDeniedException("当前会话不存在");
            }
            executionLedgerWriteRepository.upsertSession(DialogueSessionUpsertRecord.builder()
                    .sessionId(fullSession.getSessionId())
                    .userId(userId)
                    .title(fullSession.getTitle())
                    .status(fullSession.getStatus())
                    .latestRequestId(fullSession.getLatestRequestId())
                    .latestQueryText(fullSession.getLatestQueryText())
                    .latestSummaryText(fullSession.getLatestSummaryText())
                    .runCount(fullSession.getRunCount())
                    .finishedRunCount(fullSession.getFinishedRunCount())
                    .failedRunCount(fullSession.getFailedRunCount())
                    .startedAt(fullSession.getStartedAt())
                    .lastActiveAt(fullSession.getLastActiveAt())
                    .build());
            return executionLedgerWriteRepository.querySessionBySessionId(sessionId);
        }
        if (!StringUtils.equals(existing.getUserId(), userId)) {
            throw new SessionOwnershipDeniedException("当前用户无权访问该会话");
        }
        return existing;
    }

    private DialogueSession ensureSessionAccessible(String userId,
                                                   String sessionId,
                                                   String queryText,
                                                   boolean allowBindWhenMissing) {
        if (StringUtils.isAnyBlank(userId, sessionId)) {
            throw new IllegalArgumentException("userId 和 sessionId 不能为空");
        }
        DialogueSession existing = executionLedgerReadRepository.querySessionEntity(sessionId);
        if (existing == null) {
            if (!allowBindWhenMissing) {
                throw new SessionOwnershipDeniedException("当前会话不存在");
            }
            LocalDateTime now = LocalDateTime.now();
            executionLedgerWriteRepository.upsertSession(DialogueSessionUpsertRecord.builder()
                    .sessionId(sessionId)
                    .userId(userId)
                    .title(resolveSessionTitle(queryText))
                    .status(ExecutionLedgerConstants.STATUS_RUNNING)
                    .runCount(0)
                    .finishedRunCount(0)
                    .failedRunCount(0)
                    .startedAt(now)
                    .lastActiveAt(now)
                    .build());
            // 新会话：LTM 边界 end→switch（用户级 curated 不清空）
            notifyLtmNewSession(sessionId);
            return executionLedgerWriteRepository.querySessionBySessionId(sessionId);
        }
        if (StringUtils.isBlank(existing.getUserId())) {
            executionLedgerWriteRepository.upsertSession(DialogueSessionUpsertRecord.builder()
                    .sessionId(existing.getSessionId())
                    .userId(userId)
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
        if (!StringUtils.equals(existing.getUserId(), userId)) {
            throw new SessionOwnershipDeniedException("当前用户无权访问该会话");
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

    private void notifyLtmNewSession(String newSessionId) {
        LtmManager ltmManager = ltmManagerProvider == null ? null : ltmManagerProvider.getIfAvailable();
        if (ltmManager == null || StringUtils.isBlank(newSessionId)) {
            return;
        }
        try {
            ltmManager.commitSessionBoundaryAsync(List.of(), newSessionId, "", true);
        } catch (Exception ignored) {
            // LTM 边界失败不阻断会话创建
        }
    }
}
