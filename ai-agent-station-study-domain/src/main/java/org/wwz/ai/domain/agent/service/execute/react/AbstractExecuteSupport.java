package org.wwz.ai.domain.agent.service.execute.react;

import cn.bugstack.wrench.design.framework.tree.AbstractMultiThreadStrategyRouter;
import org.wwz.ai.domain.agent.genie.agent.agent.AgentContext;
import org.wwz.ai.domain.agent.model.entity.ExecuteCommandEntity;
import org.wwz.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public abstract class AbstractExecuteSupport extends AbstractMultiThreadStrategyRouter<ExecuteCommandEntity, AgentContext, String> {

    @Override
    protected void multiThread(ExecuteCommandEntity requestParameter, AgentContext dynamicContext) throws ExecutionException, InterruptedException, TimeoutException {

    }

}
