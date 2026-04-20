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
    @Resource
    private SessionTranscriptBlockAssembler transcriptBlockAssembler;
    @Resource
    private SessionMemoryTokenEstimator tokenEstimator;

    public SessionWorkingMemory assemble(AgentConversation conversation) {
        AgentSessionMemory snapshot = sessionMemoryDao.queryBySessionId(conversation.getSessionId());
        List<AgentMessage> completedMessages = messageDao.queryCompletedByConversationId(conversation.getId());
        Map<Long, List<AgentMessageEvent>> eventMap = loadEventMap(completedMessages);
        return assemble(conversation, snapshot, completedMessages, eventMap);
    }

    public SessionWorkingMemory assemble(AgentConversation conversation,
                                         AgentSessionMemory snapshot,
                                         List<AgentMessage> completedMessages,
                                         Map<Long, List<AgentMessageEvent>> eventMap) {
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

        int boundarySortOrder = snapshot != null && snapshot.getBoundarySortOrder() != null
                ? snapshot.getBoundarySortOrder()
                : -1;
        workingMemory.setBoundarySortOrder(boundarySortOrder);
        if (snapshot != null) {
            workingMemory.setSummaryText(snapshot.getSummaryText());
            workingMemory.setFacts(parseFacts(snapshot.getFactsJson()));
        }

        List<AgentMessage> recentMessages = filterRecentMessages(completedMessages, boundarySortOrder);
        workingMemory.setRecentTurns(buildRecentTurns(recentMessages, eventMap));

        List<FileInformation> restoredFiles = artifactRestoreSupport.restoreFiles(
                snapshot == null ? null : snapshot.getArtifactRefsJson(),
                recentMessages,
                eventMap);
        workingMemory.setRestoredFiles(restoredFiles);
        workingMemory.setHistoryDialogue(promptFormatter.format(workingMemory));

        int estimatedTokens = tokenEstimator.estimateWorkingMemoryTokens(workingMemory);
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

    private List<AgentMessage> filterRecentMessages(List<AgentMessage> completedMessages, int boundarySortOrder) {
        if (CollectionUtils.isEmpty(completedMessages)) {
            return List.of();
        }
        return completedMessages.stream()
                .filter(message -> message != null
                        && message.getSortOrder() != null
                        && message.getSortOrder() > boundarySortOrder)
                .sorted(Comparator.comparing(AgentMessage::getSortOrder))
                .collect(Collectors.toList());
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
        for (AgentMessageEvent event : messageEventDao.queryFinalEventsByMessageIds(messageIds)) {
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
            try {
                turnMemories.add(transcriptBlockAssembler.buildTurnMemory(
                        message,
                        eventMap.get(message.getId())));
            } catch (Exception e) {
                // 单轮 transcript 恢复失败时退化为旧版 query/response，避免整次续聊失忆。
                log.warn("组装最近窗口 turn 失败，退化为 query/response messageId={}, requestId={}",
                        message == null ? null : message.getId(),
                        message == null ? null : message.getRequestId(),
                        e);
                turnMemories.add(SessionTurnMemory.builder()
                        .messageId(message == null ? null : message.getId())
                        .requestId(message == null ? null : message.getRequestId())
                        .sortOrder(message == null ? null : message.getSortOrder())
                        .userMessage(message == null ? null : message.getQuery())
                        .assistantMessage(message == null ? null : message.getResponse())
                        .finalAnswer(message == null ? null : message.getResponse())
                        .build());
            }
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
}
