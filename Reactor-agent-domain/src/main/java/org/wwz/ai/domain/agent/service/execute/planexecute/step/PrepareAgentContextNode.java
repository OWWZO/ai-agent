package org.wwz.ai.domain.agent.service.execute.planexecute.step;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.SopRecallResponse;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.factory.AgentToolCollectionFactory;
import org.wwz.ai.domain.agent.runtime.planmode.PlanArtifactStore;
import org.wwz.ai.domain.agent.runtime.planmode.PlanModeState;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceService;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceSessionFileMaterializer;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceReadStateStore;
import org.wwz.ai.domain.agent.runtime.util.DateUtil;
import org.wwz.ai.domain.agent.reactor.model.dto.FileInformation;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.ledger.AgentExecutionRecorder;
import org.wwz.ai.domain.agent.memory.ltm.LtmRuntimeBootstrap;
import org.wwz.ai.domain.agent.ledger.ExecutionLedgerRunSupport;
import org.wwz.ai.domain.agent.rag.SopRecallService;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.service.execute.planexecute.step.factory.DefaultPlanSolveAgentExecuteStrategyFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * PlanSolve 逻辑树 - Prepare：SOP 召回 + 准备 AgentContext / 工具 / Plan Mode
 */
@Slf4j
@Service("planSolvePrepareAgentContextNode")
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
    private SopRecallService sopRecallService;

    @Resource
    private org.wwz.ai.domain.agent.runtime.cancel.ActiveAgentRunRegistry activeAgentRunRegistry;

    @Resource
    private RunReactLoopNode runReactLoopNode;

    @Resource
    private AgentExecutionRecorder agentExecutionRecorder;

    @Resource
    private ReactorRuntimeDependencies reactorRuntimeDependencies;

    @Resource
    private PlanArtifactStore planArtifactStore;

    @Resource
    private org.wwz.ai.domain.agent.runtime.capability.SessionCapabilityService sessionCapabilityService;

    @Override
    protected String doApply(AgentRequest request, DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("PlanSolve Prepare: SOP recall and context for requestId: {}", request.getRequestId());

        // 先构造上下文，再执行 SOP 召回和 plan mode 初始化，后续主代理只依赖 AgentContext。
        Printer printer = dynamicContext.getPrinter();
        var disabled = sessionCapabilityService.loadDisabled(request.getSessionId());
        AgentContext agentContext = AgentContext.builder()
                .requestId(request.getRequestId())
                .sessionId(request.getSessionId())
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
                .historyDialogue(request.getHistoryDialogue())
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

        // Execution Ledger 保存本轮事实；SOP、工作区读取状态和工作记忆分别服务当前执行或下一轮上下文。
        // 先初始化 run，再装配工具，保证后续工具调用从一开始就能关联到同一个 ledger run。
        ExecutionLedgerRunSupport.initializeRun(
                agentExecutionRecorder,
                agentContext,
                request,
                ExecutionLedgerConstants.ENTRY_AGENT_PLAN_SOLVE
        );
        ToolCollection fullToolCollection = buildToolCollection(agentContext, request);
        agentContext.setSubAgentToolCollection(fullToolCollection);
        agentContext.setToolCollection(agentToolCollectionFactory.filterForPlanSolveMain(fullToolCollection));
        if (activeAgentRunRegistry != null) {
            activeAgentRunRegistry.bindContext(agentContext.getRequestId(), agentContext);
        }
        handleSopRecall(agentContext, request);
        // 这条状态约束在工具层执行，节点只负责建立初始状态和把计划文件提示发给前端。
        boolean continuation = StringUtils.isNotBlank(request.getResumeQuestionId())
                || StringUtils.isNotBlank(request.getResumeApprovalId());
        if (!resumedApprovedPlan && !continuation) {
            enterPlanModeForPlanSolve(agentContext);
        }

        dynamicContext.setAgentContext(agentContext);
        return router(request, dynamicContext);
    }

    /**
     * 进入 plan 后才有硬只读；PlanSolve 链路默认每请求自动进入。
     */
    private void enterPlanModeForPlanSolve(AgentContext agentContext) {
        if (agentContext == null) {
            return;
        }
        PlanModeState state = agentContext.requirePlanModeState();
        if (state.isPlanMode()) {
            return;
        }
        // plan mode 的内存状态先于事件发送建立，避免消费者收到 entered 事件后立即读取到旧状态。
        state.enterPlanMode();
        String planPathHint = PlanArtifactStore.RELATIVE_PLAN_PATH;
        if (planArtifactStore != null) {
            try {
                var path = planArtifactStore.resolvePlanPath(agentContext.getSessionId());
                if (path != null) {
                    planPathHint = path.toString();
                }
            } catch (Exception ignored) {
                // 计划文件路径只是前端提示；存储解析失败时仍使用稳定的相对路径，不阻断执行。
            }
        }
        state.setPlan(state.getPlanContent(), planPathHint);
        if (agentContext.getPrinter() != null) {
            java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("mode", PlanModeState.MODE_PLAN);
            payload.put("planFilePath", planPathHint);
            payload.put("autoEntered", true);
            payload.put("reason", "PLAN_SOLVE_ENTRY");
            agentContext.getPrinter().send("plan_mode_entered", payload);
        }
        log.info("{} PlanSolve auto-entered plan mode, planFile={}",
                agentContext.getRequestId(), planPathHint);
    }

    private void handleSopRecall(AgentContext agentContext, AgentRequest request) {
        try {
            log.info("{} 开始执行SOP召回", request.getRequestId());
            // SOP 召回是提示词增强，不是执行前置条件。召回服务不可用或返回无效结果时，
            // 保留原始 sopPrompt，继续让 PlanSolve 使用基础规划能力。
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
            // SOP 属于 best-effort 外部能力；异常只记录诊断信息，不能让计划入口整体失败。
            log.error("{} SOP召回处理异常", request.getRequestId(), e);
        }
    }




    @jakarta.annotation.Resource
    private org.wwz.ai.domain.agent.runtime.planmode.IPlanApprovalRepository planApprovalRepository;

    private boolean restoreResumePlanMode(AgentContext agentContext, AgentRequest request) {
        if (agentContext == null || request == null || StringUtils.isBlank(request.getResumeContextJson())) {
            return false;
        }
        org.wwz.ai.domain.agent.runtime.askuser.UserQuestionResumeContext
                .fromJson(request.getResumeContextJson())
                .applyPlanModeTo(agentContext);
        return applyPlanApprovalDecision(agentContext, request);
    }

    private boolean applyPlanApprovalDecision(AgentContext agentContext, AgentRequest request) {
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

    private void hydrateWorkspaceReadState(AgentContext agentContext) {
        if (workspaceReadStateStore == null || agentContext == null) {
            return;
        }
        try {
            // 读取状态用于跨节点/跨轮的重复读取判断，失败时不影响 workspace 本身的读写能力。
            workspaceReadStateStore.hydrate(agentContext);
        } catch (Exception e) {
            log.warn("hydrate workspace read-state failed, requestId={}", agentContext.getRequestId(), e);
        }
    }

    private void materializeSessionFiles(AgentContext agentContext, java.util.List<FileInformation> sessionFiles) {
        if (workspaceSessionFileMaterializer == null) {
            return;
        }
        try {
            // 会话附件物化只负责把稳定文件引用准备到 workspace；它失败时保留引用，让后续
            // 工具按自身能力返回可解释的文件错误，而不是在上下文准备阶段吞掉整个请求。
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
            // 根目录由 workspace 服务统一解析并创建，节点不自行拼接路径，避免不同 Agent
            // 对 session 隔离规则产生分歧。
            return workspaceService.resolveAndEnsureRoot(sessionId).toString();
        } catch (Exception e) {
            log.warn("resolve workspace root failed, sessionId={}", sessionId, e);
            return null;
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
        // 历史消息只做运行时 DTO 翻译；它们属于输入上下文，不会在这里重新写入 Execution Ledger。
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
        return runReactLoopNode;
    }
}
