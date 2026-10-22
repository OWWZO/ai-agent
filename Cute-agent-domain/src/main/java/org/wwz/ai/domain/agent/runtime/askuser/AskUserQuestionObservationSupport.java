package org.wwz.ai.domain.agent.runtime.askuser;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.tool.ToolObservationSerializer;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.yield.PendingYieldToolCalls;
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

    public static String buildWaitingObservation(List<Map<String, Object>> questions, String questionId) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("message", "Waiting for user to answer AskUserQuestion.");
        fields.put("status", "waiting_user_input");
        fields.put("questions", questions == null ? List.of() : questions);
        fields.put("questionId", questionId);
        ToolResultPayload payload = ToolResultPayload.okData(AskUserQuestionTool.NAME, fields);
        return ToolObservationSerializer.serializeSuccess(payload.getLlmData());
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

    /**
     * 从后往前找未配对（或仅有 waiting 占位）的 AskUserQuestion toolCallId。
     */
    public static String resolveAskUserToolCallId(List<Message> messages, String preferredToolCallId) {
        return PendingYieldToolCalls.resolve(messages, preferredToolCallId,
                AskUserQuestionTool.NAME, false);
    }

    public static boolean hasToolResult(List<Message> messages, String toolCallId) {
        return PendingYieldToolCalls.hasToolResult(messages, toolCallId);
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
        map.put("persistenceStatus", record.getStatus());
        map.put("questions", record.getQuestions());
        if (record.getAnswers() != null) {
            map.put("answers", record.getAnswers());
        }
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
