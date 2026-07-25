package org.wwz.ai.application.agent.askuser;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.runtime.askuser.PendingUserQuestion;
import org.wwz.ai.domain.agent.runtime.askuser.PendingUserQuestionRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户回答 AskUserQuestion 的应用服务。
 * 前端提交答案后唤醒挂起的 Agent 工具线程。
 */
@Service
@RequiredArgsConstructor
public class AskUserQuestionApplicationService {

    private final PendingUserQuestionRegistry registry;

    public Map<String, Object> answer(String questionId, Map<String, String> answers) {
        if (StringUtils.isBlank(questionId)) {
            throw new IllegalArgumentException("questionId 不能为空");
        }
        boolean ok = registry.answer(questionId, answers);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("questionId", questionId);
        result.put("accepted", ok);
        if (!ok) {
            result.put("message", "问题不存在或已结束（已回答/超时/取消）");
        } else {
            result.put("message", "答案已提交，Agent 将继续执行");
        }
        return result;
    }

    public List<Map<String, Object>> listPending(String sessionId) {
        return registry.listBySession(sessionId).stream()
                .map(PendingUserQuestion::toClientPayload)
                .collect(Collectors.toList());
    }

    public Map<String, Object> cancel(String questionId, String reason) {
        boolean ok = registry.cancel(questionId, reason);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("questionId", questionId);
        result.put("cancelled", ok);
        return result;
    }
}
