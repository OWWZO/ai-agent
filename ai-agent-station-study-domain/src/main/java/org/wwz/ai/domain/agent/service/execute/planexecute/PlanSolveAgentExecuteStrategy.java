package org.wwz.ai.domain.agent.service.execute.planexecute;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.domain.agent.reactor.agent.printer.SSEPrinter;
import org.wwz.ai.domain.agent.reactor.model.ledger.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.reactor.service.ExecutionLedgerRunSupport;
import org.wwz.ai.domain.agent.reactor.service.SessionContextMemoryService;
import org.wwz.ai.domain.agent.service.IExecuteStrategy;
import org.wwz.ai.domain.agent.service.execute.planexecute.step.factory.DefaultPlanSolveAgentExecuteStrategyFactory;

/**
 * PlanSolve Agent 执行策略：以 AgentRequest 贯穿逻辑树
 * 树形结构：RootNode → Step1SopRecallAndPrepare → Step2PlanExecute
 */
@Slf4j
public class PlanSolveAgentExecuteStrategy implements IExecuteStrategy {

    @Resource
    private DefaultPlanSolveAgentExecuteStrategyFactory defaultPlanSolveAgentExecuteStrategyFactory;

    @Resource
    private SessionContextMemoryService sessionContextMemoryService;

    /**
     * 执行 PlanSolve 逻辑树
     */
    @Override
    public void execute(AgentRequest request, SseEmitter emitter) throws Exception {
        enrichHistoryDialogue(request);
        StrategyHandler<AgentRequest, DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext, String> executeHandler
                = defaultPlanSolveAgentExecuteStrategyFactory.armoryStrategyHandler();
        DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext dynamicContext = DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext.builder()
                .printer(new SSEPrinter(emitter, request, request.getAgentType()))
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
        // AutoAgent 当前 execute(AgentRequest, SseEmitter) 为空实现，且 auto 链已有独立 chat memory 机制；
        // workflow/FlowAgentExecuteStrategy 使用 Spring AI chat memory，不走 historyDialogue 注入。
        // 因此本期只在 React / PlanSolve 两条链路接入单会话记忆注入。
        request.setHistoryDialogue(sessionContextMemoryService == null
                ? ""
                : sessionContextMemoryService.buildHistoryDialogue(request.getSessionId(), request.getRequestId()));
    }
}
