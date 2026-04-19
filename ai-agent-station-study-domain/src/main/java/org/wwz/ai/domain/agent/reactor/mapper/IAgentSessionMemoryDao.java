package org.wwz.ai.domain.agent.reactor.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.wwz.ai.domain.agent.reactor.entity.AgentSessionMemory;

/**
 * 会话记忆快照 DAO
 */
@Mapper
public interface IAgentSessionMemoryDao {

    int insert(AgentSessionMemory sessionMemory);

    int updateById(AgentSessionMemory sessionMemory);

    AgentSessionMemory queryBySessionId(@Param("sessionId") String sessionId);

    /**
     * 使用 session_id 唯一键原地刷新当前会话快照。
     */
    int upsert(AgentSessionMemory sessionMemory);

    int softDeleteBySessionId(@Param("sessionId") String sessionId);
}
