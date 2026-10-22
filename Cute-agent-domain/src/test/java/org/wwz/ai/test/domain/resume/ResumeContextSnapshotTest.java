package org.wwz.ai.test.domain.resume;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.command.AgentExecutionCommand;
import org.wwz.ai.domain.agent.runtime.planmode.PlanApprovalResumeContext;
import org.wwz.ai.domain.agent.runtime.planmode.PlanModeState;
import org.wwz.ai.domain.agent.runtime.askuser.UserQuestionResumeContext;

/**
 * 续跑瘦快照的序列化契约与 plan mode 恢复。
 * <p>
 * resumeContextJson 是持久化字段，快照的字段集合与 JSON 形状一旦漂移，
 * 已落库的续跑记录就无法恢复。这组测试锁住 UserQuestionResumeContext 与
 * PlanApprovalResumeContext 的往返，以及 plan mode 状态的逐字段恢复。
 */
public class ResumeContextSnapshotTest {

    @Test
    public void userQuestionSnapshotSurvivesJsonRoundTrip() {
        UserQuestionResumeContext original = UserQuestionResumeContext.builder()
                .entryAgent("plan_solve")
                .agentType(1)
                .model("gpt-4o-mini")
                .thinking(Boolean.TRUE)
                .thinkingEffort("high")
                .planMode(planMode())
                .build();

        UserQuestionResumeContext parsed =
                UserQuestionResumeContext.fromJson(UserQuestionResumeContext.toJson(original));

        Assert.assertEquals("plan_solve", parsed.getEntryAgent());
        Assert.assertEquals(Integer.valueOf(1), parsed.getAgentType());
        Assert.assertEquals("gpt-4o-mini", parsed.getModel());
        Assert.assertEquals(Boolean.TRUE, parsed.getThinking());
        Assert.assertEquals("high", parsed.getThinkingEffort());
        assertPlanModeFields(parsed.getPlanMode());
    }

    @Test
    public void planApprovalSnapshotSurvivesJsonRoundTrip() {
        PlanApprovalResumeContext original = PlanApprovalResumeContext.builder()
                .entryAgent("plan_solve")
                .agentType(1)
                .model("gpt-4o-mini")
                .thinking(Boolean.FALSE)
                .thinkingEffort("low")
                .planMode(planMode())
                .build();

        PlanApprovalResumeContext parsed =
                PlanApprovalResumeContext.fromJson(PlanApprovalResumeContext.toJson(original));

        Assert.assertEquals("plan_solve", parsed.getEntryAgent());
        Assert.assertEquals(Integer.valueOf(1), parsed.getAgentType());
        Assert.assertEquals("gpt-4o-mini", parsed.getModel());
        Assert.assertEquals(Boolean.FALSE, parsed.getThinking());
        Assert.assertEquals("low", parsed.getThinkingEffort());
        assertPlanModeFields(parsed.getPlanMode());
    }

    @Test
    public void blankJsonYieldsEmptySnapshotNotNnull() {
        Assert.assertNotNull(UserQuestionResumeContext.fromJson(null));
        Assert.assertNotNull(UserQuestionResumeContext.fromJson("  "));
        Assert.assertNotNull(PlanApprovalResumeContext.fromJson(null));
        Assert.assertNotNull(PlanApprovalResumeContext.fromJson("  "));
    }

    @Test
    public void planApprovalSnapshotIsDerivedFromSharedSnapshot() {
        AgentContext context = AgentContext.builder()
                .requestId("req-snap")
                .sessionId("session-snap")
                .userId("user-1")
                .query("q")
                .model("gpt-4o-mini")
                .build();
        context.requirePlanModeState().enterPlanMode();
        context.getPlanModeState().setPlan("计划正文", ".reactor/plan.md");
        AgentExecutionCommand request = AgentExecutionCommand.builder()
                .requestId("req-snap")
                .sessionId("session-snap")
                .userId("user-1")
                .query("q")
                .agentType(1)
                .model("gpt-4o-mini")
                .thinking(Boolean.TRUE)
                .thinkingEffort("high")
                .build();

        PlanApprovalResumeContext derived =
                PlanApprovalResumeContext.from(context, request, "plan_solve");

        Assert.assertEquals("plan_solve", derived.getEntryAgent());
        Assert.assertEquals(Integer.valueOf(1), derived.getAgentType());
        Assert.assertEquals("gpt-4o-mini", derived.getModel());
        Assert.assertEquals(Boolean.TRUE, derived.getThinking());
        Assert.assertEquals("high", derived.getThinkingEffort());
        Assert.assertNotNull(derived.getPlanMode());
        Assert.assertEquals(PlanModeState.MODE_PLAN, derived.getPlanMode().getMode());
        Assert.assertEquals("计划正文", derived.getPlanMode().getPlanContent());
    }

