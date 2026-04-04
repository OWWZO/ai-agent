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

    List<AgentMessageEvent> queryByRequestId(@Param("requestId") String requestId);

    int deleteByMessageId(@Param("messageId") Long messageId);
}
