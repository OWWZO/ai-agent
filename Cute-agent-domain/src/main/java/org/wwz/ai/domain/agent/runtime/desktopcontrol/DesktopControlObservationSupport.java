package org.wwz.ai.domain.agent.runtime.desktopcontrol;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.tool.ToolObservationSerializer;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.yield.PendingYieldToolCalls;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.RequestDesktopControlTool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DesktopControlObservationSupport {

    private DesktopControlObservationSupport() {
    }

    public static String buildWaitingObservation(String reason, String controlId) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("message", "桌面控制已交给用户。reason=" + safeReason(reason) + " controlId=" + safeId(controlId));
        fields.put("status", "waiting_user_input");
        fields.put("reason", safeReason(reason));
        fields.put("controlId", safeId(controlId));
        ToolResultPayload payload = ToolResultPayload.okData(RequestDesktopControlTool.NAME, fields);
        return ToolObservationSerializer.serializeSuccess(payload.getLlmData());
    }

    public static String buildCompletedObservation(String reason, String controlId) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("message", "用户已完成桌面操作。reason=" + safeReason(reason));
        fields.put("status", "completed");
        fields.put("reason", safeReason(reason));
        fields.put("controlId", safeId(controlId));
        ToolResultPayload payload = ToolResultPayload.okData(RequestDesktopControlTool.NAME, fields);
        return ToolObservationSerializer.serializeSuccess(payload.getLlmData());
    }

    public static String resolveDesktopToolCallId(List<Message> messages, String preferredToolCallId) {
        return PendingYieldToolCalls.resolve(messages, preferredToolCallId,
                RequestDesktopControlTool.NAME, false);
    }

    public static boolean hasToolResult(List<Message> messages, String toolCallId) {
        return PendingYieldToolCalls.hasToolResult(messages, toolCallId);
    }

    public static Map<String, Object> toClientPayload(DesktopControlRecord record) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("messageType", "desktop_control");
        if (record == null) {
            return map;
        }
        map.put("controlId", record.getControlId());
        map.put("sessionId", record.getSessionId());
        map.put("requestId", record.getSourceRequestId());
        map.put("toolCallId", record.getToolCallId());
        map.put("reason", record.getReason());
        map.put("streamUrl", record.getStreamUrl());
        map.put("holdUntil", record.getHoldUntil());
        map.put("status", toClientStatus(record.getStatus()));
        map.put("persistenceStatus", record.getStatus());
        map.put("resumeRequestId", record.getResumeRequestId());
        return map;
    }

    public static Map<String, Object> toReplayPayload(DesktopControlRecord record) {
        Map<String, Object> map = toClientPayload(record);
        map.remove("streamUrl");
        return map;
    }

    public static String toClientStatus(String status) {
        if (DesktopControlStatuses.COMPLETED.equals(status)
                || DesktopControlStatuses.RESUMING.equals(status)
                || DesktopControlStatuses.RESUME_PENDING.equals(status)) {
            return "completed";
        }
        if (DesktopControlStatuses.CANCELLED.equals(status) || DesktopControlStatuses.FAILED.equals(status)) {
            return "cancelled";
        }
        return "pending";
    }

    private static String safeReason(String reason) {
        return StringUtils.defaultString(reason);
    }

    private static String safeId(String controlId) {
        return StringUtils.defaultString(controlId);
    }
}
