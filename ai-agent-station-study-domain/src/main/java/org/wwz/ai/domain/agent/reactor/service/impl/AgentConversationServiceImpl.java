package org.wwz.ai.domain.agent.reactor.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.wwz.ai.domain.agent.reactor.service.IAgentConversationService;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentConversationDao;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentMessageDao;
import org.wwz.ai.domain.agent.reactor.entity.AgentConversation;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessage;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@Service
public class AgentConversationServiceImpl implements IAgentConversationService {

    @Resource
    private IAgentConversationDao conversationDao;
    @Resource
    private IAgentMessageDao messageDao;

    @Override
    public AgentConversation createConversation(String sessionId, String deviceId, String title,
                                                 Integer agentType, String productType) {
        AgentConversation conversation = AgentConversation.builder()
                .sessionId(sessionId)
                .deviceId(deviceId)
                .title(title != null ? title : "新对话")
                .agentType(agentType)
                .productType(productType != null ? productType : "chat")
                .messageCount(0)
                .pinned(0)
                .deleted(0)
                .build();
        conversationDao.insert(conversation);
        log.info("创建会话 sessionId={}, deviceId={}, agentType={}", sessionId, deviceId, agentType);
        return conversation;
    }

    @Override
    public AgentConversation getBySessionId(String sessionId) {
        return conversationDao.queryBySessionId(sessionId);
    }

    @Override
    public void renameConversation(String sessionId, String deviceId, String newTitle) {
        AgentConversation conversation = conversationDao.queryBySessionId(sessionId);
        if (conversation == null || !conversation.getDeviceId().equals(deviceId)) {
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
        if (conversation == null || !conversation.getDeviceId().equals(deviceId)) {
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
    public List<AgentMessage> getConversationMessages(String sessionId) {
        AgentConversation conversation = conversationDao.queryBySessionId(sessionId);
        if (conversation == null) {
            return List.of();
        }
        return messageDao.queryByConversationId(conversation.getId());
    }

    @Override
    public void togglePin(String sessionId, String deviceId, boolean pinned) {
        AgentConversation conversation = conversationDao.queryBySessionId(sessionId);
        if (conversation == null || !conversation.getDeviceId().equals(deviceId)) {
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
}
