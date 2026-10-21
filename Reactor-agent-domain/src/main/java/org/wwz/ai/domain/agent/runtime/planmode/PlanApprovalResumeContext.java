package org.wwz.ai.domain.agent.runtime.planmode;

import com.alibaba.fastjson.JSON;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.command.AgentExecutionCommand;
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

    public static PlanApprovalResumeContext from(AgentContext context, AgentExecutionCommand request, String entryAgent) {
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

    /**
     * plan mode 恢复由 {@link UserQuestionResumeContext#applyPlanModeTo(AgentContext)} 承担：
     * PrepareAgentContextNode 只认这一份，此处不再保留一份逐字相同的副本。
     * 续跑时把本快照的 planMode 交给它即可：
     * {@code UserQuestionResumeContext.builder().planMode(ctx.getPlanMode()).build().applyPlanModeTo(agentContext)}
     */
}
