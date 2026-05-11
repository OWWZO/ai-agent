package org.wwz.ai.infrastructure.adapter.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.wwz.ai.domain.agent.ledger.IExecutionLedgerReadRepository;
import org.wwz.ai.domain.agent.ledger.entity.ArtifactRecord;
import org.wwz.ai.domain.agent.ledger.entity.DialogueSession;
import org.wwz.ai.domain.agent.ledger.entity.DialogueRun;
import org.wwz.ai.domain.agent.ledger.entity.LlmInvocation;
import org.wwz.ai.domain.agent.ledger.entity.ToolInvocation;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunView;
import org.wwz.ai.domain.agent.ledger.model.DialogueSessionView;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationView;
import org.wwz.ai.infrastructure.dao.reactor.IArtifactLedgerDao;
import org.wwz.ai.infrastructure.dao.reactor.IDialogueRunLedgerDao;
import org.wwz.ai.infrastructure.dao.reactor.IDialogueSessionLedgerDao;
import org.wwz.ai.infrastructure.dao.reactor.ILlmInvocationLedgerDao;
import org.wwz.ai.infrastructure.dao.reactor.IToolInvocationLedgerDao;

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
    public DialogueSession querySessionEntity(String sessionId) {
        return dialogueSessionLedgerDao.queryBySessionId(sessionId);
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
    public DialogueSessionView querySession(String visitorId, String sessionId) {
        return dialogueSessionLedgerDao.querySessionViewByVisitor(visitorId, sessionId);
    }

    @Override
    public List<DialogueSessionView> queryRecentSessions(String visitorId, int limit) {
        return dialogueSessionLedgerDao.queryRecentSessionsByVisitor(visitorId, limit);
    }

    @Override
    public List<ArtifactRecord> queryArtifactsByRunIds(List<Long> runIds) {
        return artifactLedgerDao.queryByRunIds(runIds);
    }
}
