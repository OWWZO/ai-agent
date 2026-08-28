package org.wwz.ai.test.domain.planmode;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.planmode.PlanApprovalDecision;
import org.wwz.ai.domain.agent.runtime.planmode.PlanApprovalObservationSupport;
import org.wwz.ai.domain.agent.runtime.planmode.PlanApprovalRecord;
import org.wwz.ai.domain.agent.runtime.planmode.PlanApprovalStatuses;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;

import java.util.List;
import java.util.Map;

public class PlanApprovalObservationSupportTest {

    @Test
    public void waitingObservationContainsApprovalPayload() {
        String observation = PlanApprovalObservationSupport.buildWaitingObservation(
                "## Plan\n1. collect data", "pa_waiting");

        Assert.assertNotNull(observation);
        Assert.assertFalse(observation.isBlank());
        Assert.assertTrue(observation.contains("pa_waiting"));
        Assert.assertTrue(observation.contains("waiting_user_input"));
    }

    @Test
    public void missingToolCallIdIsRepairedForExitPlanMode() {
        ToolCall exitPlanMode = ToolCall.builder()
                .id("")
                .function(ToolCall.Function.builder().name("ExitPlanMode").arguments("{}").build())
                .build();
        Message assistant = Message.fromToolCalls("submit plan", List.of(exitPlanMode));

        String toolCallId = PlanApprovalObservationSupport.resolveExitPlanToolCallId(
                List.of(assistant), null);

        Assert.assertNotNull(toolCallId);
        Assert.assertFalse(toolCallId.isBlank());
        Assert.assertEquals(toolCallId, exitPlanMode.getId());
    }

    @Test
    public void approvedObservationContainsPlan() {
        PlanApprovalRecord record = PlanApprovalRecord.builder()
                .approvalId("pa_1")
                .planContent("## Plan\n1. do A")
                .planFilePath(".reactor/plan.md")
                .toolCallId("tc1")
                .status(PlanApprovalStatuses.RESUME_PENDING)
                .decision(PlanApprovalDecision.builder().approved(true).build())
                .build();
        String obs = PlanApprovalObservationSupport.buildDecisionObservation(record);
        Assert.assertTrue(obs.contains("approved") || obs.contains("Plan") || obs.contains("do A"));
    }

    @Test
    public void clientPayloadPending() {
        PlanApprovalRecord record = PlanApprovalRecord.builder()
                .approvalId("pa_2")
                .sessionId("s1")
                .sourceRequestId("r1")
                .planContent("body")
                .status(PlanApprovalStatuses.PENDING)
                .build();
        Map<String, Object> payload = PlanApprovalObservationSupport.toClientPayload(record);
        Assert.assertEquals("plan_approval", payload.get("messageType"));
        Assert.assertEquals("pa_2", payload.get("approvalId"));
        Assert.assertEquals("pending", payload.get("status"));
        Assert.assertEquals(PlanApprovalStatuses.PENDING, payload.get("persistenceStatus"));
    }
}
