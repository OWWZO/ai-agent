package org.wwz.ai.domain.agent.service.execute.planexecute.step;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.ledger.IExecutionLedgerReadRepository;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunView;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.runtime.command.AgentExecutionCommand;
import org.wwz.ai.domain.agent.runtime.planmode.PlanArtifactStore;
import org.wwz.ai.domain.agent.runtime.planmode.PlanModeEntryPolicy;
import org.wwz.ai.domain.agent.runtime.planmode.PlanModeState;
import org.wwz.ai.domain.agent.service.execute.planexecute.step.factory.DefaultPlanSolveAgentExecuteStrategyFactory;
import org.wwz.ai.domain.agent.service.execute.support.PrepareAgentContextAssembly;

import java.util.List;

/**
 * PlanSolve 逻辑树 - Prepare：SOP 召回 + 准备 AgentContext / 工具 / Plan Mode。
 * <p>
 * 装配细节与 React 共用 {@link PrepareAgentContextAssembly}；本节点额外负责
 * PlanSolve 特有的首轮 plan mode 自动进入。
 */
@Slf4j
@Service("planSolvePrepareAgentContextNode")
public class PrepareAgentContextNode extends AbstractExecuteSupport {

    @Resource
    private PrepareAgentContextAssembly prepareAgentContextAssembly;

    @Resource
    private RunReactLoopNode runReactLoopNode;

    @Resource
    private PlanArtifactStore planArtifactStore;

    @Resource
    private IExecutionLedgerReadRepository executionLedgerReadRepository;

    @Override
    protected String doApply(AgentExecutionCommand request, DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("PlanSolve Prepare: SOP recall and context for requestId: {}", request.getRequestId());

        // 先构造上下文，再执行 plan mode 初始化，后续主代理只依赖 AgentContext。
        PrepareAgentContextAssembly.Assembled assembled = prepareAgentContextAssembly.assemble(
                request,
                dynamicContext.getPrinter(),
                ExecutionLedgerConstants.ENTRY_AGENT_PLAN_SOLVE,
                PrepareAgentContextAssembly.ToolScope.PLAN_SOLVE_MAIN_AND_SUB
        );
        org.wwz.ai.domain.agent.runtime.agent.AgentContext agentContext = assembled.context();

        boolean hasPriorPlanSolve = hasPriorPlanSolveUserTurn(request);
        if (PlanModeEntryPolicy.shouldAutoEnter(request, assembled.resumedApprovedPlan(), hasPriorPlanSolve)) {
            enterPlanModeForPlanSolve(agentContext, Boolean.TRUE.equals(request.getForcePlanMode()));
        }

        dynamicContext.setAgentContext(agentContext);
        return router(request, dynamicContext);
    }

    /**
     * 进入 plan 后才有硬只读。仅首轮 PlanSolve 或本轮 forcePlanMode 自动进入。
     */
    private void enterPlanModeForPlanSolve(org.wwz.ai.domain.agent.runtime.agent.AgentContext agentContext, boolean forced) {
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
            payload.put("reason", forced ? "FORCE_PLAN_MODE" : "PLAN_SOLVE_FIRST_TURN");
            agentContext.getPrinter().send("plan_mode_entered", payload);
        }
        log.info("{} PlanSolve auto-entered plan mode forced={} planFile={}",
                agentContext.getRequestId(), forced, planPathHint);
    }

    private boolean hasPriorPlanSolveUserTurn(AgentExecutionCommand request) {
        if (executionLedgerReadRepository == null || request == null
                || org.apache.commons.lang3.StringUtils.isBlank(request.getSessionId())) {
            return false;
        }
        try {
            int offset = 0;
            final int pageSize = 100;
            while (true) {
                List<DialogueRunView> page = executionLedgerReadRepository.queryRunsBySessionId(
                        request.getSessionId(), offset, pageSize);
                if (PlanModeEntryPolicy.hasPriorPlanSolveUserTurn(page, request.getRequestId())) {
                    return true;
                }
                if (page == null || page.size() < pageSize) {
                    return false;
                }
                offset += pageSize;
            }
        } catch (Exception e) {
            log.warn("query prior plan_solve runs failed, sessionId={}", request.getSessionId(), e);
            return false;
        }
    }

    @Override
    public StrategyHandler<AgentExecutionCommand, DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext, String> get(
            AgentExecutionCommand requestParameter,
            DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return runReactLoopNode;
    }
}
