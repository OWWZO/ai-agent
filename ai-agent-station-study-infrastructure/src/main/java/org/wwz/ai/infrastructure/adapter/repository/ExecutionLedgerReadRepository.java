package org.wwz.ai.infrastructure.adapter.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.wwz.ai.domain.agent.reactor.adapter.repository.IExecutionLedgerReadRepository;
import org.wwz.ai.domain.agent.reactor.entity.ArtifactRecord;
import org.wwz.ai.domain.agent.reactor.entity.DialogueRun;
import org.wwz.ai.domain.agent.reactor.entity.LlmInvocation;
import org.wwz.ai.domain.agent.reactor.entity.ToolInvocation;
import org.wwz.ai.domain.agent.reactor.mapper.IArtifactLedgerDao;
import org.wwz.ai.domain.agent.reactor.mapper.IDialogueRunLedgerDao;
import org.wwz.ai.domain.agent.reactor.mapper.IDialogueSessionLedgerDao;
import org.wwz.ai.domain.agent.reactor.mapper.ILlmInvocationLedgerDao;
import org.wwz.ai.domain.agent.reactor.mapper.IToolInvocationLedgerDao;
import org.wwz.ai.domain.agent.reactor.model.ledger.DialogueRunView;
import org.wwz.ai.domain.agent.reactor.model.ledger.DialogueSessionView;
import org.wwz.ai.domain.agent.reactor.model.ledger.ToolInvocationView;

import java.util.List;

/**
 * Phase 1 执行账本读仓储适配器。
 */
@Repository
@RequiredArgsConstructor
public class ExecutionLedgerReadRepository implements IExecutionLedgerReadRepository {

    private final IDialogueRunLedgerDao dialogueRunLedgerDao;
    private final IDialogueSessionLedgerDao dialogueSessionLedgerDao;
    private final ILlmInvocationLedgerDao llmInvocationLedgerDao;
    private final IToolInvocationLedgerDao toolInvocationLedgerDao;
    private final IArtifactLedgerDao artifactLedgerDao;

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
    public List<ToolInvocationView> queryRecentToolInvocations(String toolName, int limit) {
        return toolInvocationLedgerDao.queryRecentByToolName(toolName, limit);
    }

    @Override
    public List<DialogueRunView> queryRecentRunsBySessionId(String sessionId, int limit) {
        return dialogueRunLedgerDao.queryRecentBySessionId(sessionId, limit);
    }

    @Override
    public List<DialogueRunView> queryRunsBySessionId(String sessionId) {
        return dialogueRunLedgerDao.queryBySessionId(sessionId);
    }

    @Override
    public DialogueSessionView querySession(String sessionId) {
        return dialogueSessionLedgerDao.querySessionView(sessionId);
    }

    @Override
    public List<DialogueSessionView> queryRecentSessions(int limit) {
        return dialogueSessionLedgerDao.queryRecentSessions(limit);
    }

    @Override
    public List<ArtifactRecord> queryArtifactsByRunIds(List<Long> runIds) {
        return artifactLedgerDao.queryByRunIds(runIds);
    }
}
