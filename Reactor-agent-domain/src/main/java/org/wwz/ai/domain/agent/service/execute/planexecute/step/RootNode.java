package org.wwz.ai.domain.agent.service.execute.planexecute.step;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.service.execute.planexecute.step.factory.DefaultPlanSolveAgentExecuteStrategyFactory;

/**
 * PlanSolve 逻辑树根节点
 */
@Slf4j
@Service("planSolveRootNode")
public class RootNode extends AbstractExecuteSupport {

    @Resource
    private Step1SopRecallAndPrepareNode step1SopRecallAndPrepareNode;

    @Override
    protected String doApply(AgentRequest requestParameter, DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("PlanSolve RootNode start for requestId: {}", requestParameter.getRequestId());
        // 根节点不执行业务，只初始化步骤游标并把请求交给 SOP/上下文准备节点；这样树的
        // 入口和“准备上下文”职责分离，后续节点可以沿用统一的 DynamicContext 路由约定。
        dynamicContext.setStep(0);
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<AgentRequest, DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext, String> get(
            AgentRequest requestParameter,
            DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return step1SopRecallAndPrepareNode;
    }
}
