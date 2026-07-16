package org.wwz.ai.infrastructure.dao.reactor;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.wwz.ai.domain.agent.memory.entity.PromptMemoryTurn;

/**
 * 提示词记忆轮次 DAO。
 */
@Mapper
public interface IPromptMemoryTurnDao {

    PromptMemoryTurn queryByRequestId(@Param("requestId") String requestId);

    int insertBuilding(PromptMemoryTurn turn);

    int markReady(@Param("turnId") Long turnId);
}
