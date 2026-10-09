package org.wwz.ai.infrastructure.dao.reactor;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.wwz.ai.domain.agent.ledger.entity.DialogueSession;
import org.wwz.ai.domain.agent.ledger.model.DialogueSessionUpsertRecord;
import org.wwz.ai.domain.agent.ledger.model.DialogueSessionView;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话主表 DAO。
 */
@Mapper
public interface IDialogueSessionLedgerDao {

    int upsertSession(DialogueSessionUpsertRecord record);

    /**
     * run 启动时的会话主表写入：行不存在则按 run_count=1 新建，已存在则 run_count 原子 +1。
     * 不覆盖 started_at 与 finished/failed 计数，避免并发下的丢更新。
     */
    int upsertSessionOnRunStart(DialogueSessionUpsertRecord record);

    /**
     * run 结束时的会话主表写入：按终态原子累加 finished/failed 计数，不做读-改-写。
     */
    int updateSessionRunFinish(@Param("sessionId") String sessionId,
                               @Param("status") int status,
                               @Param("latestSummaryText") String latestSummaryText,
                               @Param("finishedDelta") int finishedDelta,
                               @Param("failedDelta") int failedDelta,
                               @Param("lastActiveAt") LocalDateTime lastActiveAt);

    DialogueSession queryBySessionId(@Param("sessionId") String sessionId);

    DialogueSession querySessionOwnership(@Param("sessionId") String sessionId);

    DialogueSessionView querySessionView(@Param("sessionId") String sessionId);

    DialogueSessionView querySessionHistoryView(@Param("sessionId") String sessionId);

    List<DialogueSessionView> queryRecentSessions(@Param("limit") int limit);

    DialogueSessionView querySessionViewByUserId(@Param("userId") String userId,
                                                  @Param("sessionId") String sessionId);

    List<DialogueSessionView> queryRecentSessionsByUserId(@Param("userId") String userId,
                                                           @Param("limit") int limit);

    List<DialogueSessionView> queryRecentSessionsByUserIdAfter(@Param("userId") String userId,
                                                                @Param("afterLastActiveAt") LocalDateTime afterLastActiveAt,
                                                                @Param("afterId") Long afterId,
                                                                @Param("limit") int limit);

    int bumpEventSeq(@Param("sessionId") String sessionId, @Param("eventSeq") long eventSeq);
}
