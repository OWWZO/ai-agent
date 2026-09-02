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
import org.wwz.ai.domain.agent.memory.SessionWorkingMemoryService;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import java.util.List;
import org.wwz.ai.application.agent.askuser.AskUserResumeApplicationService;
import org.wwz.ai.application.agent.planmode.PlanApprovalResumeApplicationService;
import org.wwz.ai.domain.agent.runtime.askuser.IUserQuestionRepository;
import org.wwz.ai.domain.agent.runtime.planmode.IPlanApprovalRepository;
import org.wwz.ai.domain.agent.runtime.cancel.ActiveAgentRunRegistry;
import org.wwz.ai.domain.agent.runtime.tasklist.SessionBackgroundTaskHub;

import org.wwz.ai.domain.agent.service.execute.planexecute.step.factory.DefaultPlanSolveAgentExecuteStrategyFactory;
import org.apache.commons.lang3.StringUtils;

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
    private IPlanApprovalRepository planApprovalRepository;

    @Override
    public void execute(AgentRequest request, AgentSessionStream stream) throws Exception {
        // PlanSolve 与 ReAct 共用跨轮记忆 hydrate，但执行工厂负责规划、执行两阶段的具体编排。
        enrichWorkingMemory(request);
        StrategyHandler<AgentRequest, DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext, String> executeHandler
                = defaultPlanSolveAgentExecuteStrategyFactory.armoryStrategyHandler();
        DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext dynamicContext =
                DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext.builder()
                        .printer(new AgentSessionPrinter(stream, request, request.getAgentType()))
                        .build();
        // 让 stop 入口可以通过 requestId 定位当前 run；finally 中统一释放，避免取消后残留活动记录。
        // 同一 visitor 已有活跃 run 时 begin 会拒绝，避免多会话并发。
        activeAgentRunRegistry.begin(
                request.getRequestId(),
                request.getSessionId(),
                request.getVisitorId());
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
        } finally {
            if (!SessionBackgroundTaskHub.hasRunning(request.getSessionId())) {
                activeAgentRunRegistry.end(request.getRequestId());
            } else {
                log.info("{} defer ActiveAgentRunRegistry.end: background tasks still running",
                        request.getRequestId());
            }
        }
    }

    private void enrichWorkingMemory(AgentRequest request) {
        if (request == null) {
            return;
        }
        List<Message> working = List.of();
        if (sessionWorkingMemoryService != null) {
            // working_memory 是默认跨轮上下文来源，只有投影不可用时才回退到 ledger hydrate。
            working = sessionWorkingMemoryService.loadReadyMessages(request.getSessionId(), request.getRequestId());
        }
        // 冷启动/无投影时回退 ledger hydrate，保证首批会话仍有跨轮上下文
        if ((working == null || working.isEmpty()) && sessionContextMemoryService != null) {
            working = sessionContextMemoryService.hydrateWorkingMessages(request.getSessionId(), request.getRequestId());
        }
        if (StringUtils.isNotBlank(request.getResumeQuestionId()) && userQuestionRepository != null) {
            working = AskUserResumeApplicationService.appendAnswerObservation(
                    working,
                    userQuestionRepository.findByQuestionId(request.getResumeQuestionId()).orElse(null)
            );
        }
        if (StringUtils.isNotBlank(request.getResumeApprovalId()) && planApprovalRepository != null) {
            working = PlanApprovalResumeApplicationService.appendDecisionObservation(
                    working,
                    planApprovalRepository.findByApprovalId(request.getResumeApprovalId()).orElse(null)
            );
        }
        request.setWorkingMemoryMessages(working == null ? List.of() : working);
        request.setHistoryDialogue("");
    }
}
