package org.wwz.ai.domain.agent.runtime.planmode;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 挂起的 Plan 批准请求（对标 cc-haha ExitPlanMode 等人批）。
 * 工具线程 await future；Web 通过独立 HTTP 提交 approve/reject 完成 future。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingPlanApproval {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_TIMEOUT = "timeout";
    public static final String STATUS_CANCELLED = "cancelled";

    public static final String DECISION_APPROVED = "approved";
    public static final String DECISION_REJECTED = "rejected";

    private String approvalId;
    private String sessionId;
    private String requestId;
    private String toolCallId;
    private String planContent;
    private String planFilePath;
    private long createdAtMs;
    private long timeoutMs;
    private String status;
    private String feedback;
    private CompletableFuture<PlanApprovalDecision> future;

    public Map<String, Object> toClientPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("approvalId", approvalId);
        payload.put("sessionId", sessionId);
        payload.put("requestId", requestId);
        payload.put("toolCallId", toolCallId);
        payload.put("planContent", planContent);
        payload.put("planFilePath", planFilePath);
        payload.put("status", status);
        payload.put("createdAtMs", createdAtMs);
        payload.put("timeoutMs", timeoutMs);
        return payload;
    }
}
