package org.wwz.ai.test.domain.planmode;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.planmode.PendingPlanApproval;
import org.wwz.ai.domain.agent.runtime.planmode.PendingPlanApprovalRegistry;
import org.wwz.ai.domain.agent.runtime.planmode.PlanApprovalDecision;
import org.wwz.ai.domain.agent.runtime.planmode.PlanModePromptInjector;
import org.wwz.ai.domain.agent.runtime.planmode.PlanModeState;
import org.wwz.ai.domain.agent.runtime.planmode.PlanModeToolPolicy;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Plan Mode：状态机 + 工具门禁 + 批准挂起。
 * 不依赖 AgentContext（避开 domain 单测 logback 冲突）。
 */
public class PlanModeCchahaParityTest {

    @Test
    public void planModeInstructionsContainMustNotExecute() {
        String text = PlanModePromptInjector.PLAN_MODE_INSTRUCTIONS;
        Assert.assertTrue(text.contains("MUST NOT"));
        Assert.assertTrue(text.contains("Plan mode is active"));
        Assert.assertTrue(text.contains(PlanModePromptInjector.PLAN_MODE_INSTRUCTIONS_MARKER));
    }

    @Test
    public void exitPendingDoesNotLeavePlanModeUntilApproved() {
        PlanModeState state = PlanModeState.builder().build();
        state.enterPlanMode();
        state.requestExitWithPlan("## Plan\n1. do x", "/tmp/plan.md");
        Assert.assertTrue(state.isPlanMode());
        Assert.assertTrue(state.isExitPendingApproval());
        Assert.assertTrue(state.getPendingPlanContent().contains("do x"));

        String restored = state.exitPlanMode();
        Assert.assertEquals(PlanModeState.MODE_DEFAULT, restored);
        Assert.assertFalse(state.isPlanMode());
        Assert.assertFalse(state.isExitPendingApproval());
        Assert.assertTrue(state.isHasExitedPlanMode());
    }

    @Test
    public void toolPolicyBlocksMutatingToolsInPlanMode() {
        PlanModeState state = PlanModeState.builder().build();
        state.enterPlanMode();

        Assert.assertNotNull(PlanModeToolPolicy.denyReason(state, "code_interpreter", Map.of()));
        Assert.assertNotNull(PlanModeToolPolicy.denyReason(state, "workspace_write", Map.of("path", "src/Main.java")));
        Assert.assertNull(PlanModeToolPolicy.denyReason(state, "workspace_write", Map.of("path", ".reactor/plan.md")));
        Assert.assertNull(PlanModeToolPolicy.denyReason(state, "workspace_read", Map.of("path", "src/Main.java")));
        Assert.assertNull(PlanModeToolPolicy.denyReason(state, "Agent", Map.of()));
        Assert.assertNull(PlanModeToolPolicy.denyReason(state, "ExitPlanMode", Map.of()));
    }

    @Test
    public void toolPolicyAllowsAllWhenNotInPlanMode() {
        PlanModeState state = PlanModeState.builder().build();
        Assert.assertNull(PlanModeToolPolicy.denyReason(state, "workspace_write", Map.of("path", "a.txt")));
    }

    @Test
    public void approvalRegistryApproveWakesFuture() throws Exception {
        PendingPlanApprovalRegistry registry = new PendingPlanApprovalRegistry();
        PendingPlanApproval pending = registry.create("sess", "req", "tc1", "## P", null, 5000L);

        CompletableFuture<PlanApprovalDecision> waiter = CompletableFuture.supplyAsync(() -> {
            try {
                return registry.awaitDecision(pending);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        Thread.sleep(50);
        Assert.assertTrue(registry.approve(pending.getApprovalId(), null, null));
        PlanApprovalDecision decision = waiter.get(3, TimeUnit.SECONDS);
        Assert.assertTrue(decision.isApproved());
    }

    @Test
    public void planModeFilterStripsWriteToolsEvenForGeneralPurpose() {
        // 不构造 ToolCollection（domain 单测 logback 冲突），只验证 disallowed 逻辑入口
        // 完整 filter 在 app 模块集成测；此处验证 policy + state 组合语义
        PlanModeState state = PlanModeState.builder().build();
        state.enterPlanMode();
        Assert.assertNotNull(PlanModeToolPolicy.denyReason(state, "workspace_write", Map.of("path", "a.java")));
        Assert.assertNull(PlanModeToolPolicy.denyReason(state, "workspace_read", Map.of("path", "a.java")));
    }

    @Test
    public void approvalRegistryRejectWakesFuture() throws Exception {
        PendingPlanApprovalRegistry registry = new PendingPlanApprovalRegistry();
        PendingPlanApproval pending = registry.create("sess", "req", "tc1", "## P", null, 5000L);

        CompletableFuture<PlanApprovalDecision> waiter = CompletableFuture.supplyAsync(() -> {
            try {
                return registry.awaitDecision(pending);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        Thread.sleep(50);
        Assert.assertTrue(registry.reject(pending.getApprovalId(), "need more detail"));
        PlanApprovalDecision decision = waiter.get(3, TimeUnit.SECONDS);
        Assert.assertFalse(decision.isApproved());
        Assert.assertTrue(decision.getFeedback().contains("more detail"));
    }
}
