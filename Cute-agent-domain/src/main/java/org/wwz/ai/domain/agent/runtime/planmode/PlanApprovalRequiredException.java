package org.wwz.ai.domain.agent.runtime.planmode;

/**
 * ExitPlanMode 让步信号：不阻塞线程，由上层持久化断点并结束当前 run。
 */
public class PlanApprovalRequiredException extends RuntimeException {

    private final String planContent;
    private final String planFilePath;
    private final String toolCallId;

    public PlanApprovalRequiredException(String planContent, String planFilePath, String toolCallId) {
        super("PLAN_APPROVAL_REQUIRED");
        this.planContent = planContent;
        this.planFilePath = planFilePath;
        this.toolCallId = toolCallId;
    }

    public String getPlanContent() {
        return planContent;
    }

    public String getPlanFilePath() {
        return planFilePath;
    }

    public String getToolCallId() {
        return toolCallId;
    }
}
