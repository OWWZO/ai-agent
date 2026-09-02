package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.prompt.AgentPrompt;
import org.wwz.ai.domain.agent.runtime.prompt.PlanSolvePrompt;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentDefinition;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentRegistry;

public class PlanSolvePromptOrchestrationTest {

    @Test
    public void ensureOrchestrationIsIdempotentAndUsesV4() {
        String once = PlanSolvePrompt.ensureOrchestration("");
        String twice = PlanSolvePrompt.ensureOrchestration(once);
        Assert.assertEquals(once, twice);
        Assert.assertTrue(once.contains(PlanSolvePrompt.ORCHESTRATION_MARKER));
        Assert.assertTrue(once.contains("规模门控"));
        Assert.assertFalse(once.contains("## 8. Plan Mode"));
        Assert.assertEquals(1, once.split(PlanSolvePrompt.ORCHESTRATION_MARKER, -1).length - 1);
    }

    @Test
    public void ensureOrchestrationReplacesLegacyV3Block() {
        String legacy = "# Plan-Execute 主代理职责 (PLAN_SOLVE_ORCHESTRATION_V3)\n- old\n";
        String upgraded = PlanSolvePrompt.ensureOrchestration(legacy);
        Assert.assertTrue(upgraded.contains(PlanSolvePrompt.ORCHESTRATION_MARKER));
        Assert.assertFalse(upgraded.contains("PLAN_SOLVE_ORCHESTRATION_V3"));
        Assert.assertTrue(upgraded.contains("协调者"));
    }

    @Test
    public void planSolveBaseIsOrchestrationNotReact() {
        String plan = AgentPrompt.composePlanSolveSystemPrompt();
        Assert.assertTrue(plan.contains(PlanSolvePrompt.ORCHESTRATION_MARKER));
        Assert.assertTrue(plan.contains(AgentPrompt.USER_FACING_REPLY_CONTRACT_MARKER));
        Assert.assertFalse(plan.contains("你是 Reactor，专注深度调研与数据分析"));
        Assert.assertTrue(plan.startsWith("# Plan-Execute 主代理职责")
                || plan.contains("# Plan-Execute 主代理职责"));
    }

    @Test
    public void subAgentSharesReactBaseWithCoordinatorFacing() {
        String sub = AgentPrompt.composeSubAgentSystemPrompt("专属指令");
        Assert.assertTrue(sub.contains("你是 Reactor，专注深度调研与数据分析"));
        Assert.assertTrue(sub.contains(AgentPrompt.COORDINATOR_FACING_REPLY_CONTRACT_MARKER));
        Assert.assertFalse(sub.contains(AgentPrompt.USER_FACING_REPLY_CONTRACT_MARKER));
        Assert.assertTrue(sub.contains("# Subagent directive"));
        Assert.assertTrue(sub.contains("专属指令"));
        Assert.assertFalse(sub.contains("执行型子代理（Worker）"));
    }

    @Test
    public void reactBaseKeepsUserFacingWhenEnsured() {
        String react = AgentPrompt.ensureUserFacingReplyContract(AgentPrompt.REACT_SYSTEM_PROMPT);
        Assert.assertTrue(react.contains(AgentPrompt.USER_FACING_REPLY_CONTRACT_MARKER));
        Assert.assertFalse(react.contains(AgentPrompt.COORDINATOR_FACING_REPLY_CONTRACT_MARKER));
        Assert.assertFalse(react.contains(PlanSolvePrompt.ORCHESTRATION_MARKER));
    }

    @Test
    public void generalPurposeDirectiveIsTypeSupplementOnly() {
        SubAgentRegistry registry = new SubAgentRegistry();
        SubAgentDefinition def = registry.require(SubAgentRegistry.TYPE_GENERAL_PURPOSE);
        Assert.assertTrue(def.getSystemPrompt().contains("通用调研/分析执行补充"));
        Assert.assertTrue(def.getWhenToUse().contains("Deep Search"));
    }
}
