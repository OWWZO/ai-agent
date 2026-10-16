package org.wwz.ai.infrastructure.adapter.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.wwz.ai.domain.agent.ledger.entity.DialogueRun;
import org.wwz.ai.domain.agent.ledger.entity.DialogueSession;
import org.wwz.ai.domain.agent.ledger.entity.ToolInvocation;
import org.wwz.ai.domain.agent.ledger.reconcile.IExecutionLedgerReconcileRepository;
import org.wwz.ai.domain.agent.ledger.reconcile.OrphanBackgroundTaskRow;
import org.wwz.ai.infrastructure.dao.reactor.IBackgroundTaskDao;
import org.wwz.ai.domain.agent.runtime.tasklist.RuntimeBackgroundTask;
import org.wwz.ai.infrastructure.dao.reactor.IDialogueRunLedgerDao;
import org.wwz.ai.infrastructure.dao.reactor.IDialogueSessionLedgerDao;
import org.wwz.ai.infrastructure.dao.reactor.IToolInvocationLedgerDao;
import org.wwz.ai.infrastructure.dao.reactor.po.BackgroundTaskPO;
import org.wwz.ai.infrastructure.tasklist.MyBatisTasklistPersistence;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 启动对账账本适配器：只做「三类孤儿扫描 + 终态写入」的 DAO 适配，业务判定在
 * {@link org.wwz.ai.domain.agent.ledger.reconcile.BackgroundRunReconciler}。
 */
@Repository
@RequiredArgsConstructor
public class ExecutionLedgerReconcileRepository implements IExecutionLedgerReconcileRepository {

    private final IDialogueRunLedgerDao dialogueRunLedgerDao;
    private final IDialogueSessionLedgerDao dialogueSessionLedgerDao;
    private final IToolInvocationLedgerDao toolInvocationLedgerDao;
    private final IBackgroundTaskDao backgroundTaskDao;

    @Override
    public List<DialogueRun> queryOrphanedRunningRuns(LocalDateTime startedBefore, int limit) {
        return nullToEmpty(dialogueRunLedgerDao.queryOrphanedRunningRuns(startedBefore, limit));
    }

    @Override
    public List<ToolInvocation> queryOrphanedRunningToolInvocations(LocalDateTime startedBefore, int limit) {
        return nullToEmpty(toolInvocationLedgerDao.queryOrphanedRunningToolInvocations(startedBefore, limit));
    }

    @Override
    public List<OrphanBackgroundTaskRow> queryOrphanedRunningBackgroundTasks(long startedBeforeMs, int limit) {
        List<BackgroundTaskPO> rows = backgroundTaskDao.selectOrphanedRunning(startedBeforeMs, limit);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<OrphanBackgroundTaskRow> result = new ArrayList<>(rows.size());
        for (BackgroundTaskPO row : rows) {
            if (row == null) {
                continue;
            }
            RuntimeBackgroundTask task = MyBatisTasklistPersistence.toBackgroundTask(row);
            if (task == null) {
                continue;
            }
            result.add(OrphanBackgroundTaskRow.builder()
                    .sessionId(row.getSessionId())
                    .task(task)
                    .build());
        }
        return result;
    }

    @Override
    public int updateRunFinish(DialogueRun run) {
        return dialogueRunLedgerDao.updateRunFinish(run);
    }

    @Override
    public int updateToolInvocationFinish(ToolInvocation invocation) {
        return toolInvocationLedgerDao.updateToolInvocationFinish(invocation);
    }

    @Override
    public DialogueSession querySessionBySessionId(String sessionId) {
        return dialogueSessionLedgerDao.queryBySessionId(sessionId);
    }

    @Override
    public int updateSessionRunFinish(String sessionId,
                                      int status,
                                      String latestSummaryText,
                                      int finishedDelta,
                                      int failedDelta,
                                      LocalDateTime lastActiveAt) {
        return dialogueSessionLedgerDao.updateSessionRunFinish(
                sessionId, status, latestSummaryText, finishedDelta, failedDelta, lastActiveAt);
    }

    private static <T> List<T> nullToEmpty(List<T> rows) {
        return rows == null ? List.of() : rows;
    }
}
