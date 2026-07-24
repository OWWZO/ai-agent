package org.wwz.ai.domain.agent.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 工作记忆 turn 头（投影表 ai_agent_working_memory_turn）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkingMemoryTurn {

    public static final int STATUS_BUILDING = 0;
    public static final int STATUS_READY = 1;
    public static final int STATUS_INVALID = 2;

    private Long id;
    private String sessionId;
    private String requestId;
    private Long runId;
    private Integer turnSeq;
    private String entryAgent;
    private Integer status;
    private Integer schemaVersion;
    private Integer messageCount;
    private Integer tokenEstimate;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
