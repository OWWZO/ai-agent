package org.wwz.ai.domain.agent.runtime.planmode;

import java.util.List;
import java.util.Optional;

/**
 * ExitPlanMode 计划审批交互附属状态仓储。
 */
public interface IPlanApprovalRepository {

    void insert(PlanApprovalRecord record);

    Optional<PlanApprovalRecord> findByApprovalId(String approvalId);

    Optional<PlanApprovalRecord> findByResumeRequestId(String resumeRequestId);

    List<PlanApprovalRecord> listOpenBySessionId(String sessionId);

    boolean hasOpenBySessionId(String sessionId);

    /**
     * CAS PENDING → RESUME_PENDING；成功返回 true。
     */
    boolean casDecidePending(String approvalId, String visitorId, PlanApprovalDecision decision, String resumeRequestId);

    /**
     * CAS RESUME_PENDING → RESUMING；成功返回 true。
     */
    boolean casClaimResume(String resumeRequestId, String visitorId);

    boolean markAnswered(String approvalId);

    boolean markStatus(String approvalId, String status);

    boolean casCancel(String approvalId, String visitorId);

    int cancelBySourceRequestId(String sourceRequestId, String reason);
}
