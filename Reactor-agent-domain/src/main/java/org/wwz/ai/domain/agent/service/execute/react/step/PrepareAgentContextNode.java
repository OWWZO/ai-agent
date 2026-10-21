package org.wwz.ai.domain.agent.service.execute.react.step;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.factory.AgentToolCollectionFactory;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceService;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceSessionFileMaterializer;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceReadStateStore;
import org.wwz.ai.domain.agent.runtime.util.DateUtil;
import org.wwz.ai.domain.agent.runtime.command.AgentExecutionCommand;
import org.wwz.ai.domain.agent.runtime.command.AgentExecutionFile;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.service.execute.react.step.factory.DefaultReactAgentExecuteStrategyFactory;
import org.wwz.ai.domain.agent.ledger.AgentExecutionRecorder;
import org.wwz.ai.domain.agent.ledger.ExecutionLedgerRunSupport;
import org.wwz.ai.domain.agent.memory.ltm.LtmRuntimeBootstrap;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * React 逻辑树 - Prepare：准备 AgentContext / 工具 / 账本
 */
@Slf4j
@Service("reactPrepareAgentContextNode")
public class PrepareAgentContextNode extends AbstractExecuteSupport {

    @Resource
    private AgentToolCollectionFactory agentToolCollectionFactory;

    @Resource
    private WorkspaceService workspaceService;

    @Resource
    private WorkspaceSessionFileMaterializer workspaceSessionFileMaterializer;

    @Resource
    private WorkspaceReadStateStore workspaceReadStateStore;

    @Resource
    private RunReactLoopNode runReactLoopNode;

    @Resource
    private AgentExecutionRecorder agentExecutionRecorder;

    @Resource
    private ReactorRuntimeDependencies reactorRuntimeDependencies;

    @Resource
    private org.wwz.ai.domain.agent.runtime.cancel.ActiveAgentRunRegistry activeAgentRunRegistry;

    @Resource
    private org.wwz.ai.domain.agent.runtime.capability.SessionCapabilityService sessionCapabilityService;

    @Override
    protected String doApply(AgentExecutionCommand request, DefaultReactAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("React Prepare: context and tools for requestId: {}", request.getRequestId());

        // 根节点只负责把请求翻译成运行时上下文，并把后续节点需要的能力装配齐；它是
        // 请求边界进入 runtime 的唯一准备点，后续节点不应重新读取原始请求来拼装上下文。
        Printer printer = dynamicContext.getPrinter();

        var disabled = sessionCapabilityService.loadDisabled(request.getSessionId());
        AgentContext agentContext = AgentContext.builder()
                .requestId(request.getRequestId())
                .sessionId(request.getSessionId())
                .userId(request.getUserId())
                .printer(printer)
                .query(request.getQuery())
                .task("")
                .model(request.getModel())
                .thinking(request.getThinking())
                .thinkingEffort(request.getThinkingEffort())
                .disabledSkillNames(disabled.skills())
                .disabledMcpIds(disabled.mcps())
                .dateInfo(DateUtil.CurrentDateInfo())
                .productFiles(new ArrayList<>(convertFiles(request.getSessionFiles())))
                .workspaceRoot(resolveWorkspaceRoot(request.getSessionId()))
                .sopPrompt(request.getSopPrompt())
                .basePrompt(request.getBasePrompt())
                .workingMemoryMessages(request.getWorkingMemoryMessages())
                .agentType(request.getAgentType())
                .isStream(Objects.nonNull(request.getIsStream()) ? request.getIsStream() : false)
                .templateType("dataAgent".equals(request.getOutputStyle()) ? "table" : "empty")
                .executionRecorder(agentExecutionRecorder)
                .runtimeDependencies(reactorRuntimeDependencies)
                .build();

        materializeSessionFiles(agentContext, request.getSessionFiles());
        hydrateWorkspaceReadState(agentContext);
        restoreResumePlanMode(agentContext, request);
        LtmRuntimeBootstrap.bootstrap(agentContext, request);

        // 运行账本记录本轮执行事实；working memory 只在本轮结束后作为下一轮 prompt 投影，
        // 因此这里初始化 run 但不把历史消息重新写入旧 message/transcript 主账本。
        ExecutionLedgerRunSupport.initializeRun(
                agentExecutionRecorder,
                agentContext,
                request,
                ExecutionLedgerConstants.ENTRY_AGENT_REACT
        );
        agentContext.setToolCollection(buildToolCollection(agentContext, request));
        bindActiveRunIfPresent(agentContext);
        dynamicContext.setAgentContext(agentContext);
        return router(request, dynamicContext);
    }

