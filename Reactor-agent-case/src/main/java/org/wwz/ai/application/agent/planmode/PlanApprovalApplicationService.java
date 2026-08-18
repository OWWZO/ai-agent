package org.wwz.ai.application.agent.planmode;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.runtime.planmode.IPlanApprovalRepository;
import org.wwz.ai.domain.agent.runtime.planmode.PlanApprovalDecision;
import org.wwz.ai.domain.agent.runtime.planmode.PlanApprovalObservationSupport;
import org.wwz.ai.domain.agent.runtime.planmode.PlanApprovalRecord;
import org.wwz.ai.domain.agent.runtime.planmode.PlanApprovalStatuses;
import org.wwz.ai.types.agent.visitor.VisitorRequestContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 用户批准 / 拒绝 ExitPlanMode：CAS 写库并返回 resumeRequestId（不启动 Run B）。
 */
@Service
@RequiredArgsConstructor
public class PlanApprovalApplicationService {

    private final IPlanApprovalRepository planApprovalRepository;

    public Map<String, Object> approve(String approvalId, String editedPlanContent, String feedback) {
        return decide(approvalId, PlanApprovalDecision.builder()
                .approved(true)
                .editedPlanContent(editedPlanContent)
                .feedback(feedback)
                .build());
    }

    public Map<String, Object> reject(String approvalId, String feedback) {
        return decide(approvalId, PlanApprovalDecision.builder()
                .approved(false)
                .feedback(feedback)
                .build());
    }

    private Map<String, Object> decide(String approvalId, PlanApprovalDecision decision) {
        if (StringUtils.isBlank(approvalId)) {
            throw new IllegalArgumentException("approvalId 不能为空");
        }
        String visitorId = VisitorRequestContext.currentVisitorId();
        PlanApprovalRecord existing = planApprovalRepository.findByApprovalId(approvalId.trim()).orElse(null);
        if (existing == null) {
            return rejected(approvalId, "批准请求不存在");
        }
        if (StringUtils.isNotBlank(existing.getVisitorId())
                && StringUtils.isNotBlank(visitorId)
                && !existing.getVisitorId().equals(visitorId)) {
            return rejected(approvalId, "无权操作该审批");
        }
        if (PlanApprovalStatuses.RESUME_PENDING.equals(existing.getStatus())
                || PlanApprovalStatuses.RESUMING.equals(existing.getStatus())
                || PlanApprovalStatuses.ANSWERED.equals(existing.getStatus())) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("approvalId", approvalId);
            result.put("accepted", true);
            result.put("idempotent", true);
            result.put("resumeRequestId", existing.getResumeRequestId());
            result.put("status", existing.getStatus());
            result.put("decision", decision.isApproved() ? "approved" : "rejected");
            result.put("message", "决策已存在，请连接 resume SSE 继续");
            return result;
        }
        if (!PlanApprovalStatuses.PENDING.equals(existing.getStatus())) {
            return rejected(approvalId, "批准请求不存在或已结束");
        }

        String resumeRequestId = "resume_" + UUID.randomUUID().toString().replace("-", "");
        boolean ok = planApprovalRepository.casDecidePending(
                approvalId.trim(), visitorId, decision, resumeRequestId);
        if (!ok) {
            PlanApprovalRecord latest = planApprovalRepository.findByApprovalId(approvalId.trim()).orElse(existing);
            if (StringUtils.isNotBlank(latest.getResumeRequestId())) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("approvalId", approvalId);
                result.put("accepted", true);
                result.put("idempotent", true);
                result.put("resumeRequestId", latest.getResumeRequestId());
                result.put("status", latest.getStatus());
                result.put("decision", decision.isApproved() ? "approved" : "rejected");
                result.put("message", "决策已存在，请连接 resume SSE 继续");
                return result;
            }
            return rejected(approvalId, "批准请求不存在或已结束");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("approvalId", approvalId);
        result.put("accepted", true);
        result.put("idempotent", false);
        result.put("resumeRequestId", resumeRequestId);
        result.put("status", PlanApprovalStatuses.RESUME_PENDING);
        result.put("sessionId", existing.getSessionId());
        result.put("decision", decision.isApproved() ? "approved" : "rejected");
        result.put("message", decision.isApproved()
                ? "计划已批准，请连接 resume SSE 继续执行"
                : "计划已拒绝，请连接 resume SSE 继续修订");
        return result;
    }

    public List<Map<String, Object>> listPending(String sessionId) {
        return planApprovalRepository.listOpenBySessionId(sessionId).stream()
                .map(PlanApprovalObservationSupport::toClientPayload)
                .collect(Collectors.toList());
    }

    public Map<String, Object> cancel(String approvalId, String reason) {
        String visitorId = VisitorRequestContext.currentVisitorId();
        boolean ok = planApprovalRepository.casCancel(approvalId, visitorId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("approvalId", approvalId);
        result.put("cancelled", ok);
        result.put("reason", reason);
        return result;
    }

    public boolean hasOpenApproval(String sessionId) {
        return planApprovalRepository.hasOpenBySessionId(sessionId);
    }

    private static Map<String, Object> rejected(String approvalId, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("approvalId", approvalId);
        result.put("accepted", false);
        result.put("message", message);
        return result;
    }
}
