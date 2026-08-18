package org.wwz.ai.infrastructure.dao.reactor;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.wwz.ai.infrastructure.dao.reactor.po.UserQuestionPO;

import java.util.List;

@Mapper
public interface IUserQuestionDao {

    int insert(UserQuestionPO row);

    UserQuestionPO selectByQuestionId(@Param("questionId") String questionId);

    UserQuestionPO selectByResumeRequestId(@Param("resumeRequestId") String resumeRequestId);

    List<UserQuestionPO> selectOpenBySessionId(@Param("sessionId") String sessionId);

    int countOpenBySessionId(@Param("sessionId") String sessionId);

    int casAnswerPending(@Param("questionId") String questionId,
                         @Param("visitorId") String visitorId,
                         @Param("answersJson") String answersJson,
                         @Param("resumeRequestId") String resumeRequestId);

    int casClaimResume(@Param("resumeRequestId") String resumeRequestId,
                       @Param("visitorId") String visitorId);

    int markAnswered(@Param("questionId") String questionId);

    int markFailed(@Param("questionId") String questionId, @Param("status") String status);

    int casCancel(@Param("questionId") String questionId,
                  @Param("visitorId") String visitorId,
                  @Param("fromStatuses") List<String> fromStatuses);
}
