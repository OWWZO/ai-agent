package org.wwz.ai.infrastructure.dao.reactor.po;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PlanApprovalPO {
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
    private String decisionJson;
    private String status;
    private LocalDateTime expiresAt;
    private String resumeRequestId;
    private String resumeContextJson;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
