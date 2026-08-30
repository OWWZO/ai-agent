package org.wwz.ai.domain.agent.runtime.planmode;

import com.alibaba.fastjson.JSON;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.askuser.UserQuestionResumeContext;

/**
 * 计划审批续跑瘦快照（字段对齐 AskUser resume context）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanApprovalResumeContext {

    private String entryAgent;
    private Integer agentType;
    private String model;
    private Boolean thinking;
    private String thinkingEffort;
    private UserQuestionResumeContext.PlanModeSnapshot planMode;

    public static PlanApprovalResumeContext from(AgentContext context, AgentRequest request, String entryAgent) {
        UserQuestionResumeContext shared = UserQuestionResumeContext.from(context, request, entryAgent);
        return PlanApprovalResumeContext.builder()
                .entryAgent(shared.getEntryAgent())
                .agentType(shared.getAgentType())
                .model(shared.getModel())
                .thinking(shared.getThinking())
                .thinkingEffort(shared.getThinkingEffort())
                .planMode(shared.getPlanMode())
                .build();
    }

    public static String toJson(PlanApprovalResumeContext context) {
        return JSON.toJSONString(context == null ? new PlanApprovalResumeContext() : context);
    }

    public static PlanApprovalResumeContext fromJson(String json) {
        if (StringUtils.isBlank(json)) {
            return new PlanApprovalResumeContext();
        }
        PlanApprovalResumeContext parsed = JSON.parseObject(json, PlanApprovalResumeContext.class);
        return parsed == null ? new PlanApprovalResumeContext() : parsed;
    }

    public void applyPlanModeTo(AgentContext agentContext) {
        if (agentContext == null || planMode == null) {
            return;
        }
        PlanModeState state = agentContext.requirePlanModeState();
        state.setMode(StringUtils.defaultIfBlank(planMode.getMode(), PlanModeState.MODE_DEFAULT));
        state.setPrePlanMode(planMode.getPrePlanMode());
        state.setPlanContent(planMode.getPlanContent());
        state.setPlanFilePath(planMode.getPlanFilePath());
        state.setHasExitedPlanMode(planMode.isHasExitedPlanMode());
        state.setExitPendingApproval(planMode.isExitPendingApproval());
        state.setPendingPlanContent(planMode.getPendingPlanContent());
        state.setStepsSincePlanAttachment(planMode.getStepsSincePlanAttachment());
        state.setPlanAttachmentCount(planMode.getPlanAttachmentCount());
        state.setNeedsPlanModeExitAttachment(planMode.isNeedsPlanModeExitAttachment());
    }
}
