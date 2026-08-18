package org.wwz.ai.infrastructure.dao.reactor;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.wwz.ai.infrastructure.dao.reactor.po.PlanApprovalPO;

import java.util.List;

@Mapper
public interface IPlanApprovalDao {

    int insert(PlanApprovalPO row);

    PlanApprovalPO selectByApprovalId(@Param("approvalId") String approvalId);

    PlanApprovalPO selectByResumeRequestId(@Param("resumeRequestId") String resumeRequestId);

    List<PlanApprovalPO> selectOpenBySessionId(@Param("sessionId") String sessionId);

    int countOpenBySessionId(@Param("sessionId") String sessionId);

    int casDecidePending(@Param("approvalId") String approvalId,
                         @Param("visitorId") String visitorId,
                         @Param("decisionJson") String decisionJson,
                         @Param("resumeRequestId") String resumeRequestId);

    int casClaimResume(@Param("resumeRequestId") String resumeRequestId,
                       @Param("visitorId") String visitorId);

    int markAnswered(@Param("approvalId") String approvalId);

    int markFailed(@Param("approvalId") String approvalId, @Param("status") String status);

    int casCancel(@Param("approvalId") String approvalId,
                  @Param("visitorId") String visitorId,
                  @Param("fromStatuses") List<String> fromStatuses);

    int cancelBySourceRequestId(@Param("sourceRequestId") String sourceRequestId,
                                @Param("fromStatuses") List<String> fromStatuses);
}
