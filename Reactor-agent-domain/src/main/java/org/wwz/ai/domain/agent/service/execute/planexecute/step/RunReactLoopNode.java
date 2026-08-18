package org.wwz.ai.domain.agent.service.execute.planexecute.step;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.ReactFinalAnswerResolver;
import org.wwz.ai.domain.agent.runtime.agent.ReactImplAgent;
import org.wwz.ai.domain.agent.runtime.askuser.UserInputRequiredException;
import org.wwz.ai.domain.agent.runtime.askuser.UserQuestionYieldService;
import org.wwz.ai.domain.agent.runtime.llm.LLM;
import org.wwz.ai.domain.agent.runtime.planmode.PlanApprovalRequiredException;
import org.wwz.ai.domain.agent.runtime.planmode.PlanApprovalYieldService;
import org.wwz.ai.domain.agent.runtime.planmode.PlanModePromptInjector;
import org.wwz.ai.domain.agent.service.execute.planexecute.step.factory.DefaultPlanSolveAgentExecuteStrategyFactory;

/**
 * PlanSolve 逻辑树 - Run：单主代理 React 循环（叠加 planner 约定 / 模型 / 步数）。
 */
@Slf4j
@Service("planSolveRunReactLoopNode")
public class RunReactLoopNode extends AbstractExecuteSupport {

    @Resource
    private ReactorConfig reactorConfig;

    @Resource
    private CloseTurnNode closeTurnNode;

    @Resource
    private UserQuestionYieldService userQuestionYieldService;

    @Resource
    private PlanApprovalYieldService planApprovalYieldService;

    @Override
    protected String doApply(AgentRequest requestParameter,
                             DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("PlanSolve Run: loop for requestId: {}", requestParameter.getRequestId());

        AgentContext agentContext = dynamicContext.getAgentContext();
        if (agentContext == null) {
            throw new IllegalStateException("PlanSolve Run: agentContext is null, Prepare must run first.");
        }

        ReactImplAgent planner = createPlanSolvePlanner(agentContext);
        dynamicContext.setExecutor(planner);
        try {
            String runResult = planner.run(agentContext.getQuery());
            String finalAnswer = ReactFinalAnswerResolver.resolve(planner, runResult);
            dynamicContext.setFinalAnswer(finalAnswer);
            return router(requestParameter, dynamicContext);
        } catch (UserInputRequiredException yield) {
            userQuestionYieldService.yieldAndNotify(
                    agentContext,
                    planner,
                    requestParameter,
                    yield,
                    ExecutionLedgerConstants.ENTRY_AGENT_PLAN_SOLVE
            );
            dynamicContext.setWaitingUserInput(Boolean.TRUE);
            return router(requestParameter, dynamicContext);
        } catch (PlanApprovalRequiredException yield) {
            planApprovalYieldService.yieldAndNotify(
                    agentContext,
                    planner,
                    requestParameter,
                    yield,
                    ExecutionLedgerConstants.ENTRY_AGENT_PLAN_SOLVE
            );
            dynamicContext.setWaitingUserInput(Boolean.TRUE);
            return router(requestParameter, dynamicContext);
        }
    }

    private ReactImplAgent createPlanSolvePlanner(AgentContext agentContext) {
        ReactImplAgent planner = new ReactImplAgent(agentContext);
        planner.setName("plan-solve");
        planner.setDescription("plan-execute main agent: plan mode, dispatch Agent subagents, final user reply");
        planner.setSystemPrompt(PlanModePromptInjector.ensurePlanSolveWithPlanModeGuidance(planner.getSystemPrompt()));
        PlanModePromptInjector.applyIfPlanMode(agentContext, planner);

        if (reactorConfig != null) {
            Integer maxSteps = reactorConfig.getPlannerMaxSteps();
            if (maxSteps != null && maxSteps > 0) {
                planner.setMaxSteps(maxSteps);
            }
            String plannerModel = reactorConfig.getPlannerModelName();
            if (StringUtils.isNotBlank(plannerModel) && agentContext.getRuntimeDependencies() != null) {
                planner.setLlm(new LLM(plannerModel, "", agentContext.getRuntimeDependencies()));
            }
        }
        return planner;
    }

    @Override
    public StrategyHandler<AgentRequest, DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext, String> get(
            AgentRequest requestParameter,
            DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        if (dynamicContext != null && Boolean.TRUE.equals(dynamicContext.getWaitingUserInput())) {
            return null;
        }
        return closeTurnNode;
    }
}
