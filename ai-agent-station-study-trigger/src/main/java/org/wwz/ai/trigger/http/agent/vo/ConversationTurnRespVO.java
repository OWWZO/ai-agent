package org.wwz.ai.trigger.http.agent.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ConversationTurnRespVO {
    private String requestId;
    private Integer sortOrder;
    private String query;
    private Object files;
    private Object generatedFiles;
    private Integer agentType;
    private String response;
    private Integer status;
    private Integer forceStop;
    private Object metrics;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private List<ConversationEventRespVO> events;
}
