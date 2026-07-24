package org.wwz.ai.infrastructure.dao.reactor;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.wwz.ai.domain.agent.memory.WorkingMemoryTurn;

@Mapper
public interface IWorkingMemoryTurnDao {

    int insertTurn(WorkingMemoryTurn turn);

    Integer selectMaxTurnSeq(@Param("sessionId") String sessionId);

    WorkingMemoryTurn selectByRequestId(@Param("requestId") String requestId);

    java.util.List<WorkingMemoryTurn> selectReadyBySessionId(@Param("sessionId") String sessionId);
}
