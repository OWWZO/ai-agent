package org.wwz.ai.domain.agent.reactor.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.reactor.agent.enums.ConversationAgentType;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.entity.AgentConversation;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessage;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessageEvent;
import org.wwz.ai.domain.agent.reactor.entity.AgentSessionMemory;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentMessageDao;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentSessionMemoryDao;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionMemoryDecisionType;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionMemoryPreparationResult;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionWorkingMemory;
import org.wwz.ai.domain.agent.reactor.service.IAgentSessionMemoryService;
import org.wwz.ai.domain.agent.reactor.service.support.SessionMemoryCompactionService;
import org.wwz.ai.domain.agent.reactor.service.support.SessionWorkingMemoryAssembler;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话记忆服务实现
 */
@Slf4j
@Service
public class AgentSessionMemoryServiceImpl implements IAgentSessionMemoryService {

    private final Map<String, CompactionGuardrailState> guardrailStateMap = new ConcurrentHashMap<>();

    @Resource
    private ReactorConfig reactorConfig;
    @Resource
    private IAgentSessionMemoryDao sessionMemoryDao;
    @Resource
    private IAgentMessageDao messageDao;
    @Resource
    private SessionWorkingMemoryAssembler workingMemoryAssembler;
    @Resource
    private SessionMemoryCompactionService compactionService;

    @Override
    public SessionMemoryPreparationResult prepareForRequest(AgentConversation conversation) {
        //检查是否开启对话记忆
        if (!shouldHandle(conversation)) {
            SessionWorkingMemory emptyMemory = buildEmptyWorkingMemory(conversation);
            return SessionMemoryPreparationResult.builder()
                    .decisionType(SessionMemoryDecisionType.BYPASS)
                    .workingMemory(emptyMemory)
                    .estimatedTokens(emptyMemory.getEstimatedTokens())
                    .reason("session_memory_disabled_or_chat")
                    .build();
        }

        AgentSessionMemory snapshot = sessionMemoryDao.queryBySessionId(conversation.getSessionId());
        List<AgentMessage> completedMessages = messageDao.queryCompletedByConversationId(conversation.getId());
        Map<Long, List<AgentMessageEvent>> eventMap = workingMemoryAssembler.buildFactEventMap(completedMessages);
        SessionWorkingMemory candidateWorkingMemory = workingMemoryAssembler.assemble(
                conversation,
                snapshot,
                completedMessages,
                eventMap);
        int estimatedTokens = candidateWorkingMemory.getEstimatedTokens() == null
                ? 0
                : candidateWorkingMemory.getEstimatedTokens();
        if (estimatedTokens <= reactorConfig.getSessionMemoryCompactionThresholdTokens()) {
            resetGuardrailState(conversation.getSessionId());
            return SessionMemoryPreparationResult.builder()
                    .decisionType(SessionMemoryDecisionType.BYPASS)
                    .workingMemory(candidateWorkingMemory)
                    .estimatedTokens(estimatedTokens)
                    .failureCount(0)
                    .reason("below_compaction_threshold")
                    .build();
        }

        CompactionGuardrailState guardrailState = resolveGuardrailState(conversation.getSessionId());
        if (isCircuitOpen(guardrailState)) {
            if (estimatedTokens <= reactorConfig.getSessionMemoryHardLimitTokens()) {
                return SessionMemoryPreparationResult.builder()
                        .decisionType(SessionMemoryDecisionType.SKIPPED_CIRCUIT_OPEN)
                        .workingMemory(candidateWorkingMemory)
                        .estimatedTokens(estimatedTokens)
                        .failureCount(guardrailState.getConsecutiveFailures())
                        .reason("circuit_open_but_under_hard_limit")
                        .build();
            }
            return SessionMemoryPreparationResult.builder()
                    .decisionType(SessionMemoryDecisionType.REJECTED)
                    .workingMemory(candidateWorkingMemory)
                    .estimatedTokens(estimatedTokens)
                    .failureCount(guardrailState.getConsecutiveFailures())
                    .reason("circuit_open_and_over_hard_limit")
                    .rejectReason("当前会话上下文过长且最近压缩连续失败，请稍后重试或新建会话")
                    .build();
        }

        try {
            SessionMemoryCompactionService.CompactionResult compactionResult = compactionService.compact(
                    conversation,
                    snapshot,
                    completedMessages,
                    eventMap);
            if (compactionResult == null) {
                return buildFailureFallback(
                        conversation.getSessionId(),
                        candidateWorkingMemory,
                        estimatedTokens,
                        "no_compactable_turns",
                        "当前会话上下文过长，但没有足够的历史可继续压缩，请稍后重试或新建会话");
            }
            if (compactionResult.getPostCompactionTokens() != null
                    && compactionResult.getPostCompactionTokens() > reactorConfig.getSessionMemoryHardLimitTokens()) {
                return buildFailureFallback(
                        conversation.getSessionId(),
                        candidateWorkingMemory,
                        estimatedTokens,
                        "post_compaction_still_over_hard_limit",
                        "当前会话压缩后仍然超出安全上限，请稍后重试或新建会话");
            }

            AgentSessionMemory newSnapshot = compactionResult.toSnapshotEntity();
            sessionMemoryDao.insert(newSnapshot);
            resetGuardrailState(conversation.getSessionId());

            SessionWorkingMemory compactedWorkingMemory = workingMemoryAssembler.assemble(
                    conversation,
                    newSnapshot,
                    completedMessages,
                    eventMap);
            return SessionMemoryPreparationResult.builder()
                    .decisionType(SessionMemoryDecisionType.COMPACTED)
                    .workingMemory(compactedWorkingMemory)
                    .snapshotVersionId(newSnapshot.getId())
                    .estimatedTokens(estimatedTokens)
                    .postCompactionTokens(compactedWorkingMemory.getEstimatedTokens())
                    .failureCount(0)
                    .reason("compaction_succeeded")
                    .build();
        } catch (Exception e) {
            log.warn("请求前压缩失败 sessionId={}, estimatedTokens={}",
                    conversation.getSessionId(),
                    estimatedTokens,
                    e);
            return buildFailureFallback(
                    conversation.getSessionId(),
                    candidateWorkingMemory,
                    estimatedTokens,
                    "compaction_exception",
                    "当前会话上下文过长且压缩失败，请稍后重试或新建会话");
        }
    }

