package org.wwz.ai.domain.agent.reactor.service;

import org.wwz.ai.domain.agent.reactor.model.multi.OrderedEvent;

import java.util.List;

/**
 * Agent 消息事件服务
 */
public interface IAgentMessageEventService {

    void persistEvents(List<OrderedEvent> orderedEvents,
                       Long messageId, Long conversationId,
                       String sessionId, String requestId,
                       String finalStatus);

    String buildRenderSnapshot(List<OrderedEvent> orderedEvents,
                               String thoughtText, String multiAgentJson,
                               String tasksJson, String planJson,
                               String conclusionJson, String status);
}
