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
 *
 * <p>这里只把 DAO 查询结果映射为 ledger 读模型，不创建历史消息表或其它第二套持久化真相。</p>
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
        // requestId 是运行时外部关联键，先定位 run，再由调用方按 runId 查询 LLM、tool 和 artifact 事实。
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
        // recent 查询直接返回投影所需视图，仓储不在这里拼装前端事件或改变账本事实。
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
        // visitor 过滤在 DAO 层执行，避免先查全量 session 再在内存中泄露或误混访客数据。
        return dialogueSessionLedgerDao.queryRecentSessionsByVisitor(visitorId, limit);
    }

    @Override
    public List<ArtifactRecord> queryArtifactsByRunIds(List<Long> runIds) {
        return artifactLedgerDao.queryByRunIds(runIds);
    }
}
