package org.wwz.ai.domain.agent.service.execute.planexecute.step;

import cn.bugstack.wrench.design.framework.tree.AbstractMultiThreadStrategyRouter;
import org.wwz.ai.domain.agent.runtime.command.AgentExecutionCommand;
import org.wwz.ai.domain.agent.service.execute.planexecute.step.factory.DefaultPlanSolveAgentExecuteStrategyFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * PlanSolve 链路抽象节点基类，与 react 同构
 */
public abstract class AbstractExecuteSupport extends AbstractMultiThreadStrategyRouter<AgentExecutionCommand, DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext, String> {

    @Override
    protected void multiThread(AgentExecutionCommand requestParameter, DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws ExecutionException, InterruptedException, TimeoutException {
        // PlanSolve 主路径为单 React 代理，无节点级多线程扩展
    }
}