    private void bindActiveRunIfPresent(AgentContext agentContext) {
        if (activeAgentRunRegistry == null || agentContext == null) {
            return;
        }
        activeAgentRunRegistry.bindContext(agentContext.getRequestId(), agentContext);
    }

    private ToolCollection buildToolCollection(AgentContext agentContext, AgentExecutionCommand request) {
        // 工具集合必须以已完成初始化的 AgentContext 为输入，确保工具看到相同的 session、
        // workspace、取消信号和执行记录器，而不是各自从请求参数重新推导运行状态。
        return agentToolCollectionFactory.buildForReact(agentContext, request);
    }




    @jakarta.annotation.Resource
    private org.wwz.ai.domain.agent.runtime.planmode.IPlanApprovalRepository planApprovalRepository;

    @jakarta.annotation.Resource
    private org.wwz.ai.domain.agent.runtime.planmode.PlanArtifactStore planArtifactStore;

    private void restoreResumePlanMode(AgentContext agentContext, AgentExecutionCommand request) {
        if (agentContext == null || request == null || org.apache.commons.lang3.StringUtils.isBlank(request.getResumeContextJson())) {
            return;
        }
        org.wwz.ai.domain.agent.runtime.askuser.UserQuestionResumeContext
                .fromJson(request.getResumeContextJson())
                .applyPlanModeTo(agentContext);
        applyPlanApprovalDecision(agentContext, request);
    }

    private void applyPlanApprovalDecision(AgentContext agentContext, AgentExecutionCommand request) {
        if (agentContext == null || request == null
                || org.apache.commons.lang3.StringUtils.isBlank(request.getResumeApprovalId())
                || planApprovalRepository == null) {
            return;
        }
        var record = planApprovalRepository.findByApprovalId(request.getResumeApprovalId()).orElse(null);
        if (record == null || record.getDecision() == null) {
            return;
        }
        var state = agentContext.requirePlanModeState();
        var decision = record.getDecision();
        if (decision.isApproved()) {
            String finalPlan = org.apache.commons.lang3.StringUtils.isNotBlank(decision.getEditedPlanContent())
                    ? decision.getEditedPlanContent()
                    : record.getPlanContent();
            String planFilePath = record.getPlanFilePath();
            if (planArtifactStore != null && org.apache.commons.lang3.StringUtils.isNotBlank(finalPlan)) {
                planFilePath = planArtifactStore.writePlan(agentContext.getSessionId(), finalPlan)
                        .orElse(planFilePath);
            }
            state.setPlan(finalPlan, planFilePath);
            state.exitPlanMode();
        } else {
            state.clearPendingApproval();
        }
    }

    private void hydrateWorkspaceReadState(AgentContext agentContext) {
        if (workspaceReadStateStore == null || agentContext == null) {
            return;
        }
        try {
            workspaceReadStateStore.hydrate(agentContext);
        } catch (Exception e) {
            log.warn("hydrate workspace read-state failed, requestId={}", agentContext.getRequestId(), e);
        }
    }

    private void materializeSessionFiles(AgentContext agentContext, java.util.List<AgentExecutionFile> sessionFiles) {
        if (workspaceSessionFileMaterializer == null) {
            return;
        }
        try {
            // 文件物化是 best-effort：文件服务异常不应阻止纯文本请求继续执行，工具后续仍可
            // 通过稳定引用或显式错误信息决定是否使用该文件。
            workspaceSessionFileMaterializer.materialize(agentContext, sessionFiles);
        } catch (Exception e) {
            log.warn("materialize session files failed, requestId={}", agentContext == null ? null : agentContext.getRequestId(), e);
        }
    }

    private String resolveWorkspaceRoot(String sessionId) {
        if (workspaceService == null || !workspaceService.isEnabled()) {
            return null;
        }
        try {
            return workspaceService.resolveAndEnsureRoot(sessionId).toString();
        } catch (Exception e) {
            log.warn("resolve workspace root failed, sessionId={}", sessionId, e);
            return null;
        }
    }

    private List<File> convertFiles(List<AgentExecutionFile> sessionFiles) {
        if (sessionFiles == null || sessionFiles.isEmpty()) {
            return List.of();
        }
        List<File> files = new ArrayList<>(sessionFiles.size());
        for (AgentExecutionFile sessionFile : sessionFiles) {
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


    @Override
    public StrategyHandler<AgentExecutionCommand, DefaultReactAgentExecuteStrategyFactory.DynamicContext, String> get(
            AgentExecutionCommand requestParameter,
            DefaultReactAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return runReactLoopNode;
    }
}
