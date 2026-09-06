package org.wwz.ai.domain.agent.runtime.planmode;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunView;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;

import java.util.List;

/**
 * PlanSolve 是否自动进入 plan mode。
 * 续跑不进；forcePlanMode 进；否则仅会话内第一次用户发起的 PlanSolve 轮进入。
 */
public final class PlanModeEntryPolicy {

    private PlanModeEntryPolicy() {
    }

    public static boolean shouldAutoEnter(AgentRequest request,
                                          boolean resumedApprovedPlan,
                                          boolean hasPriorPlanSolveUserTurn) {
        if (request == null || resumedApprovedPlan || isContinuation(request)) {
            return false;
        }
        if (Boolean.TRUE.equals(request.getForcePlanMode())) {
            return true;
        }
        return !hasPriorPlanSolveUserTurn;
    }

    public static boolean isContinuation(AgentRequest request) {
        return request != null
                && (StringUtils.isNotBlank(request.getResumeQuestionId())
                || StringUtils.isNotBlank(request.getResumeApprovalId()));
    }

    /**
     * 当前 request 写入账本之前调用：已有 plan_solve run 即视为非首轮。
     */
    public static boolean hasPriorPlanSolveUserTurn(List<DialogueRunView> runs, String currentRequestId) {
        if (runs == null || runs.isEmpty()) {
            return false;
        }
        for (DialogueRunView run : runs) {
            if (run == null || !ExecutionLedgerConstants.ENTRY_AGENT_PLAN_SOLVE.equals(run.getEntryAgent())) {
                continue;
            }
            if (StringUtils.isNotBlank(currentRequestId) && currentRequestId.equals(run.getRequestId())) {
                continue;
            }
            return true;
        }
        return false;
    }
}
