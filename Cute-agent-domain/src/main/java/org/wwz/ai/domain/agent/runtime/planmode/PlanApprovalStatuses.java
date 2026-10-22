package org.wwz.ai.domain.agent.runtime.planmode;

import java.util.List;

/**
 * ExitPlanMode 计划审批持久化状态（附属表，非 ledger）。
 */
public final class PlanApprovalStatuses {

    public static final String PENDING = "PENDING";
    public static final String RESUME_PENDING = "RESUME_PENDING";
    public static final String RESUMING = "RESUMING";
    public static final String ANSWERED = "ANSWERED";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String CANCELLED = "CANCELLED";
    public static final String FAILED = "FAILED";

    public static final List<String> OPEN = List.of(PENDING, RESUME_PENDING, RESUMING);
    public static final List<String> CANCELABLE = List.of(PENDING, RESUME_PENDING);

    private PlanApprovalStatuses() {
    }

    public static boolean isOpen(String status) {
        return PENDING.equals(status) || RESUME_PENDING.equals(status) || RESUMING.equals(status);
    }
}
