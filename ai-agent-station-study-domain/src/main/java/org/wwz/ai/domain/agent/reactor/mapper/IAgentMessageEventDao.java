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

    /**
     * 查询单个 turn 的完整事实块，供排查或单轮回放使用。
     */
    List<AgentMessageEvent> queryByMessageId(@Param("messageId") Long messageId);

    /**
     * 批量查询指定 turn 的完整事实块，按 message_id + seq_no 顺序返回，供历史详情回放使用。
     */
    List<AgentMessageEvent> queryByMessageIds(@Param("messageIds") List<Long> messageIds);

    /**
     * 批量查询携带 artifact 引用的事实块，供生成文件/稳定资源恢复使用。
     */
    List<AgentMessageEvent> queryArtifactEventsByMessageIds(@Param("messageIds") List<Long> messageIds);

    /**
     * 批量查询完整事实块，供 transcript working memory / session memory 预检复用。
     */
    List<AgentMessageEvent> queryFinalEventsByMessageIds(@Param("messageIds") List<Long> messageIds);

    int deleteByMessageId(@Param("messageId") Long messageId);
}
