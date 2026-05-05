package org.wwz.ai.application.agent.execute.react;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.application.agent.execute.IExecuteStrategy;
import org.wwz.ai.application.agent.stream.AgentSessionPrinter;
import org.wwz.ai.application.agent.stream.AgentSessionStream;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.ledger.ExecutionLedgerRunSupport;
import org.wwz.ai.domain.agent.memory.SessionContextMemoryService;
import org.wwz.ai.domain.agent.service.execute.react.step.factory.DefaultReactAgentExecuteStrategyFactory;

import java.util.Map;

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
    private ReactorConfig reactorConfig;

    @Resource
    private SessionContextMemoryService sessionContextMemoryService;

    @Override
    public void execute(AgentRequest request, AgentSessionStream stream) throws Exception {
        enrichHistoryDialogue(request);
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

        try {
            String result = executeHandler.apply(request, dynamicContext);
            log.info("ReactAgent execute result: {}", result);
        } catch (Exception e) {
            ExecutionLedgerRunSupport.finishRun(
                    dynamicContext.getAgentContext(),
                    ExecutionLedgerConstants.STATUS_FAILED,
                    null,
                    "REACT_EXECUTE_ERROR",
                    e == null ? null : e.getMessage()
            );
            throw e;
        }
    }

    private void applyOutputStyle(AgentRequest request) {
        Map<String, String> outputStyleMap = reactorConfig.getOutputStylePrompts();
        if (StringUtils.isNotEmpty(request.getOutputStyle())) {
            String append = outputStyleMap.computeIfAbsent(request.getOutputStyle(), k -> "");
            request.setQuery(request.getQuery() + append);
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
