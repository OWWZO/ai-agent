package org.wwz.ai.domain.agent.runtime.askuser;

import com.alibaba.fastjson.JSON;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.planmode.PlanModeState;

/**
 * 续跑瘦快照：重建 Agent 时足够，不序列化运行时对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserQuestionResumeContext {

    private String entryAgent;
    private Integer agentType;
    private String model;
    private Boolean thinking;
    private String thinkingEffort;
    private PlanModeSnapshot planMode;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanModeSnapshot {
        private String mode;
        private String prePlanMode;
        private String planContent;
        private String planFilePath;
        private boolean hasExitedPlanMode;
        private boolean exitPendingApproval;
        private String pendingPlanContent;
        private int stepsSincePlanAttachment;
        private int planAttachmentCount;
        private boolean needsPlanModeExitAttachment;
    }

    public static UserQuestionResumeContext from(AgentContext context, AgentRequest request, String entryAgent) {
        PlanModeState state = context == null ? null : context.getPlanModeState();
        PlanModeSnapshot snapshot = null;
        if (state != null) {
            snapshot = PlanModeSnapshot.builder()
                    .mode(state.getMode())
                    .prePlanMode(state.getPrePlanMode())
                    .planContent(state.getPlanContent())
                    .planFilePath(state.getPlanFilePath())
                    .hasExitedPlanMode(state.isHasExitedPlanMode())
                    .exitPendingApproval(state.isExitPendingApproval())
                    .pendingPlanContent(state.getPendingPlanContent())
                    .stepsSincePlanAttachment(state.getStepsSincePlanAttachment())
                    .planAttachmentCount(state.getPlanAttachmentCount())
                    .needsPlanModeExitAttachment(state.isNeedsPlanModeExitAttachment())
                    .build();
        }
        return UserQuestionResumeContext.builder()
                .entryAgent(entryAgent)
                .agentType(request == null ? (context == null ? null : context.getAgentType()) : request.getAgentType())
                .model(request == null ? (context == null ? null : context.getModel()) : request.getModel())
                .thinking(request == null ? (context == null ? null : context.getThinking()) : request.getThinking())
                .thinkingEffort(request == null
                        ? (context == null ? null : context.getThinkingEffort())
                        : request.getThinkingEffort())
                .planMode(snapshot)
                .build();
    }

    public static String toJson(UserQuestionResumeContext context) {
        return JSON.toJSONString(context == null ? new UserQuestionResumeContext() : context);
    }

    public static UserQuestionResumeContext fromJson(String json) {
        if (StringUtils.isBlank(json)) {
            return new UserQuestionResumeContext();
        }
        UserQuestionResumeContext parsed = JSON.parseObject(json, UserQuestionResumeContext.class);
        return parsed == null ? new UserQuestionResumeContext() : parsed;
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
