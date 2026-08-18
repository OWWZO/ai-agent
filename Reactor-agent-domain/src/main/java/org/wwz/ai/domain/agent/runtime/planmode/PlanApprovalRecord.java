package org.wwz.ai.domain.agent.runtime.planmode;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanApprovalRecord {
    private Long id;
    private String approvalId;
    private String visitorId;
    private String sessionId;
    private Long sourceRunId;
    private String sourceRequestId;
    private Long toolInvocationId;
    private String toolCallId;
    private String planContent;
    private String planFilePath;
    private PlanApprovalDecision decision;
    private String status;
    private LocalDateTime expiresAt;
    private String resumeRequestId;
    private String resumeContextJson;
}
