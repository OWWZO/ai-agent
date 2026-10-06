package org.wwz.ai.infrastructure.dao.reactor.po;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DesktopControlPO {
    private Long id;
    private String controlId;
    private String userId;
    private String sessionId;
    private String ownerKey;
    private Long sourceRunId;
    private String sourceRequestId;
    private Long toolInvocationId;
    private String toolCallId;
    private String reason;
    private String streamUrl;
    private Double holdUntil;
    private String status;
    private String resumeRequestId;
    private String resumeContextJson;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
