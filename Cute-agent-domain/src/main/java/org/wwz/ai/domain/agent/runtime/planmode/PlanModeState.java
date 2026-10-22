package org.wwz.ai.domain.agent.runtime.planmode;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

/**
 * Plan Mode 会话状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanModeState {

    public static final String MODE_DEFAULT = "default";
    public static final String MODE_PLAN = "plan";
    public static final String MODE_ACCEPT_EDITS = "acceptEdits";

    @Builder.Default
    private String mode = MODE_DEFAULT;
    /** 进入 plan 前的模式，退出时恢复 */
    private String prePlanMode;
    /** 计划正文（Exit 时展示；也可由 plan 文件加载） */
    private String planContent;
    /** 计划文件路径（可选） */
    private String planFilePath;
    private boolean hasExitedPlanMode;

    /** 是否有待用户批准的退出计划（对标 ExitPlanMode 挂起等待） */
    @Builder.Default
    private boolean exitPendingApproval = false;

    /** 最近一次 ExitPlanMode 的 plan 内容（用于批准时回显） */
    private String pendingPlanContent;

    /** 自上次 plan_mode 附件注入以来的 step 数。 */
    @Builder.Default
    private int stepsSincePlanAttachment = 0;

    /** 已注入的 plan 附件次数（用于 full/sparse 交替） */
    @Builder.Default
    private int planAttachmentCount = 0;

    /** 退出 plan 后下一轮需注入 plan_mode_exit 提醒 */
    @Builder.Default
    private boolean needsPlanModeExitAttachment = false;

    public boolean isPlanMode() {
        return MODE_PLAN.equals(mode);
    }

    public synchronized void enterPlanMode() {
        if (isPlanMode()) {
            return;
        }
        this.prePlanMode = StringUtils.defaultIfBlank(mode, MODE_DEFAULT);
        this.mode = MODE_PLAN;
        this.hasExitedPlanMode = false;
        this.exitPendingApproval = false;
        this.pendingPlanContent = null;
        this.stepsSincePlanAttachment = 0;
        this.planAttachmentCount = 0;
        this.needsPlanModeExitAttachment = false;
    }

    public synchronized String exitPlanMode() {
        String restored = StringUtils.defaultIfBlank(prePlanMode, MODE_DEFAULT);
        if (!MODE_DEFAULT.equals(restored) && !MODE_ACCEPT_EDITS.equals(restored) && !MODE_PLAN.equals(restored)) {
            restored = MODE_DEFAULT;
        }
        this.mode = restored;
        this.prePlanMode = null;
        this.hasExitedPlanMode = true;
        this.exitPendingApproval = false;
        this.pendingPlanContent = null;
        this.needsPlanModeExitAttachment = true;
        this.stepsSincePlanAttachment = 0;
        return restored;
    }

    public synchronized void markPlanAttachmentInjected() {
        this.stepsSincePlanAttachment = 0;
        this.planAttachmentCount++;
    }

    public synchronized void tickStep() {
        if (isPlanMode()) {
            this.stepsSincePlanAttachment++;
        }
    }

    public synchronized void clearExitAttachmentFlag() {
        this.needsPlanModeExitAttachment = false;
    }

    public synchronized void setPlan(String content, String filePath) {
        if (content != null) {
            this.planContent = content;
        }
        if (filePath != null) {
            this.planFilePath = filePath;
        }
    }

    /** 记录退出计划并标记待批准（不立即退出 mode） */
    public synchronized void requestExitWithPlan(String content, String filePath) {
        if (content != null) {
            this.pendingPlanContent = content;
            this.planContent = content;
        }
        if (filePath != null) {
            this.planFilePath = filePath;
        }
        this.exitPendingApproval = true;
    }

    public synchronized void clearPendingApproval() {
        this.exitPendingApproval = false;
        this.pendingPlanContent = null;
    }
}
