package org.wwz.ai.test.application.agent.planmode;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.application.agent.planmode.PlanApprovalResumeApplicationService;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.planmode.PlanApprovalDecision;
import org.wwz.ai.domain.agent.runtime.planmode.PlanApprovalRecord;

import java.util.List;

public class PlanApprovalResumeApplicationServiceTest {

    @Test
    public void appendDecisionObservationRepairsMissingToolCallId() {
        ToolCall exitPlanMode = ToolCall.builder()
                .id("")
                .type("function")
                .function(ToolCall.Function.builder()
                        .name("ExitPlanMode")
                        .arguments("{}")
                        .build())
                .build();
        List<Message> working = List.of(
                Message.fromToolCalls("submit plan", List.of(exitPlanMode)),
                Message.toolMessage(
                        "{\"tool\":\"ExitPlanMode\",\"status\":\"waiting_user_input\"}",
                        "",
                        null)
        );
        PlanApprovalRecord record = PlanApprovalRecord.builder()
                .approvalId("pa_1")
                .planContent("## Approved plan")
                .decision(PlanApprovalDecision.builder().approved(true).build())
                .build();

        List<Message> result = PlanApprovalResumeApplicationService.appendDecisionObservation(
                working, record);

        Assert.assertEquals(2, result.size());
        Message observation = result.get(1);
        Assert.assertNotNull(observation.getToolCallId());
        Assert.assertFalse(observation.getToolCallId().isBlank());
        Assert.assertTrue(observation.getContent().contains("Approved Plan"));
    }
}