    @Test
    public void applyPlanModeToRestoresEveryField() {
        AgentContext target = AgentContext.builder()
                .requestId("req-restore")
                .sessionId("session-restore")
                .build();

        UserQuestionResumeContext.builder().planMode(planMode()).build()
                .applyPlanModeTo(target);

        PlanModeState state = target.getPlanModeState();
        Assert.assertEquals(PlanModeState.MODE_PLAN, state.getMode());
        Assert.assertEquals("acceptEdits", state.getPrePlanMode());
        Assert.assertEquals("计划正文", state.getPlanContent());
        Assert.assertEquals(".reactor/plan.md", state.getPlanFilePath());
        Assert.assertTrue(state.isHasExitedPlanMode());
        Assert.assertTrue(state.isExitPendingApproval());
        Assert.assertEquals("待批准计划", state.getPendingPlanContent());
        Assert.assertEquals(3, state.getStepsSincePlanAttachment());
        Assert.assertEquals(2, state.getPlanAttachmentCount());
        Assert.assertTrue(state.isNeedsPlanModeExitAttachment());
    }

    @Test
    public void applyPlanModeToIsNoopForNullOrMissingPlanMode() {
        AgentContext target = AgentContext.builder()
                .requestId("req-noop")
                .sessionId("session-noop")
                .build();
        PlanModeState before = target.getPlanModeState();

        UserQuestionResumeContext.builder().build().applyPlanModeTo(target);
        UserQuestionResumeContext.builder().planMode(planMode()).build().applyPlanModeTo(null);

        Assert.assertSame(before, target.getPlanModeState());
    }

    @Test
    public void blankModeFallsBackToDefault() {
        AgentContext target = AgentContext.builder()
                .requestId("req-blank-mode")
                .sessionId("session-blank-mode")
                .build();

        UserQuestionResumeContext.builder()
                .planMode(UserQuestionResumeContext.PlanModeSnapshot.builder()
                        .mode("  ")
                        .build())
                .build()
                .applyPlanModeTo(target);

        Assert.assertEquals(PlanModeState.MODE_DEFAULT, target.getPlanModeState().getMode());
    }

    private static UserQuestionResumeContext.PlanModeSnapshot planMode() {
        return UserQuestionResumeContext.PlanModeSnapshot.builder()
                .mode(PlanModeState.MODE_PLAN)
                .prePlanMode("acceptEdits")
                .planContent("计划正文")
                .planFilePath(".reactor/plan.md")
                .hasExitedPlanMode(true)
                .exitPendingApproval(true)
                .pendingPlanContent("待批准计划")
                .stepsSincePlanAttachment(3)
                .planAttachmentCount(2)
                .needsPlanModeExitAttachment(true)
                .build();
    }

    private static void assertPlanModeFields(UserQuestionResumeContext.PlanModeSnapshot snapshot) {
        Assert.assertNotNull(snapshot);
        Assert.assertEquals(PlanModeState.MODE_PLAN, snapshot.getMode());
        Assert.assertEquals("acceptEdits", snapshot.getPrePlanMode());
        Assert.assertEquals("计划正文", snapshot.getPlanContent());
        Assert.assertEquals(".reactor/plan.md", snapshot.getPlanFilePath());
        Assert.assertTrue(snapshot.isHasExitedPlanMode());
        Assert.assertTrue(snapshot.isExitPendingApproval());
        Assert.assertEquals("待批准计划", snapshot.getPendingPlanContent());
        Assert.assertEquals(3, snapshot.getStepsSincePlanAttachment());
        Assert.assertEquals(2, snapshot.getPlanAttachmentCount());
        Assert.assertTrue(snapshot.isNeedsPlanModeExitAttachment());
    }
}
