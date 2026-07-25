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
import org.wwz.ai.domain.agent.memory.SessionContextCompactionService;
import org.wwz.ai.domain.agent.memory.SessionContextMemoryService;
import org.wwz.ai.domain.agent.memory.SessionWorkingMemoryService;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import java.util.List;
import org.wwz.ai.domain.agent.runtime.cancel.ActiveAgentRunRegistry;
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

    @Resource
    private SessionWorkingMemoryService sessionWorkingMemoryService;

    @Resource
    private SessionContextCompactionService sessionContextCompactionService;

    @Resource
    private ActiveAgentRunRegistry activeAgentRunRegistry;

    @Override
    public void execute(AgentRequest request, AgentSessionStream stream) throws Exception {
        enrichWorkingMemory(request);
        StrategyHandler<AgentRequest, DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext, String> executeHandler
                = defaultPlanSolveAgentExecuteStrategyFactory.armoryStrategyHandler();
        DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext dynamicContext =
                DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext.builder()
                        .printer(new AgentSessionPrinter(stream, request, request.getAgentType()))
                        .build();
        activeAgentRunRegistry.begin(request.getRequestId(), request.getSessionId());
        activeAgentRunRegistry.bindStream(request.getRequestId(), stream);
        try {
            String result = executeHandler.apply(request, dynamicContext);
            log.info("PlanSolveAgent execute result: {}", result);
            if (dynamicContext.getAgentContext() != null
                    && dynamicContext.getAgentContext().isRunCancelled()) {
                ExecutionLedgerRunSupport.finishRun(
                        dynamicContext.getAgentContext(),
                        ExecutionLedgerConstants.STATUS_STOPPED,
                        null,
                        "USER_STOP",
                        "用户停止本轮对话");
            }
        } catch (Exception e) {
            if (dynamicContext.getAgentContext() != null
                    && dynamicContext.getAgentContext().isRunCancelled()) {
                ExecutionLedgerRunSupport.finishRun(
                        dynamicContext.getAgentContext(),
                        ExecutionLedgerConstants.STATUS_STOPPED,
                        null,
                        "USER_STOP",
                        e == null ? "用户停止本轮对话" : e.getMessage());
            } else {
                ExecutionLedgerRunSupport.finishRun(
                        dynamicContext.getAgentContext(),
                        ExecutionLedgerConstants.STATUS_FAILED,
                        null,
                        "PLAN_SOLVE_EXECUTE_ERROR",
                        e == null ? null : e.getMessage()
                );
            }
            throw e;
        } finally {
            activeAgentRunRegistry.end(request.getRequestId());
        }
    }

    private void enrichWorkingMemory(AgentRequest request) {
        if (request == null) {
            return;
        }
        List<Message> working = List.of();
        if (sessionWorkingMemoryService != null) {
            working = sessionWorkingMemoryService.loadReadyMessages(request.getSessionId(), request.getRequestId());
        }
        // 冷启动/无投影时回退 ledger hydrate，保证首批会话仍有跨轮上下文
        if ((working == null || working.isEmpty()) && sessionContextMemoryService != null) {
            working = sessionContextMemoryService.hydrateWorkingMessages(request.getSessionId(), request.getRequestId());
        }
        if (working != null && !working.isEmpty() && sessionContextCompactionService != null) {
            working = sessionContextCompactionService.applyIfNeeded(
                    request.getSessionId(), request.getRequestId(), working);
        }
        request.setWorkingMemoryMessages(working == null ? List.of() : working);
        request.setHistoryDialogue("");
    }
}
