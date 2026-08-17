package org.wwz.ai.infrastructure.dao.reactor;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.wwz.ai.infrastructure.dao.reactor.po.SessionTodoPO;

import java.util.List;

@Mapper
public interface ISessionTodoDao {

    List<SessionTodoPO> selectBySessionId(@Param("sessionId") String sessionId);

    Integer selectMaxSeqNo(@Param("sessionId") String sessionId);

    int upsert(SessionTodoPO row);

    int softDelete(@Param("sessionId") String sessionId, @Param("taskId") String taskId);

    int softDeleteAllBySessionId(@Param("sessionId") String sessionId);
}
