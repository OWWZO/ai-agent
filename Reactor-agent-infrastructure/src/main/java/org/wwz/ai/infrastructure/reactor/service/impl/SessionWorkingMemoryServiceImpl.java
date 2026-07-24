package org.wwz.ai.infrastructure.reactor.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.memory.SessionWorkingMemoryService;
import org.wwz.ai.domain.agent.memory.WorkingMemoryMessage;
import org.wwz.ai.domain.agent.memory.WorkingMemoryProjector;
import org.wwz.ai.domain.agent.memory.WorkingMemoryTurn;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.llm.TokenCounter;
import org.wwz.ai.infrastructure.dao.reactor.IWorkingMemoryMessageDao;
import org.wwz.ai.infrastructure.dao.reactor.IWorkingMemoryTurnDao;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    public List<Message> loadReadyMessages(String sessionId, String currentRequestId) {
        if (StringUtils.isBlank(sessionId)) {
            return List.of();
        }
        try {
            List<WorkingMemoryTurn> turns = workingMemoryTurnDao.selectReadyBySessionId(sessionId);
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

            // Append-only: do not drop oldest turns (prefix rewrite kills prompt cache).
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
            log.warn("loadReadyMessages failed sessionId={}", sessionId, e);
            return List.of();
        }
    }

    @Override
    public void persistTurn(String sessionId, String requestId, Long runId, String entryAgent, List<Message> turnMessages) {
        if (StringUtils.isBlank(sessionId) || StringUtils.isBlank(requestId) || turnMessages == null || turnMessages.isEmpty()) {
            return;
        }
        try {
            WorkingMemoryTurn existing = workingMemoryTurnDao.selectByRequestId(requestId);
            if (existing != null) {
                return; // idempotent
            }
            List<WorkingMemoryMessage> rows = projector.project(turnMessages, sessionId, requestId, runId);
            if (rows.isEmpty()) {
                return;
            }
            Integer maxSeq = workingMemoryTurnDao.selectMaxTurnSeq(sessionId);
            int nextSeq = (maxSeq == null ? 0 : maxSeq) + 1;
            LocalDateTime now = LocalDateTime.now();
            WorkingMemoryTurn turn = WorkingMemoryTurn.builder()
                    .sessionId(sessionId)
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
            }
            workingMemoryMessageDao.batchInsertMessages(rows);
        } catch (Exception e) {
            log.warn("persistTurn failed sessionId={} requestId={}", sessionId, requestId, e);
        }
    }

    private int estimateTokens(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        StringBuilder sb = new StringBuilder();
        for (Message message : messages) {
            if (message == null) {
                continue;
            }
            if (message.getRole() != null) {
                sb.append(message.getRole().name()).append('\n');
            }
            if (message.getContent() != null) {
                sb.append(message.getContent()).append('\n');
            }
            if (message.getToolCallId() != null) {
                sb.append(message.getToolCallId()).append('\n');
            }
            if (message.getToolCalls() != null) {
                for (ToolCall toolCall : message.getToolCalls()) {
                    if (toolCall == null || toolCall.getFunction() == null) {
                        continue;
                    }
                    sb.append(StringUtils.defaultString(toolCall.getId())).append(' ')
                            .append(StringUtils.defaultString(toolCall.getFunction().getName())).append(' ')
                            .append(StringUtils.defaultString(toolCall.getFunction().getArguments()))
                            .append('\n');
                }
            }
        }
        return tokenCounter.countText(sb.toString());
    }
}
