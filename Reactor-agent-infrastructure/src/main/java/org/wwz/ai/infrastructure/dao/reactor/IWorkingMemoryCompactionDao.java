package org.wwz.ai.infrastructure.dao.reactor;

import org.apache.ibatis.annotations.Mapper;
import org.wwz.ai.domain.agent.memory.WorkingMemoryCompactionEvent;

@Mapper
public interface IWorkingMemoryCompactionDao {

    int insertEvent(WorkingMemoryCompactionEvent event);
}
