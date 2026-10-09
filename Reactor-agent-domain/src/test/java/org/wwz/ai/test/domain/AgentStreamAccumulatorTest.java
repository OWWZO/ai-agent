package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.stream.AgentStreamAccumulator;

import java.util.List;
import java.util.ArrayList;

public class AgentStreamAccumulatorTest {

    @Test
    public void keepsIndependentMessageOrderAndRenewsTaskState() {
        AgentStreamAccumulator accumulator = new AgentStreamAccumulator();

        Assert.assertEquals(Integer.valueOf(1), accumulator.getAndIncrOrder("tool_call"));
        Assert.assertEquals(Integer.valueOf(2), accumulator.getAndIncrOrder("tool_call"));
        Assert.assertEquals(Integer.valueOf(1), accumulator.getAndIncrOrder("tool_result"));

        String firstTask = accumulator.getTaskId();
        accumulator.getTaskOrder().incrementAndGet();
        String secondTask = accumulator.renewTaskId();

        Assert.assertNotEquals(firstTask, secondTask);
        Assert.assertEquals(1, accumulator.getTaskOrder().get());
    }

    @Test
    public void aggregatesPlanAndNestedTaskStateOnce() {
        AgentStreamAccumulator accumulator = new AgentStreamAccumulator();

        Assert.assertTrue(accumulator.isInitPlan());
        Assert.assertFalse(accumulator.isInitPlan());
        accumulator.setPlannerRoundId("round-1");
        accumulator.setResultMapTask(new ArrayList<>(List.of("task-1")));
        accumulator.setResultMapSubTask("tool-1");

        Assert.assertEquals("round-1", accumulator.getPlannerRoundId());
        Assert.assertEquals(1, accumulator.getResulMapTask().size());
        Assert.assertEquals(List.of("task-1", "tool-1"), accumulator.getResulMapTask().get(0));
    }
}
