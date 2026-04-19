package org.wwz.ai.domain.agent.reactor.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessageEvent;

import java.util.List;

/**
 * AI Agent 消息事件 DAO
 */
@Mapper
public interface IAgentMessageEventDao {

    int batchInsert(@Param("events") List<AgentMessageEvent> events);

    List<AgentMessageEvent> queryByMessageId(@Param("messageId") Long messageId);

    List<AgentMessageEvent> queryByMessageIds(@Param("messageIds") List<Long> messageIds);

    /**
     * 批量查询带 artifact payload 的最终事件，供会话记忆重建使用。
     */
    List<AgentMessageEvent> queryArtifactEventsByMessageIds(@Param("messageIds") List<Long> messageIds);

    int deleteByMessageId(@Param("messageId") Long messageId);
}
