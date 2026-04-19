package org.wwz.ai.domain.agent.reactor.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.wwz.ai.domain.agent.reactor.agent.enums.ConversationAgentType;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.entity.AgentConversation;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessage;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessageEvent;
import org.wwz.ai.domain.agent.reactor.entity.AgentSessionMemory;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentMessageDao;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentMessageEventDao;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentSessionMemoryDao;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionWorkingMemory;
import org.wwz.ai.domain.agent.reactor.service.IAgentSessionMemoryService;
import org.wwz.ai.domain.agent.reactor.service.support.SessionMemoryCompactionService;
import org.wwz.ai.domain.agent.reactor.service.support.SessionWorkingMemoryAssembler;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 会话记忆服务实现
 */
@Service
public class AgentSessionMemoryServiceImpl implements IAgentSessionMemoryService {

    @Resource
    private ReactorConfig reactorConfig;
    @Resource
    private IAgentSessionMemoryDao sessionMemoryDao;
    @Resource
    private IAgentMessageDao messageDao;
    @Resource
    private IAgentMessageEventDao messageEventDao;
    @Resource
    private SessionWorkingMemoryAssembler workingMemoryAssembler;
    @Resource
    private SessionMemoryCompactionService compactionService;

    @Override
    public SessionWorkingMemory rebuildWorkingMemory(AgentConversation conversation) {
        if (!shouldHandle(conversation)) {
            return SessionWorkingMemory.builder()
                    .conversationId(conversation == null ? null : conversation.getId())
                    .sessionId(conversation == null ? null : conversation.getSessionId())
                    .agentType(conversation == null ? null : conversation.getAgentType())
                    .historyDialogue("")
                    .build();
        }
        return workingMemoryAssembler.assemble(conversation);
    }

    @Override
    public void refreshSessionMemory(AgentConversation conversation) {
        if (!shouldHandle(conversation)) {
            return;
        }

        List<AgentMessage> completedMessages = messageDao.queryCompletedByConversationId(conversation.getId());
        if (CollectionUtils.isEmpty(completedMessages)) {
            return;
        }

        Map<Long, List<AgentMessageEvent>> eventMap = loadEventMap(completedMessages);
        AgentSessionMemory snapshot = sessionMemoryDao.queryBySessionId(conversation.getSessionId());
        SessionMemoryCompactionService.CompactionResult compactionResult = compactionService.compact(
                conversation,
                snapshot,
                completedMessages,
                eventMap);
        if (compactionResult == null) {
            return;
        }

        sessionMemoryDao.upsert(compactionResult.toSnapshotEntity());
    }

    private Map<Long, List<AgentMessageEvent>> loadEventMap(List<AgentMessage> completedMessages) {
        List<Long> messageIds = completedMessages.stream()
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

    private boolean shouldHandle(AgentConversation conversation) {
        return reactorConfig.getSessionMemoryEnabled()
                && conversation != null
                && conversation.getAgentType() != null
                && conversation.getAgentType() != ConversationAgentType.CHAT.getCode();
    }
}
