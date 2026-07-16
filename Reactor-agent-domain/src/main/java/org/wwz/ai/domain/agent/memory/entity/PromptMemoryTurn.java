package org.wwz.ai.domain.agent.memory.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 一次请求在记忆流中的发布轮次。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptMemoryTurn {

    private Long id;
    private Long streamId;
    private String requestId;
    private Long runId;
    private Integer turnSeq;
    private Integer baselineTurnSeq;
    private Integer status;
    private Integer messageCount;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
