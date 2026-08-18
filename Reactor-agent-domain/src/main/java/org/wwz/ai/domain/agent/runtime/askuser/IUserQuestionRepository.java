package org.wwz.ai.domain.agent.runtime.askuser;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * AskUserQuestion 交互附属状态仓储。
 */
public interface IUserQuestionRepository {

    void insert(UserQuestionRecord record);

    Optional<UserQuestionRecord> findByQuestionId(String questionId);

    Optional<UserQuestionRecord> findByResumeRequestId(String resumeRequestId);

    List<UserQuestionRecord> listOpenBySessionId(String sessionId);

    boolean hasOpenBySessionId(String sessionId);

    /**
     * CAS PENDING → RESUME_PENDING；成功返回 true。
     */
    boolean casAnswerPending(String questionId, String visitorId, Map<String, String> answers, String resumeRequestId);

    /**
     * CAS RESUME_PENDING → RESUMING；成功返回 true。
     */
    boolean casClaimResume(String resumeRequestId, String visitorId);

    boolean markAnswered(String questionId);

    boolean markStatus(String questionId, String status);

    boolean casCancel(String questionId, String visitorId);
}
