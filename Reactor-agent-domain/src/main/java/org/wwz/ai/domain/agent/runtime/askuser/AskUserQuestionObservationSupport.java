package org.wwz.ai.domain.agent.runtime.askuser;

import org.wwz.ai.domain.agent.runtime.tool.ToolObservationSerializer;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.AskUserQuestionTool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AskUserQuestion observation 与客户端卡片载荷的统一构造。
 */
public final class AskUserQuestionObservationSupport {

    private AskUserQuestionObservationSupport() {
    }

    public static String buildAnswerObservation(List<Map<String, Object>> questions,
                                               Map<String, String> answers,
                                               String questionId) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("message", "User has answered your questions. Continue with the answers in mind.");
        fields.put("questions", questions == null ? List.of() : questions);
        fields.put("answers", answers == null ? Map.of() : answers);
        fields.put("questionId", questionId);
        ToolResultPayload payload = ToolResultPayload.okData(AskUserQuestionTool.NAME, fields);
        return ToolObservationSerializer.serializeSuccess(payload.getLlmData());
    }

    public static Map<String, Object> toClientPayload(UserQuestionRecord record) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("messageType", "ask_user_question");
        if (record == null) {
            return map;
        }
        map.put("questionId", record.getQuestionId());
        map.put("sessionId", record.getSessionId());
        map.put("requestId", record.getSourceRequestId());
        map.put("toolCallId", record.getToolCallId());
        map.put("status", toClientStatus(record.getStatus()));
        map.put("questions", record.getQuestions());
        if (record.getExpiresAt() != null) {
            map.put("expiresAt", record.getExpiresAt().toString());
        }
        map.put("resumeRequestId", record.getResumeRequestId());
        return map;
    }

    public static String toClientStatus(String status) {
        if (UserQuestionStatuses.ANSWERED.equals(status) || UserQuestionStatuses.RESUMING.equals(status)
                || UserQuestionStatuses.RESUME_PENDING.equals(status)) {
            return "answered";
        }
        if (UserQuestionStatuses.TIMEOUT.equals(status)) {
            return "timeout";
        }
        if (UserQuestionStatuses.CANCELLED.equals(status) || UserQuestionStatuses.FAILED.equals(status)) {
            return "cancelled";
        }
        return "pending";
    }
}
