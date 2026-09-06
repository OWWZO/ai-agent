package org.wwz.ai.test.domain.planmode;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunView;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.runtime.planmode.PlanModeEntryPolicy;

import java.util.List;

/**
 * PlanSolve 自动进入 plan mode 的判定。
 */
public class PlanModeEntryPolicyTest {

    @Test
    public void firstPlanSolveTurnAutoEnters() {
        AgentRequest request = AgentRequest.builder()
                .requestId("req-1")
                .sessionId("s-1")
                .build();
        Assert.assertTrue(PlanModeEntryPolicy.shouldAutoEnter(request, false, false));
    }

    @Test
    public void subsequentPlanSolveTurnDoesNotAutoEnter() {
        AgentRequest request = AgentRequest.builder()
                .requestId("req-2")
                .sessionId("s-1")
                .build();
        Assert.assertFalse(PlanModeEntryPolicy.shouldAutoEnter(request, false, true));
    }

    @Test
    public void forcePlanModeEntersEvenWithPriorTurn() {
        AgentRequest request = AgentRequest.builder()
                .requestId("req-3")
                .sessionId("s-1")
                .forcePlanMode(true)
                .build();
        Assert.assertTrue(PlanModeEntryPolicy.shouldAutoEnter(request, false, true));
    }

    @Test
    public void continuationNeverAutoEnters() {
        AgentRequest request = AgentRequest.builder()
                .requestId("req-4")
                .sessionId("s-1")
                .resumeApprovalId("appr-1")
                .forcePlanMode(true)
                .build();
        Assert.assertFalse(PlanModeEntryPolicy.shouldAutoEnter(request, false, false));
    }

    @Test
    public void approvedResumeNeverAutoEnters() {
        AgentRequest request = AgentRequest.builder()
                .requestId("req-5")
                .sessionId("s-1")
                .forcePlanMode(true)
                .build();
        Assert.assertFalse(PlanModeEntryPolicy.shouldAutoEnter(request, true, false));
    }

    @Test
    public void priorPlanSolveIgnoresReactRunsAndCurrentRequest() {
        List<DialogueRunView> runs = List.of(
                DialogueRunView.builder()
                        .requestId("req-react")
                        .entryAgent(ExecutionLedgerConstants.ENTRY_AGENT_REACT)
                        .build(),
                DialogueRunView.builder()
                        .requestId("req-current")
                        .entryAgent(ExecutionLedgerConstants.ENTRY_AGENT_PLAN_SOLVE)
                        .build()
        );
        Assert.assertFalse(PlanModeEntryPolicy.hasPriorPlanSolveUserTurn(runs, "req-current"));
        Assert.assertTrue(PlanModeEntryPolicy.hasPriorPlanSolveUserTurn(runs, "req-next"));
    }
}
