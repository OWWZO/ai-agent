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
