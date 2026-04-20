package org.wwz.ai.domain.agent.reactor.service.support;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import lombok.extern.slf4j.Slf4j;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.entity.AgentConversation;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessage;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessageEvent;
import org.wwz.ai.domain.agent.reactor.entity.AgentSessionMemory;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionMemoryFact;
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
    private SessionMemorySummaryBuilder summaryBuilder;
    @Resource
    private SessionMemorySummaryGenerator summaryGenerator;
    @Resource
    private SessionTranscriptBlockAssembler transcriptBlockAssembler;
    @Resource
    private SessionMemoryTokenEstimator tokenEstimator;

    public CompactionResult compact(AgentConversation conversation,
                                    AgentSessionMemory snapshot,
                                    List<AgentMessage> completedMessages,
                                    Map<Long, List<AgentMessageEvent>> eventMap) throws Exception {
        if (conversation == null || CollectionUtils.isEmpty(completedMessages)) {
            log.debug("跳过会话压缩：会话为空或无已完成消息");
            return null;
        }

        int existingBoundary = snapshot != null && snapshot.getBoundarySortOrder() != null
                ? snapshot.getBoundarySortOrder()
                : -1;
        List<AgentMessage> eligibleMessages = completedMessages.stream()
                .filter(message -> message.getSortOrder() != null && message.getSortOrder() > existingBoundary)
                .sorted(Comparator.comparing(AgentMessage::getSortOrder))
                .collect(Collectors.toList());
        int minWindowSize = resolveRecentWindowMinMessages();
        if (eligibleMessages.size() <= minWindowSize) {
            log.debug("跳过会话压缩 sessionId={}, eligibleTurns={}, keepWindow={}",
                    conversation.getSessionId(),
                    eligibleMessages.size(),
                    minWindowSize);
            return null;
        }

        List<SessionTurnMemory> eligibleTurns = toTurnMemories(eligibleMessages, eventMap);
        PreservedWindowSelection preservedWindow = selectPreservedTurns(eligibleTurns);
        if (eligibleTurns.size() <= preservedWindow.getTurns().size()) {
            log.debug("跳过会话压缩 sessionId={}，无可归档轮次", conversation.getSessionId());
            return null;
        }

        List<SessionTurnMemory> turnsToCompact = new ArrayList<>(
                eligibleTurns.subList(0, eligibleTurns.size() - preservedWindow.getTurns().size()));
        Set<Long> compactedMessageIds = turnsToCompact.stream()
                .map(SessionTurnMemory::getMessageId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<SessionMemoryFact> existingFacts = parseFacts(snapshot == null ? null : snapshot.getFactsJson());
        List<JSONObject> artifactRefs = buildCompactedArtifactRefs(snapshot, completedMessages, eventMap, compactedMessageIds);

        SessionTurnMemory boundaryTurn = turnsToCompact.get(turnsToCompact.size() - 1);
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
        List<SessionMemoryFact> facts = summaryBuilder.buildFacts(existingFacts, turnsToCompact);

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
                .factsJson(JSON.toJSONString(facts))
                .artifactRefsJson(artifactRestoreSupport.toArtifactRefsJson(artifactRefs))
                .boundaryMessageId(boundaryTurn.getMessageId())
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

    private List<SessionMemoryFact> parseFacts(String factsJson) {
        if (!StringUtils.hasText(factsJson)) {
            return List.of();
        }
        try {
            return JSON.parseObject(factsJson, new TypeReference<List<SessionMemoryFact>>() {
            });
        } catch (Exception e) {
            log.warn("解析压缩快照 facts 失败，退化为空列表 factsJson={}", factsJson, e);
            return List.of();
        }
    }

    private List<SessionTurnMemory> toTurnMemories(List<AgentMessage> messages,
                                                   Map<Long, List<AgentMessageEvent>> eventMap) {
        List<SessionTurnMemory> turns = new ArrayList<>();
        for (AgentMessage message : messages) {
            if (message == null) {
                continue;
            }
            try {
                turns.add(transcriptBlockAssembler.buildTurnMemory(
                        message,
                        eventMap == null ? null : eventMap.get(message.getId())));
            } catch (Exception e) {
                log.warn("构造压缩 transcript turn 失败，退化为 query/response messageId={}, requestId={}",
                        message.getId(),
                        message.getRequestId(),
                        e);
                turns.add(SessionTurnMemory.builder()
                        .messageId(message.getId())
                        .requestId(message.getRequestId())
                        .sortOrder(message.getSortOrder())
                        .userMessage(message.getQuery())
                        .assistantMessage(message.getResponse())
                        .finalAnswer(message.getResponse())
                        .artifactRefs(new ArrayList<>(artifactRestoreSupport.extractArtifactRefs(
                                eventMap == null ? null : eventMap.get(message.getId()))))
                        .build());
            }
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
            return new ArrayList<>(artifactRestoreSupport.parseArtifactRefs(
                    artifactRestoreSupport.toArtifactRefsJson(artifactRefs)));
        }
        List<AgentMessage> compactedMessages = completedMessages.stream()
                .filter(message -> message != null && compactedMessageIds.contains(message.getId()))
                .sorted(Comparator.comparing(AgentMessage::getSortOrder))
                .collect(Collectors.toList());
        artifactRefs.addAll(artifactRestoreSupport.collectArtifactRefs(compactedMessages, eventMap));
        return new ArrayList<>(artifactRestoreSupport.parseArtifactRefs(
                artifactRestoreSupport.toArtifactRefsJson(artifactRefs)));
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
        private String factsJson;
        private String artifactRefsJson;
        private Long boundaryMessageId;
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
                    .factsJson(factsJson)
                    .artifactRefsJson(artifactRefsJson)
                    .boundaryMessageId(boundaryMessageId)
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
