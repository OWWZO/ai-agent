package org.wwz.ai.domain.agent.reactor.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.wwz.ai.domain.agent.model.valobj.ConversationRoleVO;
import org.wwz.ai.domain.agent.service.IFixRoleService;
import org.wwz.ai.domain.agent.reactor.model.history.ConversationTurnDetail;
import org.wwz.ai.domain.agent.reactor.service.support.ConversationReplayAssembler;
import org.wwz.ai.domain.agent.reactor.service.IAgentConversationService;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentConversationDao;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentMessageEventDao;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentMessageDao;
import org.wwz.ai.domain.agent.reactor.entity.AgentConversation;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessage;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessageEvent;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AgentConversationServiceImpl implements IAgentConversationService {

    @Resource
    private IAgentConversationDao conversationDao;
    @Resource
    private IAgentMessageDao messageDao;
    @Resource
    private IAgentMessageEventDao messageEventDao;
    @Resource
    private IFixRoleService fixRoleService;
    @Resource
    private ConversationReplayAssembler replayAssembler;

    @Override
    public AgentConversation createConversation(String sessionId, String deviceId, String title,
                                                Integer agentType, String productType,
                                                String aiAgentId, String aiAgentNameSnapshot) {
        AgentConversation conversation = AgentConversation.builder()
                .sessionId(sessionId)
                .deviceId(deviceId)
                .title(title != null ? title : "新对话")
                .agentType(agentType)
                .productType(productType != null ? productType : "chat")
                .aiAgentId(aiAgentId)
                .aiAgentNameSnapshot(aiAgentNameSnapshot)
                .messageCount(0)
                .pinned(0)
                .deleted(0)
                .build();
        conversationDao.insert(conversation);
        log.info("创建会话 sessionId={}, deviceId={}, agentType={}, aiAgentId={}", sessionId, deviceId, agentType, aiAgentId);
        return conversation;
    }

    @Override
    public AgentConversation getBySessionId(String sessionId) {
        return conversationDao.queryBySessionId(sessionId);
    }

    @Override
    public AgentConversation getAccessibleConversation(String sessionId, String deviceId, Long userId) {
        AgentConversation conversation = conversationDao.queryBySessionId(sessionId);
        if (conversation == null) {
            return null;
        }
        return matchesScope(conversation, deviceId, userId) ? conversation : null;
    }

    @Override
    public void renameConversation(String sessionId, String deviceId, String newTitle) {
        AgentConversation conversation = conversationDao.queryBySessionId(sessionId);
        if (conversation == null || !matchesScope(conversation, deviceId, null)) {
            log.warn("重命名失败: 会话不存在或设备不匹配 sessionId={}", sessionId);
            return;
        }
        AgentConversation update = new AgentConversation();
        update.setId(conversation.getId());
        update.setTitle(newTitle);
        conversationDao.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteConversation(String sessionId, String deviceId) {
        AgentConversation conversation = conversationDao.queryBySessionId(sessionId);
        if (conversation == null || !matchesScope(conversation, deviceId, null)) {
            log.warn("删除失败: 会话不存在或设备不匹配 sessionId={}", sessionId);
            return;
        }
        conversationDao.softDeleteBySessionId(sessionId, deviceId);
        messageDao.softDeleteByConversationId(conversation.getId());
        log.info("软删除会话 sessionId={}", sessionId);
    }

    @Override
    public List<AgentConversation> listConversations(String deviceId, Long userId, int pageNo, int pageSize) {
        int offset = (pageNo - 1) * pageSize;
        if (userId != null) {
            return conversationDao.queryByUserIdOrDeviceId(userId, deviceId, offset, pageSize);
        }
        if (deviceId == null || deviceId.isBlank()) {
            return conversationDao.queryAll(offset, pageSize);
        }
        return conversationDao.queryByDeviceId(deviceId, offset, pageSize);
    }

    @Override
    public int countConversations(String deviceId, Long userId) {
        if (userId != null) {
            return conversationDao.countByUserIdOrDeviceId(userId, deviceId);
        }
        if (deviceId == null || deviceId.isBlank()) {
            return conversationDao.countAll();
        }
        return conversationDao.countByDeviceId(deviceId);
    }

    @Override
    public List<ConversationTurnDetail> getConversationTurns(String sessionId, String deviceId, Long userId) {
        AgentConversation conversation = getAccessibleConversation(sessionId, deviceId, userId);
        if (conversation == null) {
            return List.of();
        }
        List<AgentMessage> messages = messageDao.queryByConversationId(conversation.getId());
        return replayAssembler.assembleTurns(messages, loadEventMap(messages));
    }

    @Override
    public void togglePin(String sessionId, String deviceId, boolean pinned) {
        AgentConversation conversation = conversationDao.queryBySessionId(sessionId);
        if (conversation == null || !matchesScope(conversation, deviceId, null)) {
            return;
        }
        AgentConversation update = new AgentConversation();
        update.setId(conversation.getId());
        update.setPinned(pinned ? 1 : 0);
        conversationDao.updateById(update);
    }

    @Override
    public int migrateToUser(String deviceId, Long userId) {
        int count = conversationDao.migrateDeviceToUser(deviceId, userId);
        log.info("匿名会话迁移: deviceId={}, userId={}, count={}", deviceId, userId, count);
        return count;
    }

    @Override
    public AgentConversation bindChatRole(AgentConversation conversation, String aiAgentId, String aiAgentNameSnapshot) {
        if (conversation == null) {
            return null;
        }

        conversationDao.bindChatRole(conversation.getId(), aiAgentId, aiAgentNameSnapshot);
        conversation.setAiAgentId(aiAgentId);
        conversation.setAiAgentNameSnapshot(aiAgentNameSnapshot);
        return conversation;
    }

    @Override
    public ConversationRoleVO buildConversationRole(AgentConversation conversation) {
        return fixRoleService.buildConversationRole(conversation);
    }

    private boolean matchesScope(AgentConversation conversation, String deviceId, Long userId) {
        if (conversation == null) {
            return false;
        }
        if (userId != null && Objects.equals(userId, conversation.getUserId())) {
            return true;
        }
        return deviceId != null && deviceId.equals(conversation.getDeviceId());
    }

    /**
     * 历史详情只信任事件流；artifact 缺失态由 payloadJson 归一化后统一回放，
     * 因此这里显式按顺序加载 message 事件，避免再从 turn 账本派生任何 rich 细节。
     */
    private Map<Long, List<AgentMessageEvent>> loadEventMap(List<AgentMessage> messages) {
        Map<Long, List<AgentMessageEvent>> eventMap = new LinkedHashMap<>();
        if (messages == null || messages.isEmpty()) {
            return eventMap;
        }

        List<Long> messageIds = messages.stream()
                .map(AgentMessage::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (messageIds.isEmpty()) {
            return eventMap;
        }

        Map<Long, List<AgentMessageEvent>> groupedEvents = new HashMap<>();
        for (AgentMessageEvent event : messageEventDao.queryByMessageIds(messageIds)) {
            groupedEvents.computeIfAbsent(event.getMessageId(), key -> new ArrayList<>()).add(event);
        }

        for (AgentMessage message : messages) {
            List<AgentMessageEvent> events = new ArrayList<>(
                    groupedEvents.getOrDefault(message.getId(), List.of()));
            events.sort(Comparator.comparing(AgentMessageEvent::getSeqNo));
            eventMap.put(message.getId(), events);
        }
        return eventMap;
    }
}
