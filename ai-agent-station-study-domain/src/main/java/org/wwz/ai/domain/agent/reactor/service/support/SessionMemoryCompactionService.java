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
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    public CompactionResult compact(AgentConversation conversation,
                                    AgentSessionMemory snapshot,
                                    List<AgentMessage> completedMessages,
                                    Map<Long, List<AgentMessageEvent>> eventMap) {
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
        if (eligibleMessages.size() <= reactorConfig.getSessionMemoryRecentWindowTurns()) {
            log.debug("跳过会话压缩 sessionId={}, eligibleTurns={}, keepWindow={}",
                    conversation.getSessionId(),
                    eligibleMessages.size(),
                    reactorConfig.getSessionMemoryRecentWindowTurns());
            return null;
        }

        int totalTokens = estimateTokens(snapshot == null ? null : snapshot.getSummaryText(), eligibleMessages);
        if (totalTokens <= reactorConfig.getSessionMemoryCompactionThresholdTokens()) {
            log.debug("跳过会话压缩 sessionId={}, estimatedTokens={}, threshold={}",
                    conversation.getSessionId(),
                    totalTokens,
                    reactorConfig.getSessionMemoryCompactionThresholdTokens());
            return null;
        }

        int keepWindowSize = reactorConfig.getSessionMemoryRecentWindowTurns();
        List<AgentMessage> turnsToCompact = new ArrayList<>(
                eligibleMessages.subList(0, eligibleMessages.size() - keepWindowSize));
        if (turnsToCompact.isEmpty()) {
            log.debug("跳过会话压缩 sessionId={}，无可归档轮次", conversation.getSessionId());
            return null;
        }

        List<SessionTurnMemory> turnMemories = toTurnMemories(turnsToCompact, eventMap);
        List<SessionMemoryFact> existingFacts = parseFacts(snapshot == null ? null : snapshot.getFactsJson());
        String summaryText = summaryBuilder.buildSummary(
                snapshot == null ? null : snapshot.getSummaryText(),
                turnMemories,
                reactorConfig.getSessionMemorySummaryMaxLength());
        List<SessionMemoryFact> facts = summaryBuilder.buildFacts(existingFacts, turnMemories);

        List<JSONObject> artifactRefs = new ArrayList<>(artifactRestoreSupport.parseArtifactRefs(
                snapshot == null ? null : snapshot.getArtifactRefsJson()));
        artifactRefs.addAll(artifactRestoreSupport.collectArtifactRefs(turnsToCompact, eventMap));
        artifactRefs = new ArrayList<>(artifactRestoreSupport.parseArtifactRefs(
                artifactRestoreSupport.toArtifactRefsJson(artifactRefs)));

        AgentMessage boundaryMessage = turnsToCompact.get(turnsToCompact.size() - 1);
        int sourceTurnCount = turnsToCompact.size() + (snapshot == null || snapshot.getSourceTurnCount() == null
                ? 0
                : snapshot.getSourceTurnCount());

        CompactionResult result = CompactionResult.builder()
                .conversationId(conversation.getId())
                .sessionId(conversation.getSessionId())
                .agentType(conversation.getAgentType())
                .summaryText(summaryText)
                .factsJson(JSON.toJSONString(facts))
                .artifactRefsJson(artifactRestoreSupport.toArtifactRefsJson(artifactRefs))
                .boundaryMessageId(boundaryMessage.getId())
                .boundarySortOrder(boundaryMessage.getSortOrder())
                .sourceTurnCount(sourceTurnCount)
                .lastCompactedAt(LocalDateTime.now())
                .build();
        log.info("生成会话压缩快照 sessionId={}, boundarySortOrder={}, sourceTurnCount={}, compactedTurns={}",
                conversation.getSessionId(),
                result.getBoundarySortOrder(),
                sourceTurnCount,
                turnsToCompact.size());
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
            if (message == null || !StringUtils.hasText(message.getResponse())) {
                continue;
            }
            turns.add(SessionTurnMemory.builder()
                    .messageId(message.getId())
                    .requestId(message.getRequestId())
                    .sortOrder(message.getSortOrder())
                    .userMessage(message.getQuery())
                    .assistantMessage(message.getResponse())
                    .artifactRefs(new ArrayList<>(artifactRestoreSupport.extractArtifactRefs(
                            eventMap == null ? null : eventMap.get(message.getId()))))
                    .build());
        }
        return turns;
    }

    private int estimateTokens(String summaryText, List<AgentMessage> messages) {
        int textLength = summaryText == null ? 0 : summaryText.length();
        for (AgentMessage message : messages) {
            if (message == null) {
                continue;
            }
            textLength += safeLength(message.getQuery());
            textLength += safeLength(message.getResponse());
        }
        return textLength / 3;
    }

    private int safeLength(String text) {
        return text == null ? 0 : text.length();
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
}
