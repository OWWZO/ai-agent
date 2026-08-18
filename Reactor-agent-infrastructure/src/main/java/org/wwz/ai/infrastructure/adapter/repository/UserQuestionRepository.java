package org.wwz.ai.infrastructure.adapter.repository;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;
import org.wwz.ai.domain.agent.runtime.askuser.IUserQuestionRepository;
import org.wwz.ai.domain.agent.runtime.askuser.UserQuestionRecord;
import org.wwz.ai.domain.agent.runtime.askuser.UserQuestionStatuses;
import org.wwz.ai.infrastructure.dao.reactor.IUserQuestionDao;
import org.wwz.ai.infrastructure.dao.reactor.po.UserQuestionPO;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class UserQuestionRepository implements IUserQuestionRepository {

    private static final TypeReference<List<Map<String, Object>>> QUESTIONS_TYPE =
            new TypeReference<List<Map<String, Object>>>() {
            };
    private static final TypeReference<Map<String, String>> ANSWERS_TYPE =
            new TypeReference<Map<String, String>>() {
            };

    private final IUserQuestionDao userQuestionDao;

    @Override
    public void insert(UserQuestionRecord record) {
        if (record == null) {
            return;
        }
        userQuestionDao.insert(toPo(record));
    }

    @Override
    public Optional<UserQuestionRecord> findByQuestionId(String questionId) {
        if (StringUtils.isBlank(questionId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(toRecord(userQuestionDao.selectByQuestionId(questionId.trim())));
    }

    @Override
    public Optional<UserQuestionRecord> findByResumeRequestId(String resumeRequestId) {
        if (StringUtils.isBlank(resumeRequestId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(toRecord(userQuestionDao.selectByResumeRequestId(resumeRequestId.trim())));
    }

    @Override
    public List<UserQuestionRecord> listOpenBySessionId(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return List.of();
        }
        List<UserQuestionPO> rows = userQuestionDao.selectOpenBySessionId(sessionId.trim());
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream().map(this::toRecord).collect(Collectors.toList());
    }

    @Override
    public boolean hasOpenBySessionId(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return false;
        }
        Integer count = userQuestionDao.countOpenBySessionId(sessionId.trim());
        return count != null && count > 0;
    }

    @Override
    public boolean casAnswerPending(String questionId, String visitorId, Map<String, String> answers, String resumeRequestId) {
        if (StringUtils.isBlank(questionId) || StringUtils.isBlank(resumeRequestId)) {
            return false;
        }
        return userQuestionDao.casAnswerPending(
                questionId.trim(),
                StringUtils.trimToNull(visitorId),
                JSON.toJSONString(answers == null ? Collections.emptyMap() : answers),
                resumeRequestId.trim()) > 0;
    }

    @Override
    public boolean casClaimResume(String resumeRequestId, String visitorId) {
        if (StringUtils.isBlank(resumeRequestId)) {
            return false;
        }
        return userQuestionDao.casClaimResume(resumeRequestId.trim(), StringUtils.trimToNull(visitorId)) > 0;
    }

    @Override
    public boolean markAnswered(String questionId) {
        if (StringUtils.isBlank(questionId)) {
            return false;
        }
        return userQuestionDao.markAnswered(questionId.trim()) > 0;
    }

    @Override
    public boolean markStatus(String questionId, String status) {
        if (StringUtils.isBlank(questionId) || StringUtils.isBlank(status)) {
            return false;
        }
        return userQuestionDao.markFailed(questionId.trim(), status.trim()) > 0;
    }

    @Override
    public boolean casCancel(String questionId, String visitorId) {
        if (StringUtils.isBlank(questionId)) {
            return false;
        }
        return userQuestionDao.casCancel(
                questionId.trim(),
                StringUtils.trimToNull(visitorId),
                UserQuestionStatuses.CANCELABLE) > 0;
    }

    private UserQuestionPO toPo(UserQuestionRecord record) {
        UserQuestionPO po = new UserQuestionPO();
        po.setQuestionId(record.getQuestionId());
        po.setVisitorId(record.getVisitorId());
        po.setSessionId(record.getSessionId());
        po.setSourceRunId(record.getSourceRunId());
        po.setSourceRequestId(record.getSourceRequestId());
        po.setToolInvocationId(record.getToolInvocationId());
        po.setToolCallId(record.getToolCallId());
        po.setQuestionsJson(JSON.toJSONString(record.getQuestions() == null ? List.of() : record.getQuestions()));
        po.setAnswersJson(record.getAnswers() == null ? null : JSON.toJSONString(record.getAnswers()));
        po.setStatus(record.getStatus());
        po.setExpiresAt(record.getExpiresAt());
        po.setResumeRequestId(record.getResumeRequestId());
        po.setResumeContextJson(record.getResumeContextJson());
        return po;
    }

    private UserQuestionRecord toRecord(UserQuestionPO po) {
        if (po == null) {
            return null;
        }
        List<Map<String, Object>> questions = List.of();
        if (StringUtils.isNotBlank(po.getQuestionsJson())) {
            List<Map<String, Object>> parsed = JSON.parseObject(po.getQuestionsJson(), QUESTIONS_TYPE);
            questions = parsed == null ? List.of() : parsed;
        }
        Map<String, String> answers = null;
        if (StringUtils.isNotBlank(po.getAnswersJson())) {
            Map<String, String> parsed = JSON.parseObject(po.getAnswersJson(), ANSWERS_TYPE);
            answers = parsed == null ? new LinkedHashMap<>() : parsed;
        }
        return UserQuestionRecord.builder()
                .id(po.getId())
                .questionId(po.getQuestionId())
                .visitorId(po.getVisitorId())
                .sessionId(po.getSessionId())
                .sourceRunId(po.getSourceRunId())
                .sourceRequestId(po.getSourceRequestId())
                .toolInvocationId(po.getToolInvocationId())
                .toolCallId(po.getToolCallId())
                .questions(questions)
                .answers(answers)
                .status(po.getStatus())
                .expiresAt(po.getExpiresAt())
                .resumeRequestId(po.getResumeRequestId())
                .resumeContextJson(po.getResumeContextJson())
                .build();
    }
}
