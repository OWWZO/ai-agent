package org.wwz.ai.infrastructure.dao.reactor;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.wwz.ai.domain.agent.memory.WorkingMemoryMessage;

import java.util.List;

@Mapper
public interface IWorkingMemoryMessageDao {

    int batchInsertMessages(@Param("records") List<WorkingMemoryMessage> records);

    List<WorkingMemoryMessage> selectBySessionIdOrdered(@Param("sessionId") String sessionId);

    List<WorkingMemoryMessage> selectByTurnIds(@Param("turnIds") List<Long> turnIds);
}
