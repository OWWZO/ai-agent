package org.wwz.ai.domain.agent.memory.ltm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * LTM fork 执行观测事件（表 ai_agent_ltm_fork_execution）。
 * 覆盖：上下文压缩前 flush、background review。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LtmForkExecutionEvent {

    public static final int STATUS_SUCCESS = LtmForkRunResult.STATUS_SUCCESS;
    public static final int STATUS_FAILED = LtmForkRunResult.STATUS_FAILED;
    public static final int STATUS_SKIPPED = LtmForkRunResult.STATUS_SKIPPED;
    public static final int STATUS_TIMEOUT = LtmForkRunResult.STATUS_TIMEOUT;

    public static final String KIND_FLUSH = "flush";
    public static final String KIND_BG_REVIEW = "bg-review";

    private Long id;
    private String sessionId;
    private String triggerRequestId;
    private String forkRequestId;
    /** flush / bg-review */
    private String forkKind;
    /** 1=SUCCESS 2=FAILED 3=SKIPPED 4=TIMEOUT */
    private Integer status;
    private String skipReason;
    private String ownerType;
    private String ownerId;
    private Integer userTurns;
    private Integer snapshotMessageCount;
    private Integer maxSteps;
    private Long timeoutSeconds;
    private Long durationMs;
    private Integer entriesBefore;
    private Integer entriesAfter;
    private Integer appliedCount;
    private String errorMessage;
    private String detailJson;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
