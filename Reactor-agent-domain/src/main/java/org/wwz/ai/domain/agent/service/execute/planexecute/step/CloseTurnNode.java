package org.wwz.ai.domain.agent.service.execute.planexecute.step;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.service.execute.planexecute.step.factory.DefaultPlanSolveAgentExecuteStrategyFactory;
import org.wwz.ai.domain.agent.service.execute.support.ReactTurnCloseSupport;

/**
 * PlanSolve 逻辑树 - Close：发送终答并收口账本 / 记忆。
 */
@Slf4j
@Service("planSolveCloseTurnNode")
public class CloseTurnNode extends AbstractExecuteSupport {

    @Resource
    private ReactTurnCloseSupport reactTurnCloseSupport;

    @Override
    protected String doApply(AgentRequest requestParameter,
                             DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("PlanSolve Close: final answer for requestId: {}", requestParameter.getRequestId());

        AgentContext agentContext = dynamicContext.getAgentContext();
        if (agentContext == null || dynamicContext.getExecutor() == null) {
            throw new IllegalStateException("PlanSolve Close: agentContext/executor is null, Run must run first.");
        }

        reactTurnCloseSupport.closeSuccessfulTurn(
                agentContext,
                dynamicContext.getExecutor(),
                dynamicContext.getFinalAnswer(),
                ExecutionLedgerConstants.ENTRY_AGENT_PLAN_SOLVE
        );
        return "success";
    }

    @Override
    public StrategyHandler<AgentRequest, DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext, String> get(
            AgentRequest requestParameter,
            DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return null;
    }
}
