package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.common.PlanningTool;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.PlanningToolOutput;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PlanningTool 回归测试。
 */
public class PlanningToolTest {

    @Test
    public void shouldEmitStructuredLifecycleOutputForOrdinaryReplan() {
        PlanningTool tool = new PlanningTool();
        tool.setCloseUpdateMode(false);

        ToolResultPayload created = (ToolResultPayload) tool.execute(command(
                "command", "create",
                "title", "普通 replan",
                "steps", List.of("步骤一", "步骤二")
        ));
        PlanningToolOutput createOutput = (PlanningToolOutput) created.getStructuredOutput();

        Assert.assertEquals("我已创建plan", created.getLlmObservation());
        Assert.assertNull(createOutput.getBeforePlan());
        Assert.assertEquals("create", createOutput.getCommand());
        Assert.assertEquals("步骤一", createOutput.getCurrentStep());
        Assert.assertEquals(Integer.valueOf(0), createOutput.getCurrentStepIndex());
        Assert.assertTrue(createOutput.getAutoAdvanced());
        Assert.assertFalse(createOutput.getAutoFinished());
        Assert.assertEquals(List.of("in_progress", "not_started"), createOutput.getAfterPlan().getStepStatus());

        ToolResultPayload marked = (ToolResultPayload) tool.execute(command(
                "command", "mark_step",
                "step_index", 0,
                "step_status", "completed",
                "step_notes", "已完成"
        ));
        PlanningToolOutput markOutput = (PlanningToolOutput) marked.getStructuredOutput();

        Assert.assertEquals("步骤一", markOutput.getBeforePlan().getCurrentStep());
        Assert.assertEquals("步骤二", markOutput.getCurrentStep());
        Assert.assertEquals(Integer.valueOf(1), markOutput.getCurrentStepIndex());
        Assert.assertEquals(List.of("completed", "in_progress"), markOutput.getAfterPlan().getStepStatus());
        Assert.assertEquals(List.of("已完成", ""), markOutput.getAfterPlan().getNotes());
        Assert.assertTrue(markOutput.getAutoAdvanced());
        Assert.assertFalse(markOutput.getAutoFinished());
    }

    @Test
    public void shouldRejectIllegalParametersWithoutMutatingPlan() {
        PlanningTool tool = new PlanningTool();
        tool.setCloseUpdateMode(false);
        tool.execute(command(
                "command", "create",
                "title", "普通 replan",
                "steps", List.of("步骤一", "步骤二")
        ));

        try {
            tool.execute(command(
                    "command", "mark_step",
                    "step_status", "completed"
            ));
            Assert.fail("缺失 step_index 时应当报错");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("step_index"));
        }

