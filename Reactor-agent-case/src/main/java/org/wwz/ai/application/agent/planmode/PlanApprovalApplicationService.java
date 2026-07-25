package org.wwz.ai.application.agent.planmode;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.runtime.planmode.PendingPlanApproval;
import org.wwz.ai.domain.agent.runtime.planmode.PendingPlanApprovalRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户批准 / 拒绝 ExitPlanMode 计划的应用服务。
 */
@Service
@RequiredArgsConstructor
public class PlanApprovalApplicationService {

    private final PendingPlanApprovalRegistry registry;

    public Map<String, Object> approve(String approvalId, String editedPlanContent, String feedback) {
        if (StringUtils.isBlank(approvalId)) {
            throw new IllegalArgumentException("approvalId 不能为空");
        }
        boolean ok = registry.approve(approvalId, editedPlanContent, feedback);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("approvalId", approvalId);
        result.put("accepted", ok);
        result.put("decision", "approved");
        if (!ok) {
            result.put("message", "批准请求不存在或已结束");
        } else {
            result.put("message", "计划已批准，Agent 将开始实现");
        }
        return result;
    }

    public Map<String, Object> reject(String approvalId, String feedback) {
        if (StringUtils.isBlank(approvalId)) {
            throw new IllegalArgumentException("approvalId 不能为空");
        }
        boolean ok = registry.reject(approvalId, feedback);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("approvalId", approvalId);
        result.put("accepted", ok);
        result.put("decision", "rejected");
        if (!ok) {
            result.put("message", "批准请求不存在或已结束");
        } else {
            result.put("message", "计划已拒绝，Agent 将继续在 plan mode 修订");
        }
        return result;
    }

    public List<Map<String, Object>> listPending(String sessionId) {
        return registry.listBySession(sessionId).stream()
                .map(PendingPlanApproval::toClientPayload)
                .collect(Collectors.toList());
    }

    public Map<String, Object> cancel(String approvalId, String reason) {
        boolean ok = registry.cancel(approvalId, reason);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("approvalId", approvalId);
        result.put("cancelled", ok);
        return result;
    }
}
