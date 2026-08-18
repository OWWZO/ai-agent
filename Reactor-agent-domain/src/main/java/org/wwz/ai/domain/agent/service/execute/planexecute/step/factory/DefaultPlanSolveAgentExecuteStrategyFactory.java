package org.wwz.ai.domain.agent.service.execute.planexecute.step.factory;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.ReActAgent;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.service.execute.planexecute.step.PrepareAgentContextNode;

/**
 * PlanSolve 执行策略工厂：Prepare → Run → Close（与 React 同构）。
 */
@Service
public class DefaultPlanSolveAgentExecuteStrategyFactory {

    private final PrepareAgentContextNode prepareNode;

    public DefaultPlanSolveAgentExecuteStrategyFactory(
            @Qualifier("planSolvePrepareAgentContextNode") PrepareAgentContextNode prepareNode) {
        this.prepareNode = prepareNode;
    }

    public StrategyHandler<AgentRequest, DynamicContext, String> armoryStrategyHandler() {
        return prepareNode;
    }

    /**
     * 节点间状态：与 React DynamicContext 字段同构，仅保留入口 printer 与 Prepare/Run 产出。
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DynamicContext {
        private Printer printer;
        private AgentContext agentContext;
        private ReActAgent executor;
        private String finalAnswer;
        /** AskUserQuestion 让步后跳过 CloseTurn */
        private Boolean waitingUserInput;
    }
}
