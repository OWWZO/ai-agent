package org.wwz.ai.application.agent.execute.react;

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

import org.wwz.ai.domain.agent.service.execute.react.step.factory.DefaultReactAgentExecuteStrategyFactory;

/**
 * React 应用层执行策略。
 * 负责会话记忆注入与输出端口适配，真正的运行时主循环仍由 domain 内核承接。
 */
@Slf4j
@Service("reactAgentExecuteStrategy")
public class ReactAgentExecuteStrategy implements IExecuteStrategy {

    @Resource
    private DefaultReactAgentExecuteStrategyFactory defaultReactAgentExecuteStrategyFactory;

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
        // 先 hydrate 跨轮工作记忆，再进入 Agent 内核；记忆加载失败不应改变 case 的执行边界。
        enrichWorkingMemory(request);
        doExecute(request, stream);
    }

    private void doExecute(AgentExecutionCommand request, AgentSessionStream stream) throws Exception {
        // 动态上下文承载协议无关 Printer，执行工厂负责创建真正的 AgentContext 和 ReAct 节点。
        StrategyHandler<AgentExecutionCommand, DefaultReactAgentExecuteStrategyFactory.DynamicContext, String> executeHandler
                = defaultReactAgentExecuteStrategyFactory.armoryStrategyHandler();

        DefaultReactAgentExecuteStrategyFactory.DynamicContext dynamicContext =
                DefaultReactAgentExecuteStrategyFactory.DynamicContext.builder()
                        .printer(new AgentSessionPrinter(stream, request, request.getAgentType()))
                        .build();

        // 注册活动 run 后，停止请求才能通过 requestId 找到同一条执行流；无论成功还是异常都必须解除注册。
        // 同一 userId 已有冲突 run 时 begin 会拒绝，避免同会话并发。
        activeAgentRunRegistry.begin(
                request.getRequestId(),
                request.getSessionId(),
                request.getUserId());
        activeAgentRunRegistry.bindStream(request.getRequestId(), stream);
        try {
            String result = executeHandler.apply(request, dynamicContext);
            log.info("ReactAgent execute result: {}", result);
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
            // 用户取消与系统失败使用不同账本终态，前端和历史回放据此区分 STOPPED 与 FAILED。
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
                        "REACT_EXECUTE_ERROR",
                        e == null ? null : e.getMessage()
                );
            }
            throw e;
        }
    }

    private void enrichWorkingMemory(AgentExecutionCommand request) {
        // hydrate 逻辑与 PlanSolveAgentExecuteStrategy 共用：优先 working_memory 投影，空则回退 ledger，再追加 resume 观测。
        WorkingMemoryHydration.enrich(
                request,
                sessionWorkingMemoryService,
                sessionContextMemoryService,
                userQuestionRepository,
                desktopControlRepository,
                planApprovalRepository);
    }
}
