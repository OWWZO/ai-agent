package org.wwz.ai.infrastructure.reactor.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.memory.SessionWorkingMemoryService;
import org.wwz.ai.domain.agent.memory.WorkingMemoryMessage;
import org.wwz.ai.domain.agent.memory.WorkingMemoryProjector;
import org.wwz.ai.domain.agent.memory.WorkingMemoryScopes;
import org.wwz.ai.domain.agent.memory.WorkingMemoryTurn;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.llm.TokenCounter;
import org.wwz.ai.infrastructure.dao.reactor.IWorkingMemoryMessageDao;
import org.wwz.ai.infrastructure.dao.reactor.IWorkingMemoryTurnDao;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 会话工作记忆的持久化适配器。
 *
 * <p>服务以 turn 为父记录、message 为子记录，将运行时消息投影到可重建的 ready
 * 工作记忆。读写键为 {@code sessionId + memoryScope}（main 与 sub:{agentId} 隔离）。
 * 普通回合采用幂等追加；压缩不会改写旧消息，而是使旧 ready 投影失效并
 * 追加 compaction turn，从而保留 append-only 历史并维护提示词缓存前缀稳定性。</p>
 *
 * <p>读取失败按 best-effort 返回空列表，避免记忆存储故障阻断主 Agent 请求；写入
 * 失败同样只记录日志，主执行链路不依赖记忆投影的成功。</p>
 */
@Slf4j
@Service
public class SessionWorkingMemoryServiceImpl implements SessionWorkingMemoryService {

    private final IWorkingMemoryTurnDao workingMemoryTurnDao;
    private final IWorkingMemoryMessageDao workingMemoryMessageDao;
    private final WorkingMemoryProjector projector = new WorkingMemoryProjector();
    private final TokenCounter tokenCounter = new TokenCounter();

    public SessionWorkingMemoryServiceImpl(IWorkingMemoryTurnDao workingMemoryTurnDao,
                                           IWorkingMemoryMessageDao workingMemoryMessageDao) {
        this.workingMemoryTurnDao = workingMemoryTurnDao;
        this.workingMemoryMessageDao = workingMemoryMessageDao;
    }

    @Override
    public List<Message> loadReadyMessages(String sessionId, String memoryScope, String currentRequestId) {
        if (StringUtils.isBlank(sessionId)) {
            return List.of();
        }
        String scope = WorkingMemoryScopes.normalize(memoryScope);
        try {
            List<WorkingMemoryTurn> turns = workingMemoryTurnDao.selectReadyBySessionIdAndScope(sessionId, scope);
            if (turns == null || turns.isEmpty()) {
                return List.of();
            }
            List<WorkingMemoryTurn> filtered = turns.stream()
                    .filter(t -> t != null && t.getId() != null)
                    .filter(t -> !StringUtils.equals(t.getRequestId(), currentRequestId))
                    .toList();
            if (filtered.isEmpty()) {
                return List.of();
            }
            List<Long> turnIds = filtered.stream().map(WorkingMemoryTurn::getId).toList();
            List<WorkingMemoryMessage> rows = workingMemoryMessageDao.selectByTurnIds(turnIds);
            if (rows == null || rows.isEmpty()) {
                return List.of();
            }
            Map<Long, List<WorkingMemoryMessage>> byTurn = rows.stream()
                    .collect(Collectors.groupingBy(WorkingMemoryMessage::getTurnId, LinkedHashMap::new, Collectors.toCollection(ArrayList::new)));

            List<Message> all = new ArrayList<>();
            for (WorkingMemoryTurn turn : filtered) {
                List<WorkingMemoryMessage> turnRows = byTurn.getOrDefault(turn.getId(), List.of());
                List<Message> msgs = projector.hydrate(turnRows);
                if (!msgs.isEmpty()) {
                    all.addAll(msgs);
                }
            }
            return all;
        } catch (Exception e) {
            log.warn("loadReadyMessages failed sessionId={} scope={}", sessionId, scope, e);
            return List.of();
        }
    }

