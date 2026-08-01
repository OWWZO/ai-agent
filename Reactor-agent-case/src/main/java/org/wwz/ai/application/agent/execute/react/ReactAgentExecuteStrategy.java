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

    @Override
    public void execute(AgentRequest request, AgentSessionStream stream) throws Exception {
        enrichWorkingMemory(request);
        applyOutputStyle(request);
        doExecute(request, stream);
    }

    private void doExecute(AgentRequest request, AgentSessionStream stream) throws Exception {
        StrategyHandler<AgentRequest, DefaultReactAgentExecuteStrategyFactory.DynamicContext, String> executeHandler
                = defaultReactAgentExecuteStrategyFactory.armoryStrategyHandler();

        DefaultReactAgentExecuteStrategyFactory.DynamicContext dynamicContext =
                DefaultReactAgentExecuteStrategyFactory.DynamicContext.builder()
                        .printer(new AgentSessionPrinter(stream, request, request.getAgentType()))
                        .build();

        activeAgentRunRegistry.begin(request.getRequestId(), request.getSessionId());
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
