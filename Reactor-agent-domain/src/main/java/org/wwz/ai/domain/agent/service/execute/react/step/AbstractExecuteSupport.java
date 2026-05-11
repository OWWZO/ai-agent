package org.wwz.ai.domain.agent.service.execute.react.step;

import cn.bugstack.wrench.design.framework.tree.AbstractMultiThreadStrategyRouter;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.service.execute.react.step.factory.DefaultReactAgentExecuteStrategyFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * React 链路抽象节点基类，与 flow/auto 的 AbstractExecuteSupport 同构：
 * 使用 AgentRequest + DynamicContext 驱动逻辑树，直接复用请求对象
 */
public abstract class AbstractExecuteSupport extends AbstractMultiThreadStrategyRouter<AgentRequest, DefaultReactAgentExecuteStrategyFactory.DynamicContext, String> {

    @Override
    protected void multiThread(AgentRequest requestParameter, DefaultReactAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws ExecutionException, InterruptedException, TimeoutException {
        // React 链暂无多线程扩展点，可后续按需补充
    }
}
