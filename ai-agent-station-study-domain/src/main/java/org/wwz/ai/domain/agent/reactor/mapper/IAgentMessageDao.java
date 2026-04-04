package org.wwz.ai.domain.agent.reactor.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessage;

import java.util.List;

/**
 * AI Agent 消息表 DAO
 */
@Mapper
public interface IAgentMessageDao {

    int insert(AgentMessage message);

    int updateById(AgentMessage message);

    AgentMessage queryByRequestId(@Param("requestId") String requestId);

    /**
     * 按会话ID查询所有消息(按轮次正序)
     */
    List<AgentMessage> queryByConversationId(@Param("conversationId") Long conversationId);

    /**
     * 按会话ID查询最近N轮已完成的消息(按轮次倒序)
     */
    List<AgentMessage> queryRecentCompleted(@Param("conversationId") Long conversationId,
                                            @Param("limit") int limit);

    /**
     * 查询会话内当前最大轮次号
     */
    Integer queryMaxSortOrder(@Param("conversationId") Long conversationId);

    /**
     * 按会话ID软删除所有消息
     */
    int softDeleteByConversationId(@Param("conversationId") Long conversationId);
}
