package org.wwz.ai.domain.agent.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 工作记忆压缩事件（表 ai_agent_working_memory_compaction）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkingMemoryCompactionEvent {

    public static final int STATUS_SUCCESS = 1;
    public static final int STATUS_FAILED = 2;

    private Long id;
    private String sessionId;
    private String triggerRequestId;
    private String compactRequestId;
    private String strategy;
    private Integer status;
    private Integer beforeTokens;
    private Integer afterTokens;
    private Integer beforeMessageCount;
    private Integer afterMessageCount;
    private Integer thresholdTokens;
    private String summaryText;
    private String beforeMessagesJson;
    private String afterMessagesJson;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
