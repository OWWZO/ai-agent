package org.wwz.ai.domain.agent.ledger;

import org.wwz.ai.domain.agent.ledger.entity.ArtifactRecord;
import org.wwz.ai.domain.agent.ledger.entity.DialogueSession;
import org.wwz.ai.domain.agent.ledger.entity.DialogueRun;
import org.wwz.ai.domain.agent.ledger.entity.LlmInvocation;
import org.wwz.ai.domain.agent.ledger.entity.ToolInvocation;
import org.wwz.ai.domain.agent.ledger.model.DialogueSessionUpsertRecord;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Phase 1 执行账本写仓储端口。
 * 仅暴露当前 Recorder 所需的领域级写操作，屏蔽底层 DAO 细节。
 */
public interface IExecutionLedgerWriteRepository {

    void insertRun(DialogueRun run);

    DialogueSession querySessionBySessionId(String sessionId);

    DialogueRun queryRunByRequestId(String requestId);

    List<LlmInvocation> queryLlmInvocationsByRunId(Long runId);

    List<ToolInvocation> queryToolInvocationsByRunId(Long runId);

    List<ArtifactRecord> queryArtifactsByRunId(Long runId);

    /**
     * 写入 run 终态。返回受影响行数：0 表示该 run 已是终态（陈旧/重复回调），调用方不应再累加会话计数。
     */
    int updateRunFinish(DialogueRun run);

    void upsertSession(DialogueSessionUpsertRecord record);

    /**
     * run 启动：会话 run_count 原子 +1（行不存在则新建），不做读-改-写。
     */
    void upsertSessionOnRunStart(DialogueSessionUpsertRecord record);

    /**
     * run 结束：按终态原子累加 finished/failed 计数。
     */
    int updateSessionRunFinish(String sessionId,
                               int status,
                               String latestSummaryText,
                               int finishedDelta,
                               int failedDelta,
                               LocalDateTime lastActiveAt);

    List<org.wwz.ai.domain.agent.ledger.model.DialogueRunView> queryRunsBySessionId(String sessionId);

    void insertLlmInvocation(LlmInvocation invocation);

    void updateLlmInvocationFinish(LlmInvocation invocation);

    void insertToolInvocation(ToolInvocation invocation);

    void updateToolInvocationFinish(ToolInvocation invocation);

    int batchInsertArtifacts(List<ArtifactRecord> records);

    void bumpSessionEventSeq(String sessionId, long eventSeq);
}
