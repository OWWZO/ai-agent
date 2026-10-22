package org.wwz.ai.trigger.http.agent.vo;

import lombok.Data;

/**
 * 用户批准 / 拒绝 Plan Mode 退出计划。
 */
@Data
public class PlanApprovalReqVO {

    private String approvalId;
    /** 可选：用户编辑后的计划正文 */
    private String editedPlanContent;
    /** 可选：拒绝反馈或备注 */
    private String feedback;
}
