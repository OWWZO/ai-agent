package org.wwz.ai.domain.agent.runtime.planmode;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;
import org.wwz.ai.domain.agent.runtime.tool.ToolObservationSerializer;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.TaskToolNames;
import org.wwz.ai.domain.agent.runtime.util.StringUtil;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ExitPlanMode observation 与客户端卡片载荷的统一构造。
 */
public final class PlanApprovalObservationSupport {

    private PlanApprovalObservationSupport() {
    }

    public static String buildWaitingObservation(String planContent, String approvalId) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("message", "Waiting for user to approve ExitPlanMode plan.");
        fields.put("status", "waiting_user_input");
        fields.put("approvalId", approvalId);
        fields.put("plan", planContent);
        ToolResultPayload payload = ToolResultPayload.okData(TaskToolNames.EXIT_PLAN_MODE, fields);
        return ToolObservationSerializer.serializeSuccess(payload.getLlmData());
    }

    public static String resolveExitPlanToolCallId(List<Message> messages, String preferredToolCallId) {
        if (StringUtils.isNotBlank(preferredToolCallId)) {
            return preferredToolCallId.trim();
        }
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        Set<String> answeredIds = new HashSet<>();
        for (Message message : messages) {
            if (message == null || message.getRole() != RoleType.TOOL || StringUtils.isBlank(message.getToolCallId())) {
                continue;
            }
            if (StringUtils.defaultString(message.getContent()).contains("waiting_user_input")) {
                continue;
            }
            answeredIds.add(message.getToolCallId());
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);
            if (message == null || message.getRole() != RoleType.ASSISTANT
                    || message.getToolCalls() == null || message.getToolCalls().isEmpty()) {
                continue;
            }
            for (ToolCall toolCall : message.getToolCalls()) {
                if (toolCall == null || toolCall.getFunction() == null) {
                    continue;
                }
                if (!TaskToolNames.EXIT_PLAN_MODE.equals(toolCall.getFunction().getName())) {
                    continue;
                }
                if (StringUtils.isNotBlank(toolCall.getId()) && answeredIds.contains(toolCall.getId())) {
                    continue;
                }
                if (StringUtils.isBlank(toolCall.getId())) {
                    // 兼容少数 OpenAI-compatible 网关丢失 tool_call_id 的响应；先修复
                    // assistant tool call，再用同一 ID 写入 waiting/decision observation。
                    toolCall.setId(StringUtil.getUUID());
                }
                return toolCall.getId();
            }
        }
        return null;
    }

    public static boolean hasToolResult(List<Message> messages, String toolCallId) {
        if (messages == null || StringUtils.isBlank(toolCallId)) {
            return false;
        }
        for (Message message : messages) {
            if (message != null
                    && message.getRole() == RoleType.TOOL
                    && toolCallId.equals(message.getToolCallId())) {
                return true;
            }
        }
        return false;
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
        map.put("persistenceStatus", record.getStatus());
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
