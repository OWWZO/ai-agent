package org.wwz.ai.application.agent.askuser;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.runtime.askuser.AskUserQuestionObservationSupport;
import org.wwz.ai.domain.agent.runtime.askuser.IUserQuestionRepository;
import org.wwz.ai.domain.agent.runtime.askuser.UserQuestionRecord;
import org.wwz.ai.domain.agent.runtime.askuser.UserQuestionStatuses;
import org.wwz.ai.types.agent.visitor.VisitorRequestContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 用户回答 AskUserQuestion：CAS 写库并返回 resumeRequestId（不启动 Run B）。
 */
@Service
@RequiredArgsConstructor
public class AskUserQuestionApplicationService {

    private final IUserQuestionRepository userQuestionRepository;

    public Map<String, Object> answer(String questionId, Map<String, String> answers) {
        if (StringUtils.isBlank(questionId)) {
            throw new IllegalArgumentException("questionId 不能为空");
        }
        String visitorId = VisitorRequestContext.currentVisitorId();
        UserQuestionRecord existing = userQuestionRepository.findByQuestionId(questionId.trim())
                .orElse(null);
        if (existing == null) {
            return rejected(questionId, "问题不存在");
        }
        if (StringUtils.isNotBlank(existing.getVisitorId())
                && StringUtils.isNotBlank(visitorId)
                && !existing.getVisitorId().equals(visitorId)) {
            return rejected(questionId, "无权回答该问题");
        }
        if (UserQuestionStatuses.RESUME_PENDING.equals(existing.getStatus())
                || UserQuestionStatuses.RESUMING.equals(existing.getStatus())
                || UserQuestionStatuses.ANSWERED.equals(existing.getStatus())) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("questionId", questionId);
            result.put("accepted", true);
            result.put("idempotent", true);
            result.put("resumeRequestId", existing.getResumeRequestId());
            result.put("status", existing.getStatus());
            result.put("message", "答案已存在，请连接 resume SSE 继续");
            return result;
        }
        if (!UserQuestionStatuses.PENDING.equals(existing.getStatus())) {
            return rejected(questionId, "问题不存在或已结束（已回答/超时/取消）");
        }

        String resumeRequestId = "resume_" + UUID.randomUUID().toString().replace("-", "");
        boolean ok = userQuestionRepository.casAnswerPending(
                questionId.trim(), visitorId, answers, resumeRequestId);
        if (!ok) {
            UserQuestionRecord latest = userQuestionRepository.findByQuestionId(questionId.trim()).orElse(existing);
            if (StringUtils.isNotBlank(latest.getResumeRequestId())) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("questionId", questionId);
                result.put("accepted", true);
                result.put("idempotent", true);
                result.put("resumeRequestId", latest.getResumeRequestId());
                result.put("status", latest.getStatus());
                result.put("message", "答案已存在，请连接 resume SSE 继续");
                return result;
            }
            return rejected(questionId, "问题不存在或已结束（已回答/超时/取消）");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("questionId", questionId);
        result.put("accepted", true);
        result.put("idempotent", false);
        result.put("resumeRequestId", resumeRequestId);
        result.put("status", UserQuestionStatuses.RESUME_PENDING);
        result.put("sessionId", existing.getSessionId());
        result.put("message", "答案已提交，请连接 resume SSE 继续执行");
        return result;
    }

    public List<Map<String, Object>> listPending(String sessionId) {
        return userQuestionRepository.listOpenBySessionId(sessionId).stream()
                .map(AskUserQuestionObservationSupport::toClientPayload)
                .collect(Collectors.toList());
    }

    public Map<String, Object> cancel(String questionId, String reason) {
        String visitorId = VisitorRequestContext.currentVisitorId();
        boolean ok = userQuestionRepository.casCancel(questionId, visitorId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("questionId", questionId);
        result.put("cancelled", ok);
        result.put("reason", reason);
        return result;
    }

    public boolean hasOpenQuestion(String sessionId) {
        return userQuestionRepository.hasOpenBySessionId(sessionId);
    }

    private static Map<String, Object> rejected(String questionId, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("questionId", questionId);
        result.put("accepted", false);
        result.put("message", message);
        return result;
    }
}
