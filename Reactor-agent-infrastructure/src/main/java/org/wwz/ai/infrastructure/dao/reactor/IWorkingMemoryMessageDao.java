package org.wwz.ai.infrastructure.dao.reactor;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.wwz.ai.domain.agent.memory.WorkingMemoryMessage;
import org.wwz.ai.domain.agent.memory.WorkingMemorySearchMessage;

import java.util.List;

@Mapper
public interface IWorkingMemoryMessageDao {

    int batchInsertMessages(@Param("records") List<WorkingMemoryMessage> records);

    List<WorkingMemoryMessage> selectBySessionIdOrdered(@Param("sessionId") String sessionId);

    List<WorkingMemoryMessage> selectByTurnIds(@Param("turnIds") List<Long> turnIds);

    List<WorkingMemorySearchMessage> searchFullTextBySession(@Param("sessionId") String sessionId,
                                                             @Param("query") String query,
                                                             @Param("limit") int limit,
                                                             @Param("roles") List<String> roles);

    List<WorkingMemorySearchMessage> searchFullTextByUserId(@Param("userId") String userId,
                                                             @Param("query") String query,
                                                             @Param("limit") int limit,
                                                             @Param("roles") List<String> roles);

    List<WorkingMemorySearchMessage> scanBySession(@Param("sessionId") String sessionId,
                                                   @Param("query") String query,
                                                   @Param("limit") int limit,
                                                   @Param("roles") List<String> roles);

    List<WorkingMemorySearchMessage> scanByUserId(@Param("userId") String userId,
                                                   @Param("query") String query,
                                                   @Param("limit") int limit,
                                                   @Param("roles") List<String> roles);

    WorkingMemorySearchMessage selectScrollAnchor(@Param("sessionId") String sessionId,
                                                  @Param("messageId") long messageId);

    List<WorkingMemorySearchMessage> selectScrollBefore(@Param("sessionId") String sessionId,
                                                        @Param("messageId") long messageId,
                                                        @Param("limit") int limit,
                                                        @Param("roles") List<String> roles);

    List<WorkingMemorySearchMessage> selectScrollAfter(@Param("sessionId") String sessionId,
                                                       @Param("messageId") long messageId,
                                                       @Param("limit") int limit,
                                                       @Param("roles") List<String> roles);

    List<WorkingMemorySearchMessage> selectHistoryBySession(@Param("sessionId") String sessionId);

    /** 最近的主会话摘要，用于 session_search browse。 */
    List<java.util.Map<String, Object>> selectRecentSessions(@Param("userId") String userId,
                                                             @Param("limit") int limit);

    java.util.Map<String, Object> selectSessionSummary(@Param("sessionId") String sessionId);
}
