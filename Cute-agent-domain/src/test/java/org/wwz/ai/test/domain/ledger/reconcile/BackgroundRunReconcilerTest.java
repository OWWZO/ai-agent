package org.wwz.ai.test.domain.ledger.reconcile;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.ledger.entity.DialogueRun;
import org.wwz.ai.domain.agent.ledger.entity.DialogueSession;
import org.wwz.ai.domain.agent.ledger.entity.ToolInvocation;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.reconcile.BackgroundRunReconciler;
import org.wwz.ai.domain.agent.ledger.reconcile.IExecutionLedgerReconcileRepository;
import org.wwz.ai.domain.agent.ledger.reconcile.OrphanBackgroundTaskRow;
import org.wwz.ai.domain.agent.runtime.tasklist.RuntimeBackgroundTask;
import org.wwz.ai.domain.agent.runtime.tasklist.SessionTaskItem;
import org.wwz.ai.domain.agent.runtime.tasklist.TasklistPersistencePort;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 启动对账器测试：僵尸收敛 / 非僵尸不碰 / 幂等重入 / 宽限窗边界 / 会话计数不双累加。
 */
public class BackgroundRunReconcilerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 16, 12, 0, 0);
    private static final long GRACE_MS = 60_000L;
    private static final int BATCH = 500;

    private final InMemoryReconcileRepository repository = new InMemoryReconcileRepository();
    private final InMemoryTasklist tasklist = new InMemoryTasklist(repository.backgroundTasks);

    private BackgroundRunReconciler reconciler() {
        return new BackgroundRunReconciler(repository, tasklist, GRACE_MS, BATCH);
    }

    /** 宽限窗边界：startedAt == now - grace 不算孤儿（严格小于）。 */
    private LocalDateTime atCutoff() {
        return NOW.minusNanos(GRACE_MS * 1_000_000L);
    }

    @Test
    public void shouldConvergeRunToolAndTaskOrphans() {
        DialogueRun run = repository.insertRun("req-1", "sess-1", NOW.minusMinutes(30));
        repository.insertSession("sess-1", "上一轮摘要");
        ToolInvocation tool = repository.insertTool(run.getId(), "Agent", NOW.minusMinutes(29));
        repository.backgroundTasks.add(OrphanBackgroundTaskRow.builder()
                .sessionId("sess-1")
                .task(RuntimeBackgroundTask.builder()
                        .id("task-1")
                        .type(RuntimeBackgroundTask.TYPE_LOCAL_AGENT)
                        .status(RuntimeBackgroundTask.STATUS_RUNNING)
                        .startedAtMs(NOW.minusMinutes(30).atZone(java.time.ZoneId.systemDefault())
                                .toInstant().toEpochMilli())
                        .build())
                .build());

        BackgroundRunReconciler.Report report = reconciler().reconcile(NOW);

        Assert.assertEquals(1, report.getRunConverged());
        Assert.assertEquals(1, report.getToolConverged());
        Assert.assertEquals(1, report.getTaskConverged());
        Assert.assertFalse(report.hasErrors());

        // Z1：run 落 STOPPED + 终态语义
        DialogueRun settledRun = repository.runs.get(run.getId());
        Assert.assertEquals(Integer.valueOf(ExecutionLedgerConstants.STATUS_STOPPED), settledRun.getStatus());
        Assert.assertEquals(BackgroundRunReconciler.ERROR_CODE_SERVER_RESTART, settledRun.getErrorCode());
        Assert.assertEquals(BackgroundRunReconciler.REASON_SERVER_RESTART, settledRun.getErrorMsg());
        Assert.assertEquals(NOW, settledRun.getFinishedAt());
        Assert.assertNotNull(settledRun.getDurationMs());

        // Z1 附带：会话状态与计数（STOPPED 计 finished+failed 各 1），且摘要未被清空
        DialogueSession session = repository.sessions.get("sess-1");
        Assert.assertEquals(Integer.valueOf(ExecutionLedgerConstants.STATUS_STOPPED), session.getStatus());
        Assert.assertEquals(Integer.valueOf(1), session.getFinishedRunCount());
        Assert.assertEquals(Integer.valueOf(1), session.getFailedRunCount());
        Assert.assertEquals("上一轮摘要", session.getLatestSummaryText());

        // Z2：工具落 STOPPED，回放投影据此生成终态 tool_result
        ToolInvocation settledTool = repository.tools.get(tool.getId());
        Assert.assertEquals(Integer.valueOf(ExecutionLedgerConstants.STATUS_STOPPED), settledTool.getStatus());
        Assert.assertEquals(BackgroundRunReconciler.REASON_SERVER_RESTART, settledTool.getErrorMsg());
        Assert.assertEquals(NOW, settledTool.getFinishedAt());

        // Z3：任务行落 lost 而非 running/failed
        RuntimeBackgroundTask settledTask = repository.backgroundTasks.get(0).getTask();
        Assert.assertEquals(RuntimeBackgroundTask.STATUS_LOST, settledTask.getStatus());
        Assert.assertNotNull(settledTask.getEndedAtMs());
        Assert.assertEquals(BackgroundRunReconciler.REASON_SERVER_RESTART, settledTask.getErrorMsg());
    }

    @Test
    public void shouldNotTouchLiveOrWaitingRecords() {
        // 宽限窗内的新 run：可能是上一实例刚派发、正在拖尾的任务
        DialogueRun freshRun = repository.insertRun("req-fresh", "sess-2", NOW.minusSeconds(1));
        // HITL 停驻态：合法等待用户输入，不是孤儿
        DialogueRun waitingRun = repository.insertRun("req-wait", "sess-2", NOW.minusMinutes(10));
        waitingRun.setStatus(ExecutionLedgerConstants.STATUS_WAITING_INPUT);
        // 父 run 仍 RUNNING 的工具：本轮还没收尾，不能判孤儿
        DialogueRun liveRun = repository.insertRun("req-live", "sess-3", NOW.minusSeconds(30));
        ToolInvocation liveTool = repository.insertTool(liveRun.getId(), "Agent", NOW.minusSeconds(30));
        // 已终态的任务行
        repository.backgroundTasks.add(OrphanBackgroundTaskRow.builder()
                .sessionId("sess-2")
                .task(RuntimeBackgroundTask.builder()
                        .id("task-done")
                        .status(RuntimeBackgroundTask.STATUS_COMPLETED)
                        .startedAtMs(0L)
                        .build())
                .build());

        BackgroundRunReconciler.Report report = reconciler().reconcile(NOW);

        Assert.assertEquals(0, report.getRunConverged());
        Assert.assertEquals(0, report.getToolConverged());
        Assert.assertEquals(0, report.getTaskConverged());
        Assert.assertEquals(Integer.valueOf(ExecutionLedgerConstants.STATUS_RUNNING),
                repository.runs.get(freshRun.getId()).getStatus());
        Assert.assertEquals(Integer.valueOf(ExecutionLedgerConstants.STATUS_WAITING_INPUT),
                repository.runs.get(waitingRun.getId()).getStatus());
        Assert.assertEquals(Integer.valueOf(ExecutionLedgerConstants.STATUS_RUNNING),
                repository.runs.get(liveRun.getId()).getStatus());
        Assert.assertNull(repository.tools.get(liveTool.getId()).getFinishedAt());
        Assert.assertEquals(RuntimeBackgroundTask.STATUS_COMPLETED,
                repository.backgroundTasks.get(0).getTask().getStatus());
    }

    @Test
    public void shouldBeIdempotentOnRepeatedReconcile() {
        repository.insertSession("sess-1", "摘要");
        DialogueRun run = repository.insertRun("req-1", "sess-1", NOW.minusMinutes(30));
        repository.insertTool(run.getId(), "Agent", NOW.minusMinutes(29));

        BackgroundRunReconciler reconciler = reconciler();
        reconciler.reconcile(NOW);
        BackgroundRunReconciler.Report second = reconciler.reconcile(NOW);

        Assert.assertEquals(0, second.getRunConverged());
        Assert.assertEquals(0, second.getToolConverged());
        // 会话计数只累加一次：重复对账不能把 finished/failed 顶到 2
        Assert.assertEquals(Integer.valueOf(1), repository.sessions.get("sess-1").getFinishedRunCount());
        Assert.assertEquals(Integer.valueOf(1), repository.sessions.get("sess-1").getFailedRunCount());
    }

    @Test
    public void shouldTreatGraceWindowBoundaryAsInclusiveExclusive() {
        repository.insertSession("sess-1", null);
        // startedAt 恰好等于 cutoff：不算孤儿（判据是严格早于）
        DialogueRun onBoundary = repository.insertRun("req-boundary", "sess-1", atCutoff());
        // 比 cutoff 早 1 毫秒：算孤儿
        DialogueRun beforeBoundary = repository.insertRun("req-before", "sess-1", atCutoff().minusNanos(1_000_000L));

        BackgroundRunReconciler.Report report = reconciler().reconcile(NOW);

        Assert.assertEquals(1, report.getRunConverged());
        Assert.assertEquals(Integer.valueOf(ExecutionLedgerConstants.STATUS_RUNNING),
                repository.runs.get(onBoundary.getId()).getStatus());
        Assert.assertEquals(Integer.valueOf(ExecutionLedgerConstants.STATUS_STOPPED),
                repository.runs.get(beforeBoundary.getId()).getStatus());
    }

    @Test
    public void shouldSkipSessionUpdateWhenRunHasNoSession() {
        DialogueRun run = repository.insertRun("req-nosession", null, NOW.minusMinutes(30));

        BackgroundRunReconciler.Report report = reconciler().reconcile(NOW);

        Assert.assertEquals(1, report.getRunConverged());
        Assert.assertFalse(report.hasErrors());
        Assert.assertTrue(repository.sessions.isEmpty());
    }

    @Test
    public void shouldSkipTaskReconcileWhenPersistenceAbsent() {
        BackgroundRunReconciler reconciler = new BackgroundRunReconciler(repository, null, GRACE_MS, BATCH);
        repository.backgroundTasks.add(OrphanBackgroundTaskRow.builder()
                .sessionId("sess-1")
                .task(RuntimeBackgroundTask.builder()
                        .id("task-1")
                        .status(RuntimeBackgroundTask.STATUS_RUNNING)
                        .startedAtMs(0L)
                        .build())
                .build());

        BackgroundRunReconciler.Report report = reconciler.reconcile(NOW);

        Assert.assertEquals(0, report.getTaskConverged());
        Assert.assertFalse(report.hasErrors());
        Assert.assertEquals(RuntimeBackgroundTask.STATUS_RUNNING,
                repository.backgroundTasks.get(0).getTask().getStatus());
    }

    @Test
    public void shouldRecordErrorAndContinueWhenScanFails() {
        repository.failRunScan = true;
        BackgroundRunReconciler.Report report = reconciler().reconcile(NOW);

        Assert.assertTrue(report.hasErrors());
        Assert.assertEquals(0, report.getRunConverged());
        // run 扫描失败不影响后续两个扫描器继续跑
        Assert.assertEquals(0, report.getToolConverged());
        Assert.assertEquals(0, report.getTaskConverged());
    }

    private static DialogueRun copyRun(DialogueRun source) {
        return DialogueRun.builder()
                .id(source.getId())
                .runUid(source.getRunUid())
                .requestId(source.getRequestId())
                .sessionId(source.getSessionId())
                .status(source.getStatus())
                .finalSummaryText(source.getFinalSummaryText())
                .errorCode(source.getErrorCode())
                .errorMsg(source.getErrorMsg())
                .startedAt(source.getStartedAt())
                .finishedAt(source.getFinishedAt())
                .durationMs(source.getDurationMs())
                .build();
    }

    private static ToolInvocation copyTool(ToolInvocation source) {
        return ToolInvocation.builder()
                .id(source.getId())
                .runId(source.getRunId())
                .toolCallId(source.getToolCallId())
                .toolName(source.getToolName())
                .status(source.getStatus())
                .errorMsg(source.getErrorMsg())
                .startedAt(source.getStartedAt())
                .finishedAt(source.getFinishedAt())
                .build();
    }

    // ------------------------------------------------------------------ fakes

    private static final class InMemoryReconcileRepository implements IExecutionLedgerReconcileRepository {

        private final AtomicLong seq = new AtomicLong(1);
        private final java.util.Map<Long, DialogueRun> runs = new java.util.LinkedHashMap<>();
        private final java.util.Map<Long, ToolInvocation> tools = new java.util.LinkedHashMap<>();
        private final java.util.Map<String, DialogueSession> sessions = new java.util.LinkedHashMap<>();
        private final List<OrphanBackgroundTaskRow> backgroundTasks = new ArrayList<>();
        private boolean failRunScan = false;

        DialogueRun insertRun(String requestId, String sessionId, LocalDateTime startedAt) {
            DialogueRun run = DialogueRun.builder()
                    .id(seq.getAndIncrement())
                    .runUid(requestId)
                    .requestId(requestId)
                    .sessionId(sessionId)
                    .status(ExecutionLedgerConstants.STATUS_RUNNING)
                    .startedAt(startedAt)
                    .build();
            runs.put(run.getId(), run);
            return run;
        }

        ToolInvocation insertTool(Long runId, String toolName, LocalDateTime startedAt) {
            ToolInvocation tool = ToolInvocation.builder()
                    .id(seq.getAndIncrement())
                    .runId(runId)
                    .toolName(toolName)
                    .status(ExecutionLedgerConstants.STATUS_RUNNING)
                    .startedAt(startedAt)
                    .build();
            tools.put(tool.getId(), tool);
            return tool;
        }

        void insertSession(String sessionId, String summary) {
            sessions.put(sessionId, DialogueSession.builder()
                    .id(seq.getAndIncrement())
                    .sessionId(sessionId)
                    .latestSummaryText(summary)
                    .runCount(1)
                    .finishedRunCount(0)
                    .failedRunCount(0)
                    .build());
        }

        @Override
        public List<DialogueRun> queryOrphanedRunningRuns(LocalDateTime startedBefore, int limit) {
            if (failRunScan) {
                throw new IllegalStateException("simulated scan failure");
            }
            // 与生产一致：返回的是「读出来的副本」，调用方对实体的修改不写回，
            // 只有显式 updateRunFinish 才改存储（终态守卫据此判重）。
            return runs.values().stream()
                    .filter(run -> run.getStatus() != null
                            && run.getStatus() == ExecutionLedgerConstants.STATUS_RUNNING
                            && run.getStartedAt().isBefore(startedBefore))
                    .sorted(Comparator.comparing(DialogueRun::getStartedAt))
                    .limit(limit)
                    .map(BackgroundRunReconcilerTest::copyRun)
                    .collect(java.util.stream.Collectors.toList());
        }

        @Override
        public List<ToolInvocation> queryOrphanedRunningToolInvocations(LocalDateTime startedBefore, int limit) {
            return tools.values().stream()
                    .filter(tool -> tool.getStatus() != null
                            && tool.getStatus() == ExecutionLedgerConstants.STATUS_RUNNING
                            && tool.getFinishedAt() == null
                            && parentRunTerminal(tool.getRunId())
                            && tool.getStartedAt().isBefore(startedBefore))
                    .sorted(Comparator.comparing(ToolInvocation::getStartedAt))
                    .limit(limit)
                    .map(BackgroundRunReconcilerTest::copyTool)
                    .collect(java.util.stream.Collectors.toList());
        }

        /** 父 run 已进终态（WAITING_INPUT 是合法停驻，不算终态，其工具仍可能在跑）。 */
        private boolean parentRunTerminal(Long runId) {
            DialogueRun run = runId == null ? null : runs.get(runId);
            return run != null && run.isTerminal();
        }

        @Override
        public List<OrphanBackgroundTaskRow> queryOrphanedRunningBackgroundTasks(long startedBeforeMs, int limit) {
            return backgroundTasks.stream()
                    .filter(row -> row.getTask() != null
                            && RuntimeBackgroundTask.STATUS_RUNNING.equals(row.getTask().getStatus())
                            && row.getTask().getStartedAtMs() < startedBeforeMs)
                    .limit(limit)
                    .collect(java.util.stream.Collectors.toList());
        }

        @Override
        public int updateRunFinish(DialogueRun run) {
            DialogueRun existing = runs.get(run.getId());
            if (existing == null || existing.isTerminal()) {
                return 0;
            }
            existing.setStatus(run.getStatus());
            existing.setErrorCode(run.getErrorCode());
            existing.setErrorMsg(run.getErrorMsg());
            existing.setFinishedAt(run.getFinishedAt());
            existing.setDurationMs(run.getDurationMs());
            return 1;
        }

        @Override
        public int updateToolInvocationFinish(ToolInvocation invocation) {
            ToolInvocation existing = tools.get(invocation.getId());
            if (existing == null || existing.getStatus() == null
                    || existing.getStatus() != ExecutionLedgerConstants.STATUS_RUNNING
                    || existing.getFinishedAt() != null) {
                return 0;
            }
            existing.setStatus(invocation.getStatus());
            existing.setErrorMsg(invocation.getErrorMsg());
            existing.setFinishedAt(invocation.getFinishedAt());
            return 1;
        }

        @Override
        public DialogueSession querySessionBySessionId(String sessionId) {
            return sessionId == null ? null : sessions.get(sessionId);
        }

        @Override
        public int updateSessionRunFinish(String sessionId,
                                          int status,
                                          String latestSummaryText,
                                          int finishedDelta,
                                          int failedDelta,
                                          LocalDateTime lastActiveAt) {
            DialogueSession session = sessions.get(sessionId);
            if (session == null) {
                return 0;
            }
            session.setStatus(status);
            session.setLatestSummaryText(latestSummaryText);
            session.setFinishedRunCount(session.getFinishedRunCount() + finishedDelta);
            session.setFailedRunCount(session.getFailedRunCount() + failedDelta);
            session.setLastActiveAt(lastActiveAt);
            return 1;
        }
    }

    private static final class InMemoryTasklist implements TasklistPersistencePort {

        private final List<OrphanBackgroundTaskRow> tasks;

        private InMemoryTasklist(List<OrphanBackgroundTaskRow> tasks) {
            this.tasks = tasks;
        }

        @Override
        public List<SessionTaskItem> loadTodos(String sessionId) {
            return List.of();
        }

        @Override
        public int loadTodoHighWaterMark(String sessionId) {
            return 0;
        }

        @Override
        public void upsertTodo(String sessionId, SessionTaskItem item) {
            // no-op
        }

        @Override
        public void deleteTodo(String sessionId, String taskId) {
            // no-op
        }

        @Override
        public void replaceTodos(String sessionId, List<SessionTaskItem> items) {
            // no-op
        }

        @Override
        public List<RuntimeBackgroundTask> loadBackgroundTasks(String sessionId) {
            return List.of();
        }

        @Override
        public Optional<RuntimeBackgroundTask> findBackgroundTask(String sessionId, String taskId) {
            return Optional.empty();
        }

        @Override
        public void upsertBackgroundTask(String sessionId, RuntimeBackgroundTask task) {
            for (OrphanBackgroundTaskRow row : tasks) {
                if (row.getTask() != null && row.getTask().getId() != null
                        && row.getTask().getId().equals(task.getId())) {
                    row.setTask(task);
                    return;
                }
            }
            tasks.add(OrphanBackgroundTaskRow.builder().sessionId(sessionId).task(task).build());
        }
    }
}