    @Override
    public SessionWorkingMemory rebuildWorkingMemory(AgentConversation conversation) {
        if (!shouldHandle(conversation)) {
            return buildEmptyWorkingMemory(conversation);
        }
        return workingMemoryAssembler.assemble(conversation);
    }

    private SessionMemoryPreparationResult buildFailureFallback(String sessionId,
                                                               SessionWorkingMemory candidateWorkingMemory,
                                                               int estimatedTokens,
                                                               String reason,
                                                               String rejectReason) {
        int failureCount = incrementFailureCount(sessionId);
        if (estimatedTokens <= reactorConfig.getSessionMemoryHardLimitTokens()) {
            return SessionMemoryPreparationResult.builder()
                    .decisionType(SessionMemoryDecisionType.DEGRADED_CONTINUE)
                    .workingMemory(candidateWorkingMemory)
                    .estimatedTokens(estimatedTokens)
                    .failureCount(failureCount)
                    .reason(reason)
                    .build();
        }
        return SessionMemoryPreparationResult.builder()
                .decisionType(SessionMemoryDecisionType.REJECTED)
                .workingMemory(candidateWorkingMemory)
                .estimatedTokens(estimatedTokens)
                .failureCount(failureCount)
                .reason(reason)
                .rejectReason(rejectReason)
                .build();
    }

    private SessionWorkingMemory buildEmptyWorkingMemory(AgentConversation conversation) {
        return SessionWorkingMemory.builder()
                .conversationId(conversation == null ? null : conversation.getId())
                .sessionId(conversation == null ? null : conversation.getSessionId())
                .agentType(conversation == null ? null : conversation.getAgentType())
                .historyDialogue("")
                .estimatedTokens(0)
                .needsCompaction(false)
                .build();
    }

    private int incrementFailureCount(String sessionId) {
        if (sessionId == null) {
            return 1;
        }
        CompactionGuardrailState state = guardrailStateMap.computeIfAbsent(
                sessionId,
                key -> new CompactionGuardrailState());
        state.setConsecutiveFailures(state.getConsecutiveFailures() + 1);
        state.setLastFailureAt(LocalDateTime.now());
        return state.getConsecutiveFailures();
    }

    private void resetGuardrailState(String sessionId) {
        if (sessionId == null) {
            return;
        }
        guardrailStateMap.remove(sessionId);
    }

    private CompactionGuardrailState resolveGuardrailState(String sessionId) {
        if (sessionId == null) {
            return new CompactionGuardrailState();
        }
        CompactionGuardrailState state = guardrailStateMap.get(sessionId);
        if (state == null || !isCircuitWindowActive(state)) {
            guardrailStateMap.remove(sessionId);
            return new CompactionGuardrailState();
        }
        return state;
    }

    private boolean isCircuitOpen(CompactionGuardrailState state) {
        return state.getConsecutiveFailures() >= reactorConfig.getSessionMemoryMaxConsecutiveFailures()
                && isCircuitWindowActive(state);
    }

    private boolean isCircuitWindowActive(CompactionGuardrailState state) {
        if (state == null || state.getLastFailureAt() == null) {
            return false;
        }
        return state.getLastFailureAt()
                .plusSeconds(reactorConfig.getSessionMemoryCircuitOpenSeconds())
                .isAfter(LocalDateTime.now());
    }

    private boolean shouldHandle(AgentConversation conversation) {
        return reactorConfig.getSessionMemoryEnabled()
                && conversation != null
                && conversation.getAgentType() != null
                && conversation.getAgentType() != ConversationAgentType.CHAT.getCode();
    }

    @lombok.Data
    private static class CompactionGuardrailState {
        private int consecutiveFailures;
        private LocalDateTime lastFailureAt;
    }
}
