package org.wwz.ai.domain.agent.reactor.service.support;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.entity.AgentConversation;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessage;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessageEvent;
import org.wwz.ai.domain.agent.reactor.entity.AgentSessionMemory;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionTurnMemory;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 会话记忆压缩服务
 */
@Slf4j
@Component
public class SessionMemoryCompactionService {

    @Resource
    private ReactorConfig reactorConfig;
    @Resource
    private SessionArtifactRestoreSupport artifactRestoreSupport;
    @Resource
    private SessionMemorySummaryGenerator summaryGenerator;
    @Resource
    private SessionTranscriptBlockAssembler transcriptBlockAssembler;
    @Resource
    private SessionMemoryTokenEstimator tokenEstimator;

    //todo:重构
    public CompactionResult compact(AgentConversation conversation,
                                    AgentSessionMemory snapshot,
                                    List<AgentMessage> completedMessages,
                                    Map<Long, List<AgentMessageEvent>> eventMap) throws Exception {
        if (conversation == null || CollectionUtils.isEmpty(completedMessages)) {
            log.debug("跳过会话压缩：会话为空或无已完成消息");
            return null;
        }

        //确定压缩边界 取上次压缩的最大序号，-1 表示首次压缩
        int existingBoundary = snapshot != null && snapshot.getBoundarySortOrder() != null
                ? snapshot.getBoundarySortOrder()
                : -1;

        //只取边界之后的新消息，按序号排序
        List<AgentMessage> eligibleMessages = completedMessages.stream()
                .filter(message -> message.getSortOrder() != null && message.getSortOrder() > existingBoundary)
                .sorted(Comparator.comparing(AgentMessage::getSortOrder))
                .collect(Collectors.toList());

        //消息太少则不压缩，保留完整上下文
        int minWindowSize = resolveRecentWindowMinMessages();
        if (eligibleMessages.size() <= minWindowSize) {
            log.debug("跳过会话压缩 sessionId={}, eligibleTurns={}, keepWindow={}",
                    conversation.getSessionId(),
                    eligibleMessages.size(),
                    minWindowSize);
            return null;
        }

        //消息 + 事件 → 结构化轮次记忆
        List<SessionTurnMemory> eligibleTurns = toTurnMemories(eligibleMessages, eventMap);
        //决定哪些轮次保留原样（通常是最近 N 轮）
        PreservedWindowSelection preservedWindow = selectPreservedTurns(eligibleTurns);
        if (eligibleTurns.size() <= preservedWindow.getTurns().size()) {
            log.debug("跳过会话压缩 sessionId={}，无可归档轮次", conversation.getSessionId());
            return null;
        }

        //除保留窗口外的所有历史轮次
        List<SessionTurnMemory> turnsToCompact = new ArrayList<>(
                eligibleTurns.subList(0, eligibleTurns.size() - preservedWindow.getTurns().size()));

        //收集被压缩消息ID 用于过滤已归档消息
        Set<Long> compactedMessageIds = turnsToCompact.stream()
                .map(SessionTurnMemory::getMessageId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<JSONObject> artifactRefs = buildCompactedArtifactRefs(snapshot, completedMessages, eventMap, compactedMessageIds);

        SessionTurnMemory boundaryTurn = turnsToCompact.get(turnsToCompact.size() - 1);
        //调用 LLM 将多轮对话压缩为文本摘要
        String summaryText = summaryGenerator.generate(SessionMemorySummaryGenerator.GenerationRequest.builder()
                .sessionId(conversation.getSessionId())
                .requestId(resolveRequestId(turnsToCompact))
                .agentType(conversation.getAgentType())
                .existingSummary(snapshot == null ? null : snapshot.getSummaryText())
                .turnsToCompact(turnsToCompact)
                .artifactRefs(artifactRefs)
                .maxLength(reactorConfig.getSessionMemorySummaryMaxLength())
                .boundarySortOrder(boundaryTurn.getSortOrder())
                .build());

        int sourceTurnCount = turnsToCompact.size() + (snapshot == null || snapshot.getSourceTurnCount() == null
                ? 0
                : snapshot.getSourceTurnCount());
        int estimatedTokens = tokenEstimator.estimateWorkingMemoryTokens(
                snapshot == null ? null : snapshot.getSummaryText(),
                eligibleTurns,
                null);
        int postCompactionTokens = tokenEstimator.estimateWorkingMemoryTokens(
                summaryText,
                preservedWindow.getTurns(),
                null);

        CompactionResult result = CompactionResult.builder()
                .conversationId(conversation.getId())
                .sessionId(conversation.getSessionId())
                .agentType(conversation.getAgentType())
                .summaryText(summaryText)
                .artifactRefsJson(artifactRestoreSupport.toArtifactRefsJson(artifactRefs))
                .boundarySortOrder(boundaryTurn.getSortOrder())
                .sourceTurnCount(sourceTurnCount)
                .lastCompactedAt(LocalDateTime.now())
                .estimatedTokens(estimatedTokens)
                .postCompactionTokens(postCompactionTokens)
                .compactedTurnCount(turnsToCompact.size())
                .preservedTurnCount(preservedWindow.getTurns().size())
                .build();
        log.info("生成会话压缩快照 sessionId={}, boundarySortOrder={}, sourceTurnCount={}, compactedTurns={}, preservedTurns={}, estimatedTokens={}, postCompactionTokens={}",
                conversation.getSessionId(),
                result.getBoundarySortOrder(),
                sourceTurnCount,
                turnsToCompact.size(),
                preservedWindow.getTurns().size(),
                estimatedTokens,
                postCompactionTokens);
        return result;
    }

    private List<SessionTurnMemory> toTurnMemories(List<AgentMessage> messages,
                                                   Map<Long, List<AgentMessageEvent>> eventMap) {
        List<SessionTurnMemory> turns = new ArrayList<>();
        for (AgentMessage message : messages) {
            if (message == null) {
                continue;
            }
            turns.add(transcriptBlockAssembler.buildTurnMemory(
                    message,
                    eventMap == null ? null : eventMap.get(message.getId())));
        }
        return turns;
    }

    private List<JSONObject> buildCompactedArtifactRefs(AgentSessionMemory snapshot,
                                                        List<AgentMessage> completedMessages,
                                                        Map<Long, List<AgentMessageEvent>> eventMap,
                                                        Set<Long> compactedMessageIds) {
        List<JSONObject> artifactRefs = new ArrayList<>(artifactRestoreSupport.parseArtifactRefs(
                snapshot == null ? null : snapshot.getArtifactRefsJson()));
        if (CollectionUtils.isEmpty(completedMessages) || CollectionUtils.isEmpty(compactedMessageIds)) {
            return artifactRestoreSupport.deduplicateArtifactRefs(artifactRefs);
        }
        List<AgentMessage> compactedMessages = completedMessages.stream()
                .filter(message -> message != null && compactedMessageIds.contains(message.getId()))
                .sorted(Comparator.comparing(AgentMessage::getSortOrder))
                .collect(Collectors.toList());
        artifactRefs.addAll(artifactRestoreSupport.collectArtifactRefs(compactedMessages, eventMap));
        return artifactRestoreSupport.deduplicateArtifactRefs(artifactRefs);
    }

    private PreservedWindowSelection selectPreservedTurns(List<SessionTurnMemory> eligibleTurns) {
        if (CollectionUtils.isEmpty(eligibleTurns)) {
            return new PreservedWindowSelection(List.of(), 0);
        }
        int minMessages = resolveRecentWindowMinMessages();
        int maxTokens = resolveRecentWindowMaxTokens();
        List<SessionTurnMemory> preservedTurns = new ArrayList<>();
        int totalTokens = 0;

        for (int i = eligibleTurns.size() - 1; i >= 0; i--) {
            SessionTurnMemory turn = eligibleTurns.get(i);
            int turnTokens = Math.max(1, tokenEstimator.estimateTurnTokens(turn));
            boolean mustKeepForMinMessages = preservedTurns.size() < minMessages;
            if (mustKeepForMinMessages || totalTokens + turnTokens <= maxTokens) {
                preservedTurns.add(0, turn);
                totalTokens += turnTokens;
                continue;
            }
            break;
        }
        return new PreservedWindowSelection(preservedTurns, totalTokens);
    }

    private int resolveRecentWindowMinMessages() {
        if (reactorConfig.getSessionMemoryRecentWindowMinMessages() != null
                && reactorConfig.getSessionMemoryRecentWindowMinMessages() > 0) {
            return reactorConfig.getSessionMemoryRecentWindowMinMessages();
        }
        return reactorConfig.getSessionMemoryRecentWindowTurns() == null
                ? 1
                : Math.max(1, reactorConfig.getSessionMemoryRecentWindowTurns());
    }

    private int resolveRecentWindowMaxTokens() {
        if (reactorConfig.getSessionMemoryRecentWindowMaxTokens() != null
                && reactorConfig.getSessionMemoryRecentWindowMaxTokens() > 0) {
            return reactorConfig.getSessionMemoryRecentWindowMaxTokens();
        }
        return Math.max(1, reactorConfig.getSessionMemoryCompactionThresholdTokens());
    }

    private String resolveRequestId(List<SessionTurnMemory> turnsToCompact) {
        if (CollectionUtils.isEmpty(turnsToCompact)) {
            return null;
        }
        return turnsToCompact.get(turnsToCompact.size() - 1).getRequestId();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompactionResult {
        private Long conversationId;
        private String sessionId;
        private Integer agentType;
        private String summaryText;
        private String artifactRefsJson;
        private Integer boundarySortOrder;
        private Integer sourceTurnCount;
        private LocalDateTime lastCompactedAt;
        private Integer estimatedTokens;
        private Integer postCompactionTokens;
        private Integer compactedTurnCount;
        private Integer preservedTurnCount;

        public AgentSessionMemory toSnapshotEntity() {
            return AgentSessionMemory.builder()
                    .conversationId(conversationId)
                    .sessionId(sessionId)
                    .agentType(agentType)
                    .summaryText(summaryText)
                    .artifactRefsJson(artifactRefsJson)
                    .boundarySortOrder(boundarySortOrder)
                    .sourceTurnCount(sourceTurnCount)
                    .lastCompactedAt(lastCompactedAt)
                    .deleted(0)
                    .build();
        }
    }

    @Data
    @AllArgsConstructor
    private static class PreservedWindowSelection {
        private List<SessionTurnMemory> turns;
        private Integer estimatedTokens;
    }
}
