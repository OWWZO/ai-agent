package org.wwz.ai.infrastructure.dao.reactor.po;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BackgroundTaskPO {
    private Long id;
    private String sessionId;
    private String taskId;
    private String type;
    private String status;
    private String description;
    private String command;
    private String agentId;
    private String agentType;
    private String prompt;
    private String output;
    private String errorMsg;
    private Integer totalToolUseCount;
    private Long totalDurationMs;
    private Long startedAtMs;
    private Long endedAtMs;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
