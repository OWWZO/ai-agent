package org.wwz.ai.infrastructure.dao.reactor;

import org.apache.ibatis.annotations.Mapper;
import org.wwz.ai.domain.agent.memory.ltm.LtmForkExecutionEvent;

@Mapper
public interface ILtmForkExecutionDao {

    int insertEvent(LtmForkExecutionEvent event);
}
