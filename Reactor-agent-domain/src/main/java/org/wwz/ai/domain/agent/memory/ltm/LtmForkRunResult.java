package org.wwz.ai.domain.agent.memory.ltm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LTM memory-only fork 执行结果（flush / background-review 共用）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LtmForkRunResult {

    public static final int STATUS_SUCCESS = 1;
    public static final int STATUS_FAILED = 2;
    public static final int STATUS_SKIPPED = 3;
    public static final int STATUS_TIMEOUT = 4;

    private int status;
    private String skipReason;
    private String errorMessage;
    private int appliedCount;
    private int entriesBefore;
    private int entriesAfter;
    private long durationMs;
    private String forkRequestId;
    private String forkLabel;
    /**
     * 本 fork 相对执行前的记忆变更 JSON：
     * {"added":[{"scope":"user","content":"..."}],"removed":[...]}
     */
    private String writtenEntriesJson;

    public static LtmForkRunResult skipped(String reason) {
        return LtmForkRunResult.builder()
                .status(STATUS_SKIPPED)
                .skipReason(reason)
                .appliedCount(0)
                .build();
    }

    public static LtmForkRunResult timeout(String forkRequestId, String forkLabel,
                                           int entriesBefore, long durationMs) {
        return LtmForkRunResult.builder()
                .status(STATUS_TIMEOUT)
                .skipReason("timeout")
                .errorMessage("fork timed out")
                .forkRequestId(forkRequestId)
                .forkLabel(forkLabel)
                .entriesBefore(entriesBefore)
                .entriesAfter(entriesBefore)
                .appliedCount(0)
                .durationMs(durationMs)
                .build();
    }

    public static LtmForkRunResult failed(String forkRequestId, String forkLabel,
                                          int entriesBefore, long durationMs, String error) {
        return LtmForkRunResult.builder()
                .status(STATUS_FAILED)
                .errorMessage(error)
                .forkRequestId(forkRequestId)
                .forkLabel(forkLabel)
                .entriesBefore(entriesBefore)
                .entriesAfter(entriesBefore)
                .appliedCount(0)
                .durationMs(durationMs)
                .build();
    }

    public int appliedOrZero() {
        return Math.max(0, appliedCount);
    }
}
