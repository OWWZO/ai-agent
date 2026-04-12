package org.wwz.ai.domain.agent.service.execute.react.step;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.reactor.agent.agent.AgentContext;
import org.wwz.ai.domain.agent.reactor.agent.tool.ToolCollection;
import org.wwz.ai.domain.agent.reactor.agent.tool.factory.AgentToolCollectionFactory;
import org.wwz.ai.domain.agent.reactor.agent.util.DateUtil;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.reactor.agent.printer.Printer;
import org.wwz.ai.domain.agent.reactor.agent.printer.SSEPrinter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.domain.agent.service.execute.react.step.factory.DefaultReactAgentExecuteStrategyFactory;

import java.util.ArrayList;
import java.util.Objects;

/**
 * React 逻辑树 - 步骤1：准备上下文与工具（AgentContext、AgentRequest、ToolCollection）
 */
@Slf4j
@Service("reactRootNode")
public class RootNode extends AbstractExecuteSupport {

    @Resource
    private AgentToolCollectionFactory agentToolCollectionFactory;

    @Resource
    private RunReactNode step2RunReactNode;

    @Override
    protected String doApply(AgentRequest request, DefaultReactAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("React Step1: Prepare context and tools for requestId: {}", request.getRequestId());

        dynamicContext.setStep(0);
        Printer printer = new SSEPrinter(
                (SseEmitter) dynamicContext.getEmitter(),
                request,
                request.getAgentType()
        );

        AgentContext agentContext = AgentContext.builder()
                .requestId(request.getRequestId())
                .sessionId(request.getRequestId())
                .printer(printer)
                .query(request.getQuery())
                .task("")
                .dateInfo(DateUtil.CurrentDateInfo())
                .productFiles(new ArrayList<>())
                .taskProductFiles(new ArrayList<>())
                .sopPrompt(request.getSopPrompt())
                .basePrompt(request.getBasePrompt())
                .agentType(request.getAgentType())
                .isStream(Objects.nonNull(request.getIsStream()) ? request.getIsStream() : false)
                .templateType("dataAgent".equals(request.getOutputStyle()) ? "fix" : "empty")
                .build();

        agentContext.setToolCollection(buildToolCollection(agentContext, request));
        dynamicContext.setAgentContext(agentContext);
        dynamicContext.setStep(1);

        return router(request, dynamicContext);
    }

    private ToolCollection buildToolCollection(AgentContext agentContext, AgentRequest request) {
        return agentToolCollectionFactory.buildForReact(agentContext, request);
    }

    @Override
    public StrategyHandler<AgentRequest, DefaultReactAgentExecuteStrategyFactory.DynamicContext, String> get(
            AgentRequest requestParameter,
            DefaultReactAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return step2RunReactNode;
    }
}
