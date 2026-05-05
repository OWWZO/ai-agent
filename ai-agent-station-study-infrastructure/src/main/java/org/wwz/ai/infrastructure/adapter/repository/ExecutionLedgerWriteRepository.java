package org.wwz.ai.infrastructure.adapter.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.wwz.ai.domain.agent.ledger.IExecutionLedgerWriteRepository;
import org.wwz.ai.domain.agent.ledger.entity.ArtifactRecord;
import org.wwz.ai.domain.agent.ledger.entity.DialogueSession;
import org.wwz.ai.domain.agent.ledger.entity.DialogueRun;
import org.wwz.ai.domain.agent.ledger.entity.LlmInvocation;
import org.wwz.ai.domain.agent.ledger.entity.ToolInvocation;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunView;
import org.wwz.ai.domain.agent.ledger.model.DialogueSessionUpsertRecord;
import org.wwz.ai.infrastructure.dao.reactor.IArtifactLedgerDao;
import org.wwz.ai.infrastructure.dao.reactor.IDialogueRunLedgerDao;
import org.wwz.ai.infrastructure.dao.reactor.IDialogueSessionLedgerDao;
import org.wwz.ai.infrastructure.dao.reactor.ILlmInvocationLedgerDao;
import org.wwz.ai.infrastructure.dao.reactor.IToolInvocationLedgerDao;

import java.util.List;

/**
 * Phase 1 执行账本写仓储适配器。
 * 继续复用现有 DAO / entity / mapper XML，只把持久化细节封在 infrastructure。
 */
@Repository
@RequiredArgsConstructor
public class ExecutionLedgerWriteRepository implements IExecutionLedgerWriteRepository {

    private final IDialogueRunLedgerDao dialogueRunLedgerDao;
    private final IDialogueSessionLedgerDao dialogueSessionLedgerDao;
    private final ILlmInvocationLedgerDao llmInvocationLedgerDao;
    private final IToolInvocationLedgerDao toolInvocationLedgerDao;
    private final IArtifactLedgerDao artifactLedgerDao;

    @Override
    public void insertRun(DialogueRun run) {
        dialogueRunLedgerDao.insertRun(run);
    }

    @Override
    public DialogueSession querySessionBySessionId(String sessionId) {
        return dialogueSessionLedgerDao.queryBySessionId(sessionId);
    }

    @Override
    public DialogueRun queryRunByRequestId(String requestId) {
        return dialogueRunLedgerDao.queryByRequestId(requestId);
    }

    @Override
    public List<LlmInvocation> queryLlmInvocationsByRunId(Long runId) {
        return llmInvocationLedgerDao.queryByRunId(runId);
    }

    @Override
    public List<ToolInvocation> queryToolInvocationsByRunId(Long runId) {
        return toolInvocationLedgerDao.queryByRunId(runId);
    }

    @Override
    public List<ArtifactRecord> queryArtifactsByRunId(Long runId) {
        return artifactLedgerDao.queryByRunId(runId);
    }

    @Override
    public void updateRunFinish(DialogueRun run) {
        dialogueRunLedgerDao.updateRunFinish(run);
    }

    @Override
    public void upsertSession(DialogueSessionUpsertRecord record) {
        dialogueSessionLedgerDao.upsertSession(record);
    }

    @Override
    public List<DialogueRunView> queryRunsBySessionId(String sessionId) {
        return dialogueRunLedgerDao.queryBySessionId(sessionId);
    }

    @Override
    public void insertLlmInvocation(LlmInvocation invocation) {
        llmInvocationLedgerDao.insertLlmInvocation(invocation);
    }

    @Override
    public void updateLlmInvocationFinish(LlmInvocation invocation) {
        llmInvocationLedgerDao.updateLlmInvocationFinish(invocation);
    }

    @Override
    public void insertToolInvocation(ToolInvocation invocation) {
        toolInvocationLedgerDao.insertToolInvocation(invocation);
    }

    @Override
    public void updateToolInvocationFinish(ToolInvocation invocation) {
        toolInvocationLedgerDao.updateToolInvocationFinish(invocation);
    }

    @Override
    public int batchInsertArtifacts(List<ArtifactRecord> records) {
        return artifactLedgerDao.batchInsertArtifacts(records);
    }
}