    @Override
    public void persistTurn(String sessionId,
                            String memoryScope,
                            String requestId,
                            Long runId,
                            String entryAgent,
                            List<Message> turnMessages) {
        if (StringUtils.isBlank(sessionId) || StringUtils.isBlank(requestId) || turnMessages == null || turnMessages.isEmpty()) {
            return;
        }
        String scope = WorkingMemoryScopes.normalize(memoryScope);
        try {
            WorkingMemoryTurn existing = workingMemoryTurnDao.selectByRequestId(requestId);
            if (existing != null) {
                return; // idempotent
            }
            List<WorkingMemoryMessage> rows = projector.project(turnMessages, sessionId, scope, requestId, runId);
            if (rows.isEmpty()) {
                return;
            }
            Integer maxSeq = workingMemoryTurnDao.selectMaxTurnSeq(sessionId, scope);
            int nextSeq = (maxSeq == null ? 0 : maxSeq) + 1;
            LocalDateTime now = LocalDateTime.now();
            WorkingMemoryTurn turn = WorkingMemoryTurn.builder()
                    .sessionId(sessionId)
                    .memoryScope(scope)
                    .requestId(requestId)
                    .runId(runId)
                    .turnSeq(nextSeq)
                    .entryAgent(StringUtils.defaultIfBlank(entryAgent, "react"))
                    .status(WorkingMemoryTurn.STATUS_READY)
                    .schemaVersion(1)
                    .messageCount(rows.size())
                    .tokenEstimate(estimateTokens(projector.hydrate(rows)))
                    .startedAt(now)
                    .finishedAt(now)
                    .deleted(0)
                    .build();
            workingMemoryTurnDao.insertTurn(turn);
            if (turn.getId() == null) {
                return;
            }
            for (WorkingMemoryMessage row : rows) {
                row.setTurnId(turn.getId());
                row.setMemoryScope(scope);
            }
            workingMemoryMessageDao.batchInsertMessages(rows);
        } catch (Exception e) {
            log.warn("persistTurn failed sessionId={} scope={} requestId={}", sessionId, scope, requestId, e);
        }
    }

    @Override
    public void replaceReadyProjection(String sessionId,
                                       String memoryScope,
                                       String compactRequestId,
                                       List<Message> compactedMessages) {
        if (StringUtils.isBlank(sessionId) || StringUtils.isBlank(compactRequestId)
                || compactedMessages == null || compactedMessages.isEmpty()) {
            return;
        }
        String scope = WorkingMemoryScopes.normalize(memoryScope);
        try {
            WorkingMemoryTurn existing = workingMemoryTurnDao.selectByRequestId(compactRequestId);
            if (existing != null) {
                return;
            }
            List<WorkingMemoryMessage> rows = projector.project(compactedMessages, sessionId, scope, compactRequestId, null);
            if (rows.isEmpty()) {
                return;
            }
            workingMemoryTurnDao.markReadyInvalidBySessionIdAndScope(sessionId, scope);
            Integer maxSeq = workingMemoryTurnDao.selectMaxTurnSeq(sessionId, scope);
            int nextSeq = (maxSeq == null ? 0 : maxSeq) + 1;
            LocalDateTime now = LocalDateTime.now();
            WorkingMemoryTurn turn = WorkingMemoryTurn.builder()
                    .sessionId(sessionId)
                    .memoryScope(scope)
                    .requestId(compactRequestId)
                    .runId(null)
                    .turnSeq(nextSeq)
                    .entryAgent("compaction")
                    .status(WorkingMemoryTurn.STATUS_READY)
                    .schemaVersion(1)
                    .messageCount(rows.size())
                    .tokenEstimate(estimateTokens(projector.hydrate(rows)))
                    .startedAt(now)
                    .finishedAt(now)
                    .deleted(0)
                    .build();
            workingMemoryTurnDao.insertTurn(turn);
            if (turn.getId() == null) {
                return;
            }
            for (WorkingMemoryMessage row : rows) {
                row.setTurnId(turn.getId());
                row.setMemoryScope(scope);
            }
            workingMemoryMessageDao.batchInsertMessages(rows);
            log.info("replaceReadyProjection sessionId={} scope={} compactRequestId={} messages={} tokens={}",
                    sessionId, scope, compactRequestId, rows.size(), turn.getTokenEstimate());
        } catch (Exception e) {
            log.warn("replaceReadyProjection failed sessionId={} scope={} compactRequestId={}",
                    sessionId, scope, compactRequestId, e);
        }
    }

    private int estimateTokens(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        return tokenCounter.estimateMessages(messages);
    }
}
