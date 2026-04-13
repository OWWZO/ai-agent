package org.wwz.ai.domain.agent.reactor.service;

import org.wwz.ai.domain.agent.reactor.entity.AgentMessage;

import java.util.List;

/**
 * Agent消息服务
 */
public interface IAgentMessageService {

    /**
     * 插入占位消息(流式开始前)
     */
    AgentMessage insertPlaceholder(Long conversationId, String requestId,
                                   String query, Integer agentType, String filesJson);

    /**
     * 流式完成后更新消息全量数据
     */
    void completeMessage(Long messageId, String response, String metricsJson);

    /**
     * 标记消息为错误状态
     */
    void markError(Long messageId, String partialResponse, String metricsJson);

    /**
     * 标记消息为强制停止
     */
    void markForceStop(Long messageId, String partialResponse, String metricsJson);

    /**
     * 查询最近N轮已完成消息(滑动窗口上下文)
     */
    List<AgentMessage> getRecentCompleted(Long conversationId, int limit);

    /**
     * 获取下一个轮次序号
     */
    int getNextSortOrder(Long conversationId);
}
