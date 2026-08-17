package org.wwz.ai.domain.agent.service.execute.react.step;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.service.execute.react.step.factory.DefaultReactAgentExecuteStrategyFactory;
import org.wwz.ai.domain.agent.service.execute.support.ReactTurnCloseSupport;

/**
 * React 逻辑树 - Close：发送终答并收口账本 / 记忆。
 */
@Slf4j
@Service("reactCloseTurnNode")
public class CloseTurnNode extends AbstractExecuteSupport {

    @Resource
    private ReactTurnCloseSupport reactTurnCloseSupport;

    @Override
    protected String doApply(AgentRequest requestParameter,
                             DefaultReactAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("React Close: final answer for requestId: {}", requestParameter.getRequestId());

        AgentContext agentContext = dynamicContext.getAgentContext();
        if (agentContext == null || dynamicContext.getExecutor() == null) {
            throw new IllegalStateException("React Close: agentContext/executor is null, Run must run first.");
        }

        reactTurnCloseSupport.closeSuccessfulTurn(
                agentContext,
                dynamicContext.getExecutor(),
                dynamicContext.getFinalAnswer(),
                ExecutionLedgerConstants.ENTRY_AGENT_REACT
        );
        return "success";
    }

    @Override
    public StrategyHandler<AgentRequest, DefaultReactAgentExecuteStrategyFactory.DynamicContext, String> get(
            AgentRequest requestParameter,
            DefaultReactAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return null;
    }
}
