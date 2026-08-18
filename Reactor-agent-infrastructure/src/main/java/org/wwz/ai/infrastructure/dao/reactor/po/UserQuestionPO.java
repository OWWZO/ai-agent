package org.wwz.ai.infrastructure.dao.reactor.po;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserQuestionPO {
    private Long id;
    private String questionId;
    private String visitorId;
    private String sessionId;
    private Long sourceRunId;
    private String sourceRequestId;
    private Long toolInvocationId;
    private String toolCallId;
    private String questionsJson;
    private String answersJson;
    private String status;
    private LocalDateTime expiresAt;
    private String resumeRequestId;
    private String resumeContextJson;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
