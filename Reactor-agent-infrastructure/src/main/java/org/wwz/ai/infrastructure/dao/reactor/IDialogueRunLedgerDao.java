package org.wwz.ai.infrastructure.dao.reactor;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.wwz.ai.domain.agent.ledger.entity.DialogueRun;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunView;

import java.util.List;

/**
 * 对话执行总账 DAO。
 */
@Mapper
public interface IDialogueRunLedgerDao {

    int insertRun(DialogueRun run);

    int updateRunFinish(DialogueRun run);

    DialogueRun queryByRequestId(@Param("requestId") String requestId);

    List<DialogueRunView> queryRecentBySessionId(@Param("sessionId") String sessionId,
                                                 @Param("limit") int limit);

    List<DialogueRunView> queryBySessionId(@Param("sessionId") String sessionId);
}
