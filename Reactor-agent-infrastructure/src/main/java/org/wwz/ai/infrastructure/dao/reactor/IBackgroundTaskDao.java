package org.wwz.ai.infrastructure.dao.reactor;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.wwz.ai.infrastructure.dao.reactor.po.BackgroundTaskPO;

import java.util.List;

@Mapper
public interface IBackgroundTaskDao {

    List<BackgroundTaskPO> selectBySessionId(@Param("sessionId") String sessionId);

    BackgroundTaskPO selectBySessionAndTaskId(@Param("sessionId") String sessionId,
                                              @Param("taskId") String taskId);

    int upsert(BackgroundTaskPO row);
}
