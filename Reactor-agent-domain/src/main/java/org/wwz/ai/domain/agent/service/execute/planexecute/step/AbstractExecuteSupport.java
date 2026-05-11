package org.wwz.ai.domain.agent.service.execute.planexecute.step;

import cn.bugstack.wrench.design.framework.tree.AbstractMultiThreadStrategyRouter;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.service.execute.planexecute.step.factory.DefaultPlanSolveAgentExecuteStrategyFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * PlanSolve 链路抽象节点基类，与 react 同构
 */
public abstract class AbstractExecuteSupport extends AbstractMultiThreadStrategyRouter<AgentRequest, DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext, String> {

    @Override
    protected void multiThread(AgentRequest requestParameter, DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws ExecutionException, InterruptedException, TimeoutException {
        // PlanSolve 链支持多线程执行子任务，扩展点可在 Step2PlanExecute 中实现
    }
}
