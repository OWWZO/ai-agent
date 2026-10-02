package org.wwz.ai.domain.agent.runtime.desktopcontrol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DesktopControlRecord {
    private Long id;
    private String controlId;
    private String visitorId;
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
}
