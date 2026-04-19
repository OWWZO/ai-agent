package org.wwz.ai.domain.agent.reactor.service.support;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.entity.AgentConversation;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessage;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessageEvent;
import org.wwz.ai.domain.agent.reactor.entity.AgentSessionMemory;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentMessageDao;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentMessageEventDao;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentSessionMemoryDao;
import org.wwz.ai.domain.agent.reactor.model.dto.FileInformation;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionMemoryFact;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionTurnMemory;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionWorkingMemory;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 基于数据库账本重建请求级工作记忆
 */
@Slf4j
@Component
public class SessionWorkingMemoryAssembler {

    @Resource
    private ReactorConfig reactorConfig;
    @Resource
    private IAgentSessionMemoryDao sessionMemoryDao;
    @Resource
    private IAgentMessageDao messageDao;
    @Resource
    private IAgentMessageEventDao messageEventDao;
    @Resource
    private SessionMemoryPromptFormatter promptFormatter;
    @Resource
    private SessionArtifactRestoreSupport artifactRestoreSupport;

    public SessionWorkingMemory assemble(AgentConversation conversation) {
        SessionWorkingMemory workingMemory = SessionWorkingMemory.builder()
                .conversationId(conversation.getId())
                .sessionId(conversation.getSessionId())
                .agentType(conversation.getAgentType())
                .facts(new ArrayList<>())
                .recentTurns(new ArrayList<>())
                .restoredFiles(new ArrayList<>())
                .boundarySortOrder(-1)
                .estimatedTokens(0)
                .needsCompaction(false)
                .build();

        AgentSessionMemory snapshot = sessionMemoryDao.queryBySessionId(conversation.getSessionId());
        int boundarySortOrder = snapshot != null && snapshot.getBoundarySortOrder() != null
                ? snapshot.getBoundarySortOrder()
                : -1;
        workingMemory.setBoundarySortOrder(boundarySortOrder);
        if (snapshot != null) {
            workingMemory.setSummaryText(snapshot.getSummaryText());
            workingMemory.setFacts(parseFacts(snapshot.getFactsJson()));
        }

        List<AgentMessage> recentMessages = messageDao.queryCompletedAfterSortOrder(
                conversation.getId(),
                boundarySortOrder,
                reactorConfig.getSessionMemoryRecentWindowTurns());
        Map<Long, List<AgentMessageEvent>> eventMap = loadEventMap(recentMessages);
        workingMemory.setRecentTurns(buildRecentTurns(recentMessages, eventMap));

        List<FileInformation> restoredFiles = artifactRestoreSupport.restoreFiles(
                snapshot == null ? null : snapshot.getArtifactRefsJson(),
                recentMessages,
                eventMap);
        workingMemory.setRestoredFiles(restoredFiles);
        workingMemory.setHistoryDialogue(promptFormatter.format(workingMemory));

        int estimatedTokens = estimateWorkingMemoryTokens(workingMemory);
        workingMemory.setEstimatedTokens(estimatedTokens);
        workingMemory.setNeedsCompaction(estimatedTokens > reactorConfig.getSessionMemoryCompactionThresholdTokens());
        log.info("重建会话工作记忆 sessionId={}, snapshotBoundary={}, recentTurns={}, restoredFiles={}, estimatedTokens={}",
                conversation.getSessionId(),
                boundarySortOrder,
                workingMemory.getRecentTurns().size(),
                workingMemory.getRestoredFiles().size(),
                estimatedTokens);
        return workingMemory;
    }

    private Map<Long, List<AgentMessageEvent>> loadEventMap(List<AgentMessage> recentMessages) {
        if (CollectionUtils.isEmpty(recentMessages)) {
            return Map.of();
        }

        List<Long> messageIds = recentMessages.stream()
                .map(AgentMessage::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (messageIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<AgentMessageEvent>> eventMap = new LinkedHashMap<>();
        for (AgentMessageEvent event : messageEventDao.queryArtifactEventsByMessageIds(messageIds)) {
            eventMap.computeIfAbsent(event.getMessageId(), key -> new ArrayList<>()).add(event);
        }
        for (List<AgentMessageEvent> events : eventMap.values()) {
            events.sort(Comparator.comparing(AgentMessageEvent::getSeqNo));
        }
        return eventMap;
    }

    private List<SessionTurnMemory> buildRecentTurns(List<AgentMessage> recentMessages,
                                                     Map<Long, List<AgentMessageEvent>> eventMap) {
        if (CollectionUtils.isEmpty(recentMessages)) {
            return List.of();
        }

        List<SessionTurnMemory> turnMemories = new ArrayList<>(recentMessages.size());
        for (AgentMessage message : recentMessages) {
            if (!StringUtils.hasText(message.getResponse())) {
                continue;
            }
            turnMemories.add(SessionTurnMemory.builder()
                    .messageId(message.getId())
                    .requestId(message.getRequestId())
                    .sortOrder(message.getSortOrder())
                    .userMessage(message.getQuery())
                    .assistantMessage(message.getResponse())
                    .artifactRefs(new ArrayList<>(artifactRestoreSupport.extractArtifactRefs(eventMap.get(message.getId()))))
                    .build());
        }
        return turnMemories;
    }

    private List<SessionMemoryFact> parseFacts(String factsJson) {
        if (!StringUtils.hasText(factsJson)) {
            return List.of();
        }
        try {
            return JSON.parseObject(factsJson, new TypeReference<List<SessionMemoryFact>>() {
            });
        } catch (Exception e) {
            log.warn("解析会话记忆 facts 失败，退化为空列表 factsJson={}", factsJson, e);
            return List.of();
        }
    }

    private int estimateWorkingMemoryTokens(SessionWorkingMemory workingMemory) {
        int textLength = 0;
        if (StringUtils.hasText(workingMemory.getSummaryText())) {
            textLength += workingMemory.getSummaryText().length();
        }
        if (StringUtils.hasText(workingMemory.getHistoryDialogue())) {
            textLength += workingMemory.getHistoryDialogue().length();
        }
        if (!CollectionUtils.isEmpty(workingMemory.getRecentTurns())) {
            for (SessionTurnMemory turn : workingMemory.getRecentTurns()) {
                textLength += safeLength(turn.getUserMessage());
                textLength += safeLength(turn.getAssistantMessage());
            }
        }
        return textLength / 3;
    }

    private int safeLength(String text) {
        return text == null ? 0 : text.length();
    }
}
