package org.wwz.ai.domain.agent.runtime.askuser;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserQuestionRecord {
    private Long id;
    private String questionId;
    private String visitorId;
    private String sessionId;
    private Long sourceRunId;
    private String sourceRequestId;
    private Long toolInvocationId;
    private String toolCallId;
    private List<Map<String, Object>> questions;
    private Map<String, String> answers;
    private String status;
    private LocalDateTime expiresAt;
    private String resumeRequestId;
    private String resumeContextJson;
}
