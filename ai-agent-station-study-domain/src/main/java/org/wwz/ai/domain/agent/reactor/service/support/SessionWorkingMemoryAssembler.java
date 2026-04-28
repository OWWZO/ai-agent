package org.wwz.ai.domain.agent.reactor.service.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.entity.AgentConversation;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessage;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessageEvent;
import org.wwz.ai.domain.agent.reactor.entity.AgentSessionMemory;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentMessageDao;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentMessageEventDao;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentSessionMemoryDao;
import org.wwz.ai.domain.agent.reactor.model.dto.FileInformation;
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
        Map<Long, List<AgentMessageEvent>> eventMap = buildFactEventMap(completedMessages);
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

        //从快照恢复边界序号（区分"历史摘要区"和"近期消息区"的分界点）
        int boundarySortOrder = snapshot != null && snapshot.getBoundarySortOrder() != null
                ? snapshot.getBoundarySortOrder()
                : -1;
        workingMemory.setBoundarySortOrder(boundarySortOrder);

        if (snapshot != null) {
            workingMemory.setSummaryText(snapshot.getSummaryText());
        }

        //筛选边界后的近期消息，构建对话轮次
        List<AgentMessage> recentMessages = filterRecentMessages(completedMessages, boundarySortOrder);
        workingMemory.setRecentTurns(buildRecentTurns(recentMessages, eventMap));

        //从快照和消息中恢复关联的文件信息  //todo：重构
        List<FileInformation> restoredFiles = artifactRestoreSupport.restoreFiles(
                snapshot == null ? null : snapshot.getArtifactRefsJson(),
                recentMessages,
                eventMap);
        workingMemory.setRestoredFiles(restoredFiles);

        // 格式化历史对话为 LLM 提示文本
        workingMemory.setHistoryDialogue(promptFormatter.format(workingMemory));
        //估算 Token 数，判断是否超出阈值需要压缩
        int estimatedTokens = tokenEstimator.estimateWorkingMemoryTokens(workingMemory);

        workingMemory.setEstimatedTokens(estimatedTokens);
        workingMemory.setNeedsCompaction(estimatedTokens > reactorConfig.getSessionMemoryCompactionThresholdTokens());

        // 记录重建日志
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

    /**
     根据消息列表，查询并构建"消息ID → 该消息的事件列表"的映射Map
     */
    public Map<Long, List<AgentMessageEvent>> buildFactEventMap(List<AgentMessage> messages) {
        if (CollectionUtils.isEmpty(messages)) {
            return Map.of();
        }

        List<Long> messageIds = messages.stream()
                .map(AgentMessage::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (messageIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<AgentMessageEvent>> eventMap = new LinkedHashMap<>();
        //用这些ID去数据库查询关联的事件数据
        for (AgentMessageEvent event : messageEventDao.queryFinalEventsByMessageIds(messageIds)) {
            eventMap.computeIfAbsent(event.getMessageId(), key -> new ArrayList<>()).add(event);
        }
        //按消息ID分组，并将每组内的事件按序号排序
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
            turnMemories.add(transcriptBlockAssembler.buildTurnMemory(
                    message,
                    eventMap.get(message.getId())));
        }
        return turnMemories;
    }
}
