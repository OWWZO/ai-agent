package org.wwz.ai.application.agent.execute.planexecute;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.wwz.ai.application.agent.execute.IExecuteStrategy;
import org.wwz.ai.application.agent.execute.WorkingMemoryHydration;
import org.wwz.ai.application.agent.stream.AgentSessionPrinter;
import org.wwz.ai.application.agent.stream.AgentSessionStream;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.runtime.command.AgentExecutionCommand;
import org.wwz.ai.domain.agent.ledger.ExecutionLedgerRunSupport;
import org.wwz.ai.domain.agent.memory.SessionContextMemoryService;
import org.wwz.ai.domain.agent.memory.SessionWorkingMemoryService;
import org.wwz.ai.domain.agent.runtime.askuser.IUserQuestionRepository;
import org.wwz.ai.domain.agent.runtime.desktopcontrol.IDesktopControlRepository;
import org.wwz.ai.domain.agent.runtime.planmode.IPlanApprovalRepository;
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
    private ActiveAgentRunRegistry activeAgentRunRegistry;

    @Resource
    private IUserQuestionRepository userQuestionRepository;

    @Resource
    private IDesktopControlRepository desktopControlRepository;

    @Resource
    private IPlanApprovalRepository planApprovalRepository;

    @Override
    public void execute(AgentExecutionCommand request, AgentSessionStream stream) throws Exception {
        // PlanSolve 与 ReAct 共用跨轮记忆 hydrate，但执行工厂负责规划、执行两阶段的具体编排。
        enrichWorkingMemory(request);
        StrategyHandler<AgentExecutionCommand, DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext, String> executeHandler
                = defaultPlanSolveAgentExecuteStrategyFactory.armoryStrategyHandler();
        DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext dynamicContext =
                DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext.builder()
                        .printer(new AgentSessionPrinter(stream, request, request.getAgentType()))
                        .build();
        // 让 stop 入口可以通过 requestId 定位当前 run；finally 中统一释放，避免取消后残留活动记录。
        // 同一 userId 已有冲突 run 时 begin 会拒绝，避免同会话并发。
        activeAgentRunRegistry.begin(
                request.getRequestId(),
                request.getSessionId(),
                request.getUserId());
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
            // 取消是用户行为，普通异常是系统失败；两者必须分别写入 ledger 终态。
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
        }
    }

    private void enrichWorkingMemory(AgentExecutionCommand request) {
        // hydrate 逻辑与 ReactAgentExecuteStrategy 共用：优先 working_memory 投影，空则回退 ledger，再追加 resume 观测。
        WorkingMemoryHydration.enrich(
                request,
                sessionWorkingMemoryService,
                sessionContextMemoryService,
                userQuestionRepository,
                desktopControlRepository,
                planApprovalRepository);
    }
}
