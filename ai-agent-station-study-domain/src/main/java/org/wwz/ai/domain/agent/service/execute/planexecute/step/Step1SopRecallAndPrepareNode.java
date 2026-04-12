package org.wwz.ai.domain.agent.service.execute.planexecute.step;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.domain.agent.reactor.agent.agent.AgentContext;
import org.wwz.ai.domain.agent.reactor.agent.dto.SopRecallResponse;
import org.wwz.ai.domain.agent.reactor.agent.printer.Printer;
import org.wwz.ai.domain.agent.reactor.agent.printer.SSEPrinter;
import org.wwz.ai.domain.agent.reactor.agent.tool.ToolCollection;
import org.wwz.ai.domain.agent.reactor.agent.tool.factory.AgentToolCollectionFactory;
import org.wwz.ai.domain.agent.reactor.agent.util.DateUtil;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.reactor.service.SopRecallService;
import org.wwz.ai.domain.agent.service.execute.planexecute.step.factory.DefaultPlanSolveAgentExecuteStrategyFactory;

import java.util.ArrayList;
import java.util.Objects;

/**
 * PlanSolve 逻辑树 - 步骤1：SOP召回 + 准备 AgentContext 与工具
 */
@Slf4j
@Service
public class Step1SopRecallAndPrepareNode extends AbstractExecuteSupport {

    @Resource
    private AgentToolCollectionFactory agentToolCollectionFactory;

    @Resource
    private SopRecallService sopRecallService;

    @Resource
    private Step2PlanExecuteNode step2PlanExecuteNode;

    @Override
    protected String doApply(AgentRequest request, DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("PlanSolve Step1: SOP recall and prepare for requestId: {}", request.getRequestId());

        Printer printer = new SSEPrinter(
                dynamicContext.getEmitter(),
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
        handleSopRecall(agentContext, request);

        dynamicContext.setAgentContext(agentContext);
        dynamicContext.setStep(1);

        return router(request, dynamicContext);
    }

    private void handleSopRecall(AgentContext agentContext, AgentRequest request) {
        try {
            log.info("{} 开始执行SOP召回", request.getRequestId());
            SopRecallResponse sopResponse = sopRecallService.sopRecall(request.getRequestId(), request.getQuery());
            if (sopRecallService.isValidSopResult(sopResponse)) {
                String sopContent = sopResponse.getData().getChoosed_sop_string();
                String sopMode = sopResponse.getData().getSop_mode();
                log.info("{} SOP召回成功，模式：{}，内容长度：{}", request.getRequestId(), sopMode, sopContent.length());
                if (agentContext.getSopPrompt() != null) {
                    String sopPrompt = agentContext.getSopPrompt().replace("{{sop}}", sopContent);
                    agentContext.setSopPrompt(sopPrompt);
                }
            } else {
                log.warn("{} SOP召回失败或结果无效", request.getRequestId());
            }
        } catch (Exception e) {
            log.error("{} SOP召回处理异常", request.getRequestId(), e);
        }
    }

    private ToolCollection buildToolCollection(AgentContext agentContext, AgentRequest request) {
        return agentToolCollectionFactory.buildForPlanSolve(agentContext, request);
    }

    @Override
    public StrategyHandler<AgentRequest, DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext, String> get(
            AgentRequest requestParameter,
            DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return step2PlanExecuteNode;
    }
}
