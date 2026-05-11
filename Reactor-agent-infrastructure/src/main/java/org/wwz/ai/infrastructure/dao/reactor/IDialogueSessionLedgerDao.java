package org.wwz.ai.infrastructure.dao.reactor;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.wwz.ai.domain.agent.ledger.entity.DialogueSession;
import org.wwz.ai.domain.agent.ledger.model.DialogueSessionUpsertRecord;
import org.wwz.ai.domain.agent.ledger.model.DialogueSessionView;

import java.util.List;

/**
 * 会话主表 DAO。
 */
@Mapper
public interface IDialogueSessionLedgerDao {

    int upsertSession(DialogueSessionUpsertRecord record);

    DialogueSession queryBySessionId(@Param("sessionId") String sessionId);

    DialogueSessionView querySessionView(@Param("sessionId") String sessionId);

    List<DialogueSessionView> queryRecentSessions(@Param("limit") int limit);

    DialogueSessionView querySessionViewByVisitor(@Param("visitorId") String visitorId,
                                                  @Param("sessionId") String sessionId);

    List<DialogueSessionView> queryRecentSessionsByVisitor(@Param("visitorId") String visitorId,
                                                           @Param("limit") int limit);
}
