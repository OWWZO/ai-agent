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

    void updateRunFinish(DialogueRun run);

    void upsertSession(DialogueSessionUpsertRecord record);

    List<org.wwz.ai.domain.agent.ledger.model.DialogueRunView> queryRunsBySessionId(String sessionId);

    void insertLlmInvocation(LlmInvocation invocation);

    void updateLlmInvocationFinish(LlmInvocation invocation);

    void insertToolInvocation(ToolInvocation invocation);

    void updateToolInvocationFinish(ToolInvocation invocation);

    int batchInsertArtifacts(List<ArtifactRecord> records);
}
