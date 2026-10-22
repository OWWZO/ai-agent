package org.wwz.ai.infrastructure.dao.cute;

import org.apache.ibatis.annotations.Mapper;
import org.wwz.ai.domain.agent.memory.ltm.LtmForkExecutionEvent;

@Mapper
public interface ILtmForkExecutionDao {

    int insertEvent(LtmForkExecutionEvent event);
}
