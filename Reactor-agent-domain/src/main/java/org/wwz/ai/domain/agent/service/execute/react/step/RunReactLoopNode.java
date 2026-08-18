package org.wwz.ai.domain.agent.service.execute.react.step;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.ReactFinalAnswerResolver;
import org.wwz.ai.domain.agent.runtime.agent.ReactImplAgent;
import org.wwz.ai.domain.agent.runtime.askuser.UserInputRequiredException;
import org.wwz.ai.domain.agent.runtime.askuser.UserQuestionYieldService;
import org.wwz.ai.domain.agent.runtime.planmode.PlanApprovalRequiredException;
import org.wwz.ai.domain.agent.runtime.planmode.PlanApprovalYieldService;
import org.wwz.ai.domain.agent.runtime.planmode.PlanModePromptInjector;
import org.wwz.ai.domain.agent.service.execute.react.step.factory.DefaultReactAgentExecuteStrategyFactory;

/**
 * React 逻辑树 - Run：执行 ReAct 循环。
 */
@Slf4j
@Service("reactRunReactLoopNode")
public class RunReactLoopNode extends AbstractExecuteSupport {

    @Resource
    private CloseTurnNode closeTurnNode;

    @Resource
    private UserQuestionYieldService userQuestionYieldService;

    @Resource
    private PlanApprovalYieldService planApprovalYieldService;

    @Override
    protected String doApply(AgentRequest requestParameter,
                             DefaultReactAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("React Run: loop for requestId: {}", requestParameter.getRequestId());

        AgentContext agentContext = dynamicContext.getAgentContext();
        if (agentContext == null) {
            throw new IllegalStateException("React Run: agentContext is null, Prepare must run first.");
        }

        ReactImplAgent executor = new ReactImplAgent(agentContext);
        dynamicContext.setExecutor(executor);
        PlanModePromptInjector.applyIfPlanMode(agentContext, executor);
        try {
            String runResult = executor.run(requestParameter.getQuery());
            String finalAnswer = ReactFinalAnswerResolver.resolve(executor, runResult);
            dynamicContext.setFinalAnswer(finalAnswer);
            return router(requestParameter, dynamicContext);
        } catch (UserInputRequiredException yield) {
            userQuestionYieldService.yieldAndNotify(
                    agentContext,
                    executor,
                    requestParameter,
                    yield,
                    ExecutionLedgerConstants.ENTRY_AGENT_REACT
            );
            dynamicContext.setWaitingUserInput(Boolean.TRUE);
            return router(requestParameter, dynamicContext);
        } catch (PlanApprovalRequiredException yield) {
            planApprovalYieldService.yieldAndNotify(
                    agentContext,
                    executor,
                    requestParameter,
                    yield,
                    ExecutionLedgerConstants.ENTRY_AGENT_REACT
            );
            dynamicContext.setWaitingUserInput(Boolean.TRUE);
            return router(requestParameter, dynamicContext);
        }
    }

    @Override
    public StrategyHandler<AgentRequest, DefaultReactAgentExecuteStrategyFactory.DynamicContext, String> get(
            AgentRequest requestParameter,
            DefaultReactAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        if (dynamicContext != null && Boolean.TRUE.equals(dynamicContext.getWaitingUserInput())) {
            return null;
        }
        return closeTurnNode;
    }
}
