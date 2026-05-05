package org.wwz.ai.application.agent.execute.planexecute;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.wwz.ai.application.agent.execute.IExecuteStrategy;
import org.wwz.ai.application.agent.stream.AgentSessionPrinter;
import org.wwz.ai.application.agent.stream.AgentSessionStream;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.ledger.ExecutionLedgerRunSupport;
import org.wwz.ai.domain.agent.memory.SessionContextMemoryService;
import org.wwz.ai.domain.agent.service.execute.planexecute.step.factory.DefaultPlanSolveAgentExecuteStrategyFactory;

/**
 * PlanSolve 应用层执行策略。
 */
@Slf4j
@Service("planSolveAgentExecuteStrategy")
public class PlanSolveAgentExecuteStrategy implements IExecuteStrategy {

    @Resource
    private DefaultPlanSolveAgentExecuteStrategyFactory defaultPlanSolveAgentExecuteStrategyFactory;

    @Resource
    private SessionContextMemoryService sessionContextMemoryService;

    @Override
    public void execute(AgentRequest request, AgentSessionStream stream) throws Exception {
        enrichHistoryDialogue(request);
        StrategyHandler<AgentRequest, DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext, String> executeHandler
                = defaultPlanSolveAgentExecuteStrategyFactory.armoryStrategyHandler();
        DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext dynamicContext =
                DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext.builder()
                        .printer(new AgentSessionPrinter(stream, request, request.getAgentType()))
                        .build();
        try {
            String result = executeHandler.apply(request, dynamicContext);
            log.info("PlanSolveAgent execute result: {}", result);
        } catch (Exception e) {
            ExecutionLedgerRunSupport.finishRun(
                    dynamicContext.getAgentContext(),
                    ExecutionLedgerConstants.STATUS_FAILED,
                    null,
                    "PLAN_SOLVE_EXECUTE_ERROR",
                    e == null ? null : e.getMessage()
            );
            throw e;
        }
    }

    private void enrichHistoryDialogue(AgentRequest request) {
        if (request == null) {
            return;
        }
        request.setHistoryDialogue(sessionContextMemoryService == null
                ? ""
                : sessionContextMemoryService.buildHistoryDialogue(request.getSessionId(), request.getRequestId()));
    }
}
