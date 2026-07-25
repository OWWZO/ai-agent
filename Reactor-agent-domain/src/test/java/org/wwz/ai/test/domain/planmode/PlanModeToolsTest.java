package org.wwz.ai.test.domain.planmode;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.planmode.PlanModeState;
import org.wwz.ai.domain.agent.runtime.tasklist.RuntimeBackgroundTask;
import org.wwz.ai.domain.agent.runtime.tasklist.RuntimeBackgroundTaskRegistry;
import org.wwz.ai.domain.agent.runtime.tasklist.SessionTaskItem;
import org.wwz.ai.domain.agent.runtime.tasklist.SessionTaskListStore;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.TaskToolNames;

/**
 * Task* / Plan Mode 核心存储与状态测试（避开 AgentContext @Slf4j 在 domain 单测 classpath 的 logback 冲突）。
 */
public class PlanModeToolsTest {

    @Test
    public void taskCreateAndGetRoundTrip() {
        SessionTaskListStore store = new SessionTaskListStore("session-1");
        SessionTaskItem created = store.create("实现登录", "完成 JWT 登录与单测", "正在实现登录", null);
        Assert.assertEquals("1", created.getId());
        Assert.assertEquals(SessionTaskItem.STATUS_PENDING, created.getStatus());

        SessionTaskItem got = store.get("1").orElseThrow();
        Assert.assertTrue(got.getDescription().contains("JWT"));
        Assert.assertEquals("实现登录", got.getSubject());
    }

    @Test
    public void taskStopStopsRunningBackgroundTask() {
        RuntimeBackgroundTaskRegistry registry = new RuntimeBackgroundTaskRegistry();
        RuntimeBackgroundTask bg = registry.register(RuntimeBackgroundTask.TYPE_LOCAL_AGENT, "explore", null);
        Assert.assertEquals(RuntimeBackgroundTask.STATUS_RUNNING, bg.getStatus());

        RuntimeBackgroundTask stopped = registry.stop(bg.getId()).orElseThrow();
        Assert.assertEquals(RuntimeBackgroundTask.STATUS_STOPPED, stopped.getStatus());
        Assert.assertNotNull(stopped.getEndedAtMs());
    }

    @Test
    public void enterAndExitPlanMode() {
        PlanModeState state = PlanModeState.builder().build();
        Assert.assertFalse(state.isPlanMode());

        state.enterPlanMode();
        Assert.assertTrue(state.isPlanMode());
        Assert.assertEquals(PlanModeState.MODE_DEFAULT, state.getPrePlanMode());

        state.setPlan("## Steps\n1. A\n2. B", null);
        String restored = state.exitPlanMode();
        Assert.assertEquals(PlanModeState.MODE_DEFAULT, restored);
        Assert.assertFalse(state.isPlanMode());
        Assert.assertTrue(state.isHasExitedPlanMode());
        Assert.assertTrue(state.getPlanContent().contains("Steps"));
    }

    @Test
    public void requestExitKeepsPlanModeUntilExit() {
        PlanModeState state = PlanModeState.builder().build();
        state.enterPlanMode();
        state.requestExitWithPlan("plan body", "p.md");
        Assert.assertTrue(state.isPlanMode());
        Assert.assertTrue(state.isExitPendingApproval());
        state.clearPendingApproval();
        Assert.assertFalse(state.isExitPendingApproval());
        Assert.assertTrue(state.isPlanMode());
    }

    @Test
    public void exitWhenAlreadyDefaultIsIdempotentEnter() {
        PlanModeState state = PlanModeState.builder().build();
        state.enterPlanMode();
        state.enterPlanMode();
        Assert.assertTrue(state.isPlanMode());
    }

    @Test
    public void toolNamesMatchCchaha() {
        Assert.assertEquals("TaskCreate", TaskToolNames.TASK_CREATE);
        Assert.assertEquals("TaskGet", TaskToolNames.TASK_GET);
        Assert.assertEquals("TaskUpdate", TaskToolNames.TASK_UPDATE);
        Assert.assertEquals("TaskList", TaskToolNames.TASK_LIST);
        Assert.assertEquals("TodoWrite", TaskToolNames.TODO_WRITE);
        Assert.assertEquals("TaskStop", TaskToolNames.TASK_STOP);
        Assert.assertEquals("EnterPlanMode", TaskToolNames.ENTER_PLAN_MODE);
        Assert.assertEquals("ExitPlanMode", TaskToolNames.EXIT_PLAN_MODE);
    }

    @Test
    public void taskUpdateAndListRoundTrip() {
        SessionTaskListStore store = new SessionTaskListStore("session-2");
        SessionTaskItem created = store.create("写单测", "覆盖登录", null, null);
        store.update(created.getId(), null, null, SessionTaskItem.STATUS_IN_PROGRESS, "正在写单测", null);
        Assert.assertEquals(SessionTaskItem.STATUS_IN_PROGRESS, store.get(created.getId()).orElseThrow().getStatus());
        store.update(created.getId(), null, null, SessionTaskItem.STATUS_COMPLETED, null, null);
        Assert.assertEquals(1, store.list().size());
        Assert.assertEquals(SessionTaskItem.STATUS_COMPLETED, store.list().get(0).getStatus());
    }

    @Test
    public void todoWriteReplaceAllAndClearWhenAllDone() {
        SessionTaskListStore store = new SessionTaskListStore("session-3");
        store.replaceAll(java.util.List.of(
                java.util.Map.of("content", "任务A", "status", "pending"),
                java.util.Map.of("content", "任务B", "status", "in_progress")
        ));
        Assert.assertEquals(2, store.list().size());
        store.replaceAll(java.util.List.of(
                java.util.Map.of("content", "任务A", "status", "completed"),
                java.util.Map.of("content", "任务B", "status", "completed")
        ));
        Assert.assertEquals(0, store.list().size());
    }

    @Test
    public void taskStopMissingIdReturnsEmpty() {
        RuntimeBackgroundTaskRegistry registry = new RuntimeBackgroundTaskRegistry();
        Assert.assertTrue(registry.stop("no-such").isEmpty());
        Assert.assertTrue(registry.get("no-such").isEmpty());
    }
}
