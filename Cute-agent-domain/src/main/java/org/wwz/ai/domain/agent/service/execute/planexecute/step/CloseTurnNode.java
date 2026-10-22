package org.wwz.ai.domain.agent.service.execute.planexecute.step;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.runtime.command.AgentExecutionCommand;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.service.execute.planexecute.step.factory.DefaultPlanSolveAgentExecuteStrategyFactory;
import org.wwz.ai.domain.agent.service.execute.support.ReactTurnCloseSupport;

/**
 * PlanSolve 逻辑树 - Close：发送终答并收口账本 / 记忆。
 * <p>
 * 收口流程与 React 共用 {@link ReactTurnCloseSupport#closeFromNode}，
 * 本节点只提供链路标识与 ledger 入口。
 */
@Slf4j
@Service("planSolveCloseTurnNode")
public class CloseTurnNode extends AbstractExecuteSupport {

    @Resource
    private ReactTurnCloseSupport reactTurnCloseSupport;

    @Override
    protected String doApply(AgentExecutionCommand requestParameter,
                             DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("PlanSolve Close: final answer for requestId: {}", requestParameter.getRequestId());

        reactTurnCloseSupport.closeFromNode(
                dynamicContext.getAgentContext(),
                dynamicContext.getExecutor(),
                dynamicContext.getFinalAnswer(),
                "PlanSolve",
                ExecutionLedgerConstants.ENTRY_AGENT_PLAN_SOLVE
        );
        return "success";
    }

    @Override
    public StrategyHandler<AgentExecutionCommand, DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext, String> get(
            AgentExecutionCommand requestParameter,
            DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return null;
    }
}
