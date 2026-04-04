package org.wwz.ai.domain.agent.reactor.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.reactor.agent.enums.MessageStatus;
import org.wwz.ai.domain.agent.reactor.service.IAgentMessageService;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentMessageDao;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessage;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class AgentMessageServiceImpl implements IAgentMessageService {

    @Resource
    private IAgentMessageDao messageDao;

    @Override
    public AgentMessage insertPlaceholder(Long conversationId, String sessionId, String requestId,
                                          String query, Integer agentType, String filesJson) {
        int sortOrder = getNextSortOrder(conversationId);
        AgentMessage message = AgentMessage.builder()
                .conversationId(conversationId)
                .sessionId(sessionId)
                .requestId(requestId)
                .sortOrder(sortOrder)
                .query(query)
                .agentType(agentType)
                .filesJson(filesJson)
                .status(MessageStatus.STREAMING.getCode())
                .forceStop(0)
                .startedAt(LocalDateTime.now())
                .deleted(0)
                .build();
        messageDao.insert(message);
        log.info("插入占位消息 requestId={}, conversationId={}, sortOrder={}", requestId, conversationId, sortOrder);
        return message;
    }

    @Override
    public void completeMessage(Long messageId, String response, String thought,
                                String planJson, String tasksJson, String multiAgentJson,
                                String conclusionJson, String planListJson,
                                String renderSnapshotJson, String metricsJson) {
        AgentMessage update = new AgentMessage();
        update.setId(messageId);
        update.setResponse(response);
        update.setThought(thought);
        update.setPlanJson(planJson);
        update.setTasksJson(tasksJson);
        update.setMultiAgentJson(multiAgentJson);
        update.setConclusionJson(conclusionJson);
        update.setPlanListJson(planListJson);
        update.setRenderSnapshotJson(renderSnapshotJson);
        update.setMetricsJson(metricsJson);
        update.setStatus(MessageStatus.COMPLETED.getCode());
        update.setFinishedAt(LocalDateTime.now());
        messageDao.updateById(update);
        log.info("消息完成持久化 messageId={}", messageId);
    }

    @Override
    public void markError(Long messageId, String partialResponse, String partialThought,
                          String renderSnapshotJson, String metricsJson) {
        AgentMessage update = new AgentMessage();
        update.setId(messageId);
        update.setResponse(partialResponse);
        update.setThought(partialThought);
        update.setRenderSnapshotJson(renderSnapshotJson);
        update.setMetricsJson(metricsJson);
        update.setStatus(MessageStatus.ERROR.getCode());
        update.setFinishedAt(LocalDateTime.now());
        messageDao.updateById(update);
        log.warn("消息标记为错误 messageId={}", messageId);
    }

    @Override
    public void markForceStop(Long messageId, String partialResponse, String partialThought,
                              String partialTasksJson, String partialMultiAgentJson,
                              String renderSnapshotJson, String metricsJson) {
        AgentMessage update = new AgentMessage();
        update.setId(messageId);
        update.setResponse(partialResponse);
        update.setThought(partialThought);
        update.setTasksJson(partialTasksJson);
        update.setMultiAgentJson(partialMultiAgentJson);
        update.setRenderSnapshotJson(renderSnapshotJson);
        update.setMetricsJson(metricsJson);
        update.setStatus(MessageStatus.FORCE_STOPPED.getCode());
        update.setForceStop(1);
        update.setFinishedAt(LocalDateTime.now());
        messageDao.updateById(update);
        log.info("消息标记为强制停止 messageId={}", messageId);
    }

    @Override
    public List<AgentMessage> getRecentCompleted(Long conversationId, int limit) {
        return messageDao.queryRecentCompleted(conversationId, limit);
    }

    @Override
    public int getNextSortOrder(Long conversationId) {
        Integer maxOrder = messageDao.queryMaxSortOrder(conversationId);
        return (maxOrder == null ? -1 : maxOrder) + 1;
    }
}
