package org.wwz.ai.domain.agent.runtime.planmode;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.tool.ToolObservationSerializer;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.TaskToolNames;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ExitPlanMode observation 与客户端卡片载荷的统一构造。
 */
public final class PlanApprovalObservationSupport {

    private PlanApprovalObservationSupport() {
    }

    public static String buildDecisionObservation(PlanApprovalRecord record) {
        if (record == null) {
            return ToolObservationSerializer.serializeSuccess(Map.of(
                    "approved", false,
                    "message", "Plan approval record missing."));
        }
        PlanApprovalDecision decision = record.getDecision();
        boolean approved = decision != null && decision.isApproved();
        String planContent = record.getPlanContent();
        String planFilePath = record.getPlanFilePath();
        if (approved && decision != null && StringUtils.isNotBlank(decision.getEditedPlanContent())) {
            planContent = decision.getEditedPlanContent();
        }

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("approved", approved);
        fields.put("approvalId", record.getApprovalId());
        if (approved) {
            String restored = "default";
            PlanApprovalResumeContext resumeContext =
                    PlanApprovalResumeContext.fromJson(record.getResumeContextJson());
            if (resumeContext.getPlanMode() != null
                    && StringUtils.isNotBlank(resumeContext.getPlanMode().getPrePlanMode())) {
                restored = resumeContext.getPlanMode().getPrePlanMode();
            }
            fields.put("message", PlanModePromptInjector.buildApprovedPlanToolResult(
                    planContent, planFilePath, restored));
            fields.put("restoredMode", restored);
            fields.put("plan", planContent);
            fields.put("filePath", planFilePath);
        } else {
            String feedback = decision == null ? null : decision.getFeedback();
            fields.put("message", PlanModePromptInjector.buildRejectedPlanToolResult(feedback));
            fields.put("feedback", feedback);
            fields.put("stillInPlanMode", Boolean.TRUE);
        }
        ToolResultPayload payload = ToolResultPayload.okData(TaskToolNames.EXIT_PLAN_MODE, fields);
        return ToolObservationSerializer.serializeSuccess(payload.getLlmData());
    }

    public static Map<String, Object> toClientPayload(PlanApprovalRecord record) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("messageType", "plan_approval");
        if (record == null) {
            return map;
        }
        map.put("approvalId", record.getApprovalId());
        map.put("sessionId", record.getSessionId());
        map.put("requestId", record.getSourceRequestId());
        map.put("toolCallId", record.getToolCallId());
        map.put("planContent", record.getPlanContent());
        map.put("planFilePath", record.getPlanFilePath());
        map.put("status", toClientStatus(record.getStatus()));
        if (record.getExpiresAt() != null) {
            map.put("expiresAt", record.getExpiresAt().toString());
        }
        map.put("resumeRequestId", record.getResumeRequestId());
        if (record.getDecision() != null) {
            map.put("approved", record.getDecision().isApproved());
            map.put("feedback", record.getDecision().getFeedback());
            map.put("editedPlanContent", record.getDecision().getEditedPlanContent());
        }
        return map;
    }

    public static String toClientStatus(String status) {
        if (PlanApprovalStatuses.ANSWERED.equals(status)
                || PlanApprovalStatuses.RESUMING.equals(status)
                || PlanApprovalStatuses.RESUME_PENDING.equals(status)) {
            return "decided";
        }
        if (PlanApprovalStatuses.TIMEOUT.equals(status)) {
            return "timeout";
        }
        if (PlanApprovalStatuses.CANCELLED.equals(status) || PlanApprovalStatuses.FAILED.equals(status)) {
            return "cancelled";
        }
        return "pending";
    }
}
