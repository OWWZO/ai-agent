package org.wwz.ai.domain.agent.runtime.askuser;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;
import org.wwz.ai.domain.agent.runtime.tool.ToolObservationSerializer;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.AskUserQuestionTool;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        if (StringUtils.isNotBlank(preferredToolCallId)) {
            return preferredToolCallId.trim();
        }
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        Set<String> answeredIds = new HashSet<>();
        for (Message message : messages) {
            if (message == null || message.getRole() != RoleType.TOOL) {
                continue;
            }
            if (StringUtils.isBlank(message.getToolCallId())) {
                continue;
            }
            String content = StringUtils.defaultString(message.getContent());
            if (content.contains("waiting_user_input")) {
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
                if (!AskUserQuestionTool.NAME.equals(toolCall.getFunction().getName())) {
                    continue;
                }
                if (StringUtils.isBlank(toolCall.getId()) || answeredIds.contains(toolCall.getId())) {
                    continue;
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
