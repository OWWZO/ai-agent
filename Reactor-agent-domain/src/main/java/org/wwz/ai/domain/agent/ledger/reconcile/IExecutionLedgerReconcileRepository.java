package org.wwz.ai.domain.agent.ledger.reconcile;

import org.wwz.ai.domain.agent.ledger.entity.DialogueRun;
import org.wwz.ai.domain.agent.ledger.entity.DialogueSession;
import org.wwz.ai.domain.agent.ledger.entity.ToolInvocation;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 启动对账所需的账本读写端口。
 * <p>
 * 刻意与 {@link org.wwz.ai.domain.agent.ledger.IExecutionLedgerWriteRepository} 分开：
 * 对账只在启动跑一次，只用到「三类孤儿扫描 + 四个终态写入」，单独成口可以让对账器
 * 与健康路径解耦，也便于用很小的内存 stub 做单测。
 */
public interface IExecutionLedgerReconcileRepository {

    /**
     * 扫「仍 RUNNING 且 started_at 早于宽限窗边界」的 run 孤儿。
     */
    List<DialogueRun> queryOrphanedRunningRuns(LocalDateTime startedBefore, int limit);

    /**
     * 扫「父 run 已终态、自身仍 RUNNING 且 finished_at 为空」的工具孤儿。
     */
    List<ToolInvocation> queryOrphanedRunningToolInvocations(LocalDateTime startedBefore, int limit);

    /**
     * 扫「仍 running 且 started_at_ms 早于宽限窗边界」的后台任务登记行。
     */
    List<OrphanBackgroundTaskRow> queryOrphanedRunningBackgroundTasks(long startedBeforeMs, int limit);

    /**
     * 写 run 终态。返回受影响行数，0 表示已是终态（并发/重复回调），调用方不应再累加会话计数。
     */
    int updateRunFinish(DialogueRun run);

    /**
     * 写工具终态。实现应保留 status=RUNNING 且 finished_at IS NULL 守卫，保证并发/重复对账幂等。
     */
    int updateToolInvocationFinish(ToolInvocation invocation);

    DialogueSession querySessionBySessionId(String sessionId);

    int updateSessionRunFinish(String sessionId,
                               int status,
                               String latestSummaryText,
                               int finishedDelta,
                               int failedDelta,
                               LocalDateTime lastActiveAt);
}
