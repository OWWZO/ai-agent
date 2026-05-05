package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.dto.Plan;
import org.wwz.ai.domain.agent.runtime.tool.common.planning.PlanLifecycleResult;
import org.wwz.ai.domain.agent.runtime.tool.common.planning.PlanLifecycleService;

import java.util.ArrayList;
import java.util.List;

/**
 * PlanLifecycleService 生命周期回归测试。
 */
public class PlanLifecycleServiceTest {

    private final PlanLifecycleService service = new PlanLifecycleService();

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectEmptyPlanOnCreate() {
        service.create("空计划", List.of());
    }

    @Test
    public void shouldActivateFirstStepOnCreateAndAutoAdvanceWhenMarkingCompleted() {
        PlanLifecycleResult created = service.create("普通 replan", List.of("步骤一", "步骤二"));

        Assert.assertEquals("步骤一", created.getCurrentStep());
        Assert.assertEquals(Integer.valueOf(0), created.getCurrentStepIndex());
        Assert.assertEquals(List.of("in_progress", "not_started"), created.getPlan().getStepStatus());
        Assert.assertTrue(created.getAutoAdvanced());
        Assert.assertFalse(created.getAutoFinished());

        PlanLifecycleResult advanced = service.markStep(created.getPlan(), 0, "completed", "已完成");

        Assert.assertEquals(List.of("completed", "in_progress"), advanced.getPlan().getStepStatus());
        Assert.assertEquals(List.of("已完成", ""), advanced.getPlan().getNotes());
        Assert.assertEquals("步骤二", advanced.getCurrentStep());
        Assert.assertEquals(Integer.valueOf(1), advanced.getCurrentStepIndex());
        Assert.assertTrue(advanced.getAutoAdvanced());
        Assert.assertFalse(advanced.getAutoFinished());
    }

    @Test
    public void shouldFreezeCompletedPrefixWhenUpdatingRemainingSteps() {
        PlanLifecycleResult created = service.create("普通 replan", List.of("步骤一", "步骤二", "步骤三"));
        PlanLifecycleResult advanced = service.markStep(created.getPlan(), 0, "completed", "首步完成");

        PlanLifecycleResult updated = service.update(advanced.getPlan(), "重排后的计划", List.of("新步骤A", "新步骤B"));

        Assert.assertEquals("重排后的计划", updated.getPlan().getTitle());
        Assert.assertEquals(List.of("步骤一", "新步骤A", "新步骤B"), updated.getPlan().getSteps());
        Assert.assertEquals(List.of("completed", "in_progress", "not_started"), updated.getPlan().getStepStatus());
        Assert.assertEquals(List.of("首步完成", "", ""), updated.getPlan().getNotes());
        Assert.assertEquals("新步骤A", updated.getCurrentStep());
        Assert.assertTrue(updated.getAutoAdvanced());
        Assert.assertFalse(updated.getAutoFinished());
    }

    @Test
    public void shouldRepairMissingCurrentStepOrFailFast() {
        Plan repairable = Plan.builder()
                .title("repairable")
                .steps(new ArrayList<>(List.of("已完成步骤", "待执行步骤")))
                .stepStatus(new ArrayList<>(List.of("completed", "not_started")))
                .notes(new ArrayList<>(List.of("", "")))
                .build();

        PlanLifecycleResult repaired = service.ensureExecutable(repairable);

        Assert.assertEquals("待执行步骤", repaired.getCurrentStep());
        Assert.assertEquals(Integer.valueOf(1), repaired.getCurrentStepIndex());
        Assert.assertEquals(List.of("completed", "in_progress"), repaired.getPlan().getStepStatus());
        Assert.assertTrue(repaired.getAutoAdvanced());

        Plan broken = Plan.builder()
                .title("broken")
                .steps(new ArrayList<>(List.of("步骤一", "步骤二")))
                .stepStatus(new ArrayList<>(List.of("completed", "blocked")))
                .notes(new ArrayList<>(List.of("", "")))
                .build();

        try {
            service.ensureExecutable(broken);
            Assert.fail("应当在缺失当前步骤且无法修复时快速失败");
        } catch (IllegalStateException e) {
            Assert.assertTrue(e.getMessage().contains("current step"));
        }
    }

    @Test
    public void shouldAutoFinishWhenFinalStepCompleted() {
        PlanLifecycleResult created = service.create("收口计划", List.of("最后一步"));

        PlanLifecycleResult finished = service.markStep(created.getPlan(), 0, "completed", "全部完成");

        Assert.assertTrue(finished.getAutoFinished());
        Assert.assertFalse(finished.getAutoAdvanced());
        Assert.assertEquals(List.of("completed"), finished.getPlan().getStepStatus());
        Assert.assertTrue(service.isAllStepsCompleted(finished.getPlan()));
        Assert.assertEquals("", finished.getCurrentStep());
        Assert.assertNull(finished.getCurrentStepIndex());
    }
}
