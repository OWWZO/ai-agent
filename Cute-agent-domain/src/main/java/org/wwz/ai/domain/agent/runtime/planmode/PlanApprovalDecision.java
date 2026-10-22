package org.wwz.ai.domain.agent.runtime.planmode;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户对 ExitPlanMode 的决定。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanApprovalDecision {

    private boolean approved;
    /** 拒绝时的反馈；批准时可空 */
    private String feedback;
    /** 用户编辑后的计划正文（可选，覆盖 pending 中的 plan） */
    private String editedPlanContent;
}
