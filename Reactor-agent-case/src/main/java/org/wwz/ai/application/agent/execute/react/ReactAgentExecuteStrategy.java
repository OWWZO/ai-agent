package org.wwz.ai.application.agent.execute.react;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
import org.wwz.ai.application.agent.askuser.AskUserResumeApplicationService;
import org.wwz.ai.application.agent.planmode.PlanApprovalResumeApplicationService;
import org.wwz.ai.domain.agent.runtime.askuser.IUserQuestionRepository;
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
    private SessionContextCompactionService sessionContextCompactionService;

    @Resource
    private ActiveAgentRunRegistry activeAgentRunRegistry;

    @Resource
    private IUserQuestionRepository userQuestionRepository;

    @Resource
    private IPlanApprovalRepository planApprovalRepository;

    @Override
    public void execute(AgentRequest request, AgentSessionStream stream) throws Exception {
        // 先 hydrate 跨轮工作记忆，再进入 Agent 内核；记忆加载失败不应改变 case 的执行边界。
        enrichWorkingMemory(request);
        applyOutputStyle(request);
        doExecute(request, stream);
    }

    private void doExecute(AgentRequest request, AgentSessionStream stream) throws Exception {
        // 动态上下文承载协议无关 Printer，执行工厂负责创建真正的 AgentContext 和 ReAct 节点。
        StrategyHandler<AgentRequest, DefaultReactAgentExecuteStrategyFactory.DynamicContext, String> executeHandler
                = defaultReactAgentExecuteStrategyFactory.armoryStrategyHandler();

        DefaultReactAgentExecuteStrategyFactory.DynamicContext dynamicContext =
                DefaultReactAgentExecuteStrategyFactory.DynamicContext.builder()
                        .printer(new AgentSessionPrinter(stream, request, request.getAgentType()))
                        .build();

        // 注册活动 run 后，停止请求才能通过 requestId 找到同一条执行流；无论成功还是异常都必须解除注册。
        // 同一 visitor 已有活跃 run 时 begin 会拒绝，避免多会话并发。
        activeAgentRunRegistry.begin(
                request.getRequestId(),
                request.getSessionId(),
                request.getVisitorId());
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
        } finally {
            activeAgentRunRegistry.end(request.getRequestId());
        }
    }

    /**
     * 输出格式（html/docs/ppt/table）已下线，不再向 query 追加格式提示词。
     * chat / dataAgent 仅作模式标记，不走 output_style_prompts。
     */
    private void applyOutputStyle(AgentRequest request) {
        if (request == null || StringUtils.isBlank(request.getOutputStyle())) {
            return;
        }
        String style = request.getOutputStyle().trim();
        if ("chat".equals(style) || "dataAgent".equals(style) || "task".equals(style)) {
            return;
        }
        // 兼容旧客户端若仍传 html/docs/ppt/table：忽略，不改写 query
        log.debug("ignore deprecated outputStyle={}", style);
    }

    private void enrichWorkingMemory(AgentRequest request) {
        if (request == null) {
            return;
        }
        List<Message> working = List.of();
        if (sessionWorkingMemoryService != null) {
            // 优先读取已持久化的 working_memory 投影，保持 prompt cache 友好的消息形状。
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