        Assert.assertEquals("步骤一", tool.getPlan().getCurrentStep());
        Assert.assertEquals(List.of("in_progress", "not_started"), tool.getPlan().getStepStatus());
    }

    @Test
    public void shouldRejectUpdateWithoutRemainingStepsAndKeepPlanUnchanged() {
        PlanningTool tool = new PlanningTool();
        tool.setCloseUpdateMode(false);
        tool.execute(command(
                "command", "create",
                "title", "普通 replan",
                "steps", List.of("步骤一", "步骤二")
        ));

        try {
            tool.execute(command(
                    "command", "update",
                    "title", "仅改标题"
            ));
            Assert.fail("update 缺失 steps 时应当报错");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("steps"));
        }

        Assert.assertEquals("普通 replan", tool.getPlan().getTitle());
        Assert.assertEquals(List.of("步骤一", "步骤二"), tool.getPlan().getSteps());
        Assert.assertEquals(List.of("in_progress", "not_started"), tool.getPlan().getStepStatus());
    }

    @Test
    public void shouldKeepCompatibilityStepAdvancementWhenCloseUpdateEnabled() {
        PlanningTool tool = new PlanningTool();
        tool.setCloseUpdateMode(true);

        ToolResultPayload created = (ToolResultPayload) tool.execute(command(
                "command", "create",
                "title", "兼容路径",
                "steps", List.of("步骤一", "步骤二")
        ));
        PlanningToolOutput createOutput = (PlanningToolOutput) created.getStructuredOutput();

        Assert.assertEquals("步骤一", createOutput.getCurrentStep());
        Assert.assertEquals(List.of("in_progress", "not_started"), tool.getPlan().getStepStatus());

        tool.stepPlan();

        Assert.assertEquals("步骤二", tool.getPlan().getCurrentStep());
        Assert.assertEquals(List.of("completed", "in_progress"), tool.getPlan().getStepStatus());
    }

    @Test
    public void shouldAcceptDisplayedOneBasedCurrentStepIndexEvenWhenValueIsStillWithinArrayRange() {
        PlanningTool tool = new PlanningTool();
        tool.setCloseUpdateMode(false);
        tool.execute(command(
                "command", "create",
                "title", "普通 replan",
                "steps", List.of("步骤一", "步骤二", "步骤三")
        ));

        tool.execute(command(
                "command", "mark_step",
                "step_index", 0,
                "step_status", "completed",
                "step_notes", "首步完成"
        ));

        ToolResultPayload secondMarked = (ToolResultPayload) tool.execute(command(
                "command", "mark_step",
                "step_index", 2,
                "step_status", "completed",
                "step_notes", "兼容展示序号"
        ));
        PlanningToolOutput secondOutput = (PlanningToolOutput) secondMarked.getStructuredOutput();

        Assert.assertEquals(List.of("completed", "completed", "in_progress"), tool.getPlan().getStepStatus());
        Assert.assertEquals(List.of("首步完成", "兼容展示序号", ""), tool.getPlan().getNotes());
        Assert.assertFalse(secondOutput.getAutoFinished());
        Assert.assertTrue(secondOutput.getAutoAdvanced());
        Assert.assertEquals(Integer.valueOf(2), secondOutput.getCurrentStepIndex());
        Assert.assertEquals("步骤三", secondOutput.getCurrentStep());
    }

    @Test
    public void shouldFailFastWhenTryingToMutateCompletedStepInOrdinaryMode() {
        PlanningTool tool = new PlanningTool();
        tool.setCloseUpdateMode(false);
        tool.execute(command(
                "command", "create",
                "title", "普通 replan",
                "steps", List.of("步骤一", "步骤二")
        ));
        tool.execute(command(
                "command", "mark_step",
                "step_index", 0,
                "step_status", "completed"
        ));

        try {
            tool.execute(command(
                    "command", "mark_step",
                    "step_index", 0,
                    "step_status", "blocked",
                    "step_notes", "不允许回写"
            ));
            Assert.fail("已完成步骤应当被冻结");
        } catch (IllegalStateException e) {
            Assert.assertTrue(e.getMessage().contains("completed"));
        }
    }

    @Test
    public void shouldAcceptDisplayedOneBasedCurrentStepIndexInOrdinaryMode() {
        PlanningTool tool = new PlanningTool();
        tool.setCloseUpdateMode(false);
        tool.execute(command(
                "command", "create",
                "title", "普通 replan",
                "steps", List.of("步骤一", "步骤二", "步骤三", "步骤四", "步骤五")
        ));

        tool.execute(command(
                "command", "mark_step",
                "step_index", 0,
                "step_status", "completed"
        ));
        tool.execute(command(
                "command", "mark_step",
                "step_index", 1,
                "step_status", "completed"
        ));
        tool.execute(command(
                "command", "mark_step",
                "step_index", 2,
                "step_status", "completed"
        ));
        tool.execute(command(
                "command", "mark_step",
                "step_index", 3,
                "step_status", "completed"
        ));

        ToolResultPayload finalMarked = (ToolResultPayload) tool.execute(command(
                "command", "mark_step",
                "step_index", 5,
                "step_status", "completed",
                "step_notes", "兼容展示序号"
        ));
        PlanningToolOutput finalOutput = (PlanningToolOutput) finalMarked.getStructuredOutput();

        Assert.assertEquals(List.of("completed", "completed", "completed", "completed", "completed"),
                tool.getPlan().getStepStatus());
        Assert.assertTrue(finalOutput.getAutoFinished());
        Assert.assertNull(finalOutput.getCurrentStepIndex());
    }

    private Map<String, Object> command(Object... kvPairs) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 0; i < kvPairs.length; i += 2) {
            params.put(String.valueOf(kvPairs[i]), kvPairs[i + 1]);
        }
        return params;
    }
}
