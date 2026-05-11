package org.wwz.ai.domain.agent.service.execute.planexecute.step;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.SopRecallResponse;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.factory.AgentToolCollectionFactory;
import org.wwz.ai.domain.agent.runtime.util.DateUtil;
import org.wwz.ai.domain.agent.reactor.model.dto.FileInformation;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.ledger.AgentExecutionRecorder;
import org.wwz.ai.domain.agent.ledger.ExecutionLedgerRunSupport;
import org.wwz.ai.domain.agent.rag.SopRecallService;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.service.execute.planexecute.step.factory.DefaultPlanSolveAgentExecuteStrategyFactory;

import java.util.ArrayList;
import java.util.List;
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

    @Resource
    private AgentExecutionRecorder agentExecutionRecorder;

    @Resource
    private ReactorRuntimeDependencies reactorRuntimeDependencies;

    @Override
    protected String doApply(AgentRequest request, DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("PlanSolve Step1: SOP recall and prepare for requestId: {}", request.getRequestId());

        Printer printer = dynamicContext.getPrinter();
        AgentContext agentContext = AgentContext.builder()
                .requestId(request.getRequestId())
                .sessionId(request.getSessionId())
                .printer(printer)
                .query(request.getQuery())
                .task("")
                .dateInfo(DateUtil.CurrentDateInfo())
                .productFiles(new ArrayList<>(convertFiles(request.getSessionFiles())))
                .taskProductFiles(new ArrayList<>())
                .sopPrompt(request.getSopPrompt())
                .basePrompt(request.getBasePrompt())
                .historyDialogue(request.getHistoryDialogue())
                .agentType(request.getAgentType())
                .isStream(Objects.nonNull(request.getIsStream()) ? request.getIsStream() : false)
                .templateType("dataAgent".equals(request.getOutputStyle()) ? "fix" : "empty")
                .executionRecorder(agentExecutionRecorder)
                .runtimeDependencies(reactorRuntimeDependencies)
                .build();

        ExecutionLedgerRunSupport.initializeRun(
                agentExecutionRecorder,
                agentContext,
                request,
                ExecutionLedgerConstants.ENTRY_AGENT_PLAN_SOLVE
        );
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

    private List<File> convertFiles(List<FileInformation> sessionFiles) {
        if (sessionFiles == null || sessionFiles.isEmpty()) {
            return List.of();
        }
        List<File> files = new ArrayList<>(sessionFiles.size());
        for (FileInformation sessionFile : sessionFiles) {
            files.add(File.builder()
                    .fileName(sessionFile.getFileName())
                    .description(sessionFile.getFileDesc())
                    .ossUrl(sessionFile.getOssUrl())
                    .domainUrl(sessionFile.getDomainUrl())
                    .fileSize(sessionFile.getFileSize())
                    .originFileName(sessionFile.getOriginFileName())
                    .originOssUrl(sessionFile.getOriginOssUrl())
                    .originDomainUrl(sessionFile.getOriginDomainUrl())
                    .isInternalFile(Boolean.FALSE)
                    .build());
        }
        return files;
    }

    private List<Message> convertMessages(List<AgentRequest.Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<Message> result = new ArrayList<>(messages.size());
        for (AgentRequest.Message message : messages) {
            result.add(convertMessage(message));
        }
        return result;
    }

    private Message convertMessage(AgentRequest.Message message) {
        RoleType role = resolveRoleType(message == null ? null : message.getRole());
        Message.MessageBuilder builder = Message.builder()
                .role(role)
                .content(message == null ? null : message.getContent());
        if (message != null && message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
            builder.toolCalls(message.getToolCalls());
        }
        if (message != null && Objects.nonNull(message.getToolCallId())) {
            builder.toolCallId(message.getToolCallId());
        }
        return builder.build();
    }

    private RoleType resolveRoleType(String role) {
        if ("assistant".equalsIgnoreCase(role)) {
            return RoleType.ASSISTANT;
        }
        if ("tool".equalsIgnoreCase(role)) {
            return RoleType.TOOL;
        }
        return RoleType.USER;
    }

    @Override
    public StrategyHandler<AgentRequest, DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext, String> get(
            AgentRequest requestParameter,
            DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return step2PlanExecuteNode;
    }
}
