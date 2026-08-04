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

    /**
     * ngram FULLTEXT：按 visitor 跨会话检索 query_text / final_summary_text。
     */
    List<DialogueRunView> searchFullTextByVisitor(@Param("visitorId") String visitorId,
                                                 @Param("query") String query,
                                                 @Param("limit") int limit);

    /**
     * ngram FULLTEXT：仅当前 session。
     */
    List<DialogueRunView> searchFullTextBySession(@Param("sessionId") String sessionId,
                                                 @Param("query") String query,
                                                 @Param("limit") int limit);
}
