package org.wwz.ai.domain.agent.service.execute.support;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.cancel.ActiveAgentRunRegistry;
import org.wwz.ai.domain.agent.runtime.capability.SessionCapabilityService;
import org.wwz.ai.domain.agent.runtime.command.AgentExecutionCommand;
import org.wwz.ai.domain.agent.runtime.command.AgentExecutionFile;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.planmode.IPlanApprovalRepository;
import org.wwz.ai.domain.agent.runtime.planmode.PlanArtifactStore;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.factory.AgentToolCollectionFactory;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceService;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceSessionFileMaterializer;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceReadStateStore;
import org.wwz.ai.domain.agent.runtime.util.DateUtil;
import org.wwz.ai.domain.agent.ledger.AgentExecutionRecorder;
import org.wwz.ai.domain.agent.ledger.ExecutionLedgerRunSupport;
import org.wwz.ai.domain.agent.memory.ltm.LtmRuntimeBootstrap;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * React / PlanSolve 共用的 AgentContext 装配体。
 * <p>
 * 两条链路各有自己的 PrepareAgentContextNode（wrench 策略树要求节点绑定各自的
 * DynamicContext 泛型），但「从请求翻译出运行时上下文」这一段是逐字相同的：
 * 字段映射、附件物化、workspace read-state hydrate、resume plan mode 恢复、
 * ledger run 初始化、活动 run 绑定。差异只剩三处，全部由参数显式传入：
 * 入口标识、工具集合构造入口、以及 PlanSolve 额外的主/子工具池分流。
 */
@Slf4j
@Component
public class PrepareAgentContextAssembly {

    @Resource
    private AgentExecutionRecorder agentExecutionRecorder;

    @Resource
    private SessionCapabilityService sessionCapabilityService;

    @Resource
    private AgentToolCollectionFactory agentToolCollectionFactory;

    @Resource
    private WorkspaceService workspaceService;

    @Resource
    private WorkspaceSessionFileMaterializer workspaceSessionFileMaterializer;

    @Resource
    private WorkspaceReadStateStore workspaceReadStateStore;

    @Resource
    private ReactorRuntimeDependencies reactorRuntimeDependencies;

    @Resource
    private ActiveAgentRunRegistry activeAgentRunRegistry;

    @Resource
    private IPlanApprovalRepository planApprovalRepository;

    @Resource
    private PlanArtifactStore planArtifactStore;

    /**
     * 装配结果：AgentContext 加上 resume 结论（PlanSolve 据此判定是否自动进入 plan mode）。
     */
    public record Assembled(AgentContext context, boolean resumedApprovedPlan) {
    }

    /**
     * 两条链路的差异点：入口标识与工具池构造方式。
     *
     * @param entryAgent  ledger run 的 entryAgent（ENTRY_AGENT_REACT / ENTRY_AGENT_PLAN_SOLVE）
     * @param planSolve   PlanSolve 链路需分流主/子工具池，React 链路只有一套
     */
    public Assembled assemble(AgentExecutionCommand request,
                              Printer printer,
                              String entryAgent,
                              boolean planSolve) {
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
        boolean resumedApprovedPlan = restoreResumePlanMode(agentContext, request);
        LtmRuntimeBootstrap.bootstrap(agentContext, request);

        // 运行账本记录本轮执行事实；working memory 只在本轮结束后作为下一轮 prompt 投影，
        // 因此这里初始化 run 但不把历史消息重新写入旧 message/transcript 主账本。
        // 先初始化 run，再装配工具，保证后续工具调用从一开始就能关联到同一个 ledger run。
        ExecutionLedgerRunSupport.initializeRun(
                agentExecutionRecorder,
                agentContext,
                request,
                entryAgent
        );
        bindTools(agentContext, request, planSolve);
        bindActiveRunIfPresent(agentContext);
        return new Assembled(agentContext, resumedApprovedPlan);
    }

    /**
     * 恢复续跑快照里的 plan mode 状态，并应用 ExitPlanMode 审批结论。
     *
     * @return 是否恢复到一个「已批准」的计划（PlanSolve 据此不再自动进入 plan mode）
     */
    private boolean restoreResumePlanMode(AgentContext agentContext, AgentExecutionCommand request) {
        if (agentContext == null || request == null || StringUtils.isBlank(request.getResumeContextJson())) {
            return false;
        }
        org.wwz.ai.domain.agent.runtime.askuser.UserQuestionResumeContext
                .fromJson(request.getResumeContextJson())
                .applyPlanModeTo(agentContext);
        return applyPlanApprovalDecision(agentContext, request);
    }

    private void bindTools(AgentContext agentContext, AgentExecutionCommand request, boolean planSolve) {
        ToolCollection fullToolCollection = planSolve
                ? agentToolCollectionFactory.buildForPlanSolve(agentContext, request)
                : agentToolCollectionFactory.buildForReact(agentContext, request);
        if (planSolve) {
            // 主代理只看白名单工具；完整工具池留给派生子 Agent 时筛选。
            agentContext.setSubAgentToolCollection(fullToolCollection);
            agentContext.setToolCollection(agentToolCollectionFactory.filterForPlanSolveMain(fullToolCollection));
            return;
        }
        agentContext.setToolCollection(fullToolCollection);
    }

    private boolean applyPlanApprovalDecision(AgentContext agentContext, AgentExecutionCommand request) {
        if (agentContext == null || request == null
                || StringUtils.isBlank(request.getResumeApprovalId())
                || planApprovalRepository == null) {
            return false;
        }
        var record = planApprovalRepository.findByApprovalId(request.getResumeApprovalId()).orElse(null);
        if (record == null || record.getDecision() == null) {
            return false;
        }
        var state = agentContext.requirePlanModeState();
        var decision = record.getDecision();
        if (decision.isApproved()) {
            String finalPlan = StringUtils.isNotBlank(decision.getEditedPlanContent())
                    ? decision.getEditedPlanContent()
                    : record.getPlanContent();
            String planFilePath = record.getPlanFilePath();
            if (planArtifactStore != null && StringUtils.isNotBlank(finalPlan)) {
                planFilePath = planArtifactStore.writePlan(agentContext.getSessionId(), finalPlan)
                        .orElse(planFilePath);
            }
            state.setPlan(finalPlan, planFilePath);
            state.exitPlanMode();
            return true;
        } else {
            state.clearPendingApproval();
            return false;
        }
    }

    private void bindActiveRunIfPresent(AgentContext agentContext) {
        if (activeAgentRunRegistry == null || agentContext == null) {
            return;
        }
        activeAgentRunRegistry.bindContext(agentContext.getRequestId(), agentContext);
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

    private void materializeSessionFiles(AgentContext agentContext, List<AgentExecutionFile> sessionFiles) {
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
}
