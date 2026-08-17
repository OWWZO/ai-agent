package org.wwz.ai.domain.agent.service.execute.react.step;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.ReactFinalAnswerResolver;
import org.wwz.ai.domain.agent.runtime.agent.ReactImplAgent;
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

    @Override
    protected String doApply(AgentRequest requestParameter,
                             DefaultReactAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("React Run: loop for requestId: {}", requestParameter.getRequestId());

        AgentContext agentContext = dynamicContext.getAgentContext();
        if (agentContext == null) {
            throw new IllegalStateException("React Run: agentContext is null, Prepare must run first.");
        }

        ReactImplAgent executor = new ReactImplAgent(agentContext);
        PlanModePromptInjector.applyIfPlanMode(agentContext, executor);
        String runResult = executor.run(requestParameter.getQuery());
        String finalAnswer = ReactFinalAnswerResolver.resolve(executor, runResult);

        dynamicContext.setExecutor(executor);
        dynamicContext.setFinalAnswer(finalAnswer);
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<AgentRequest, DefaultReactAgentExecuteStrategyFactory.DynamicContext, String> get(
            AgentRequest requestParameter,
            DefaultReactAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return closeTurnNode;
    }
}
