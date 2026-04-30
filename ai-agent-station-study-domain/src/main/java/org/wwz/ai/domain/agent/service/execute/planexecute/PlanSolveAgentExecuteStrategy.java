package org.wwz.ai.domain.agent.service.execute.planexecute;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.domain.agent.reactor.agent.agent.AgentContext;
import org.wwz.ai.domain.agent.reactor.model.ledger.DialogueRunFinishRecord;
import org.wwz.ai.domain.agent.reactor.model.ledger.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.service.IExecuteStrategy;
import org.wwz.ai.domain.agent.service.execute.planexecute.step.factory.DefaultPlanSolveAgentExecuteStrategyFactory;

/**
 * PlanSolve Agent 执行策略：以 AgentRequest 贯穿逻辑树
 * 树形结构：RootNode → Step1SopRecallAndPrepare → Step2PlanExecute
 */
@Slf4j
@Service("planSolveAgentExecuteStrategy")
public class PlanSolveAgentExecuteStrategy implements IExecuteStrategy {

    @Resource
    private DefaultPlanSolveAgentExecuteStrategyFactory defaultPlanSolveAgentExecuteStrategyFactory;

    /**
     * 执行 PlanSolve 逻辑树
     */
    @Override
    public void execute(AgentRequest request, SseEmitter emitter) throws Exception {
        StrategyHandler<AgentRequest, DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext, String> executeHandler
                = defaultPlanSolveAgentExecuteStrategyFactory.armoryStrategyHandler();
        DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext dynamicContext = DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext.builder()
                .emitter(emitter)
                .build();
        try {
            String result = executeHandler.apply(request, dynamicContext);
            log.info("PlanSolveAgent execute result: {}", result);
        } catch (Exception e) {
            finishRunOnFailure(dynamicContext.getAgentContext(), "PLAN_SOLVE_EXECUTE_ERROR", e);
            throw e;
        }
    }

    private void finishRunOnFailure(AgentContext agentContext, String errorCode, Exception e) {
        if (agentContext == null || !agentContext.hasActiveLedgerRun() || agentContext.getAgentRunState() == null) {
            return;
        }
        agentContext.getExecutionRecorder().finishRun(DialogueRunFinishRecord.builder()
                .runId(agentContext.getAgentRunState().getRunId())
                .requestId(agentContext.getRequestId())
                .status(ExecutionLedgerConstants.STATUS_FAILED)
                .errorCode(errorCode)
                .errorMsg(e == null ? null : e.getMessage())
                .build());
    }
}
