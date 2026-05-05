package org.wwz.ai.domain.agent.service.execute.planexecute.step.factory;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.ExecutorAgent;
import org.wwz.ai.domain.agent.runtime.agent.PlanningAgent;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.agent.SummaryAgent;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.service.execute.planexecute.step.RootNode;

/**
 * PlanSolve 执行策略工厂，与 react 同构
 */
@Service
public class DefaultPlanSolveAgentExecuteStrategyFactory {

    private final RootNode planSolveRootNode;

    public DefaultPlanSolveAgentExecuteStrategyFactory(RootNode planSolveRootNode) {
        this.planSolveRootNode = planSolveRootNode;
    }

    public StrategyHandler<AgentRequest, DynamicContext, String> armoryStrategyHandler() {
        return planSolveRootNode;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DynamicContext {
        private Printer printer;
        private AgentContext agentContext;
        private PlanningAgent planning;
        private ExecutorAgent executor;
        private SummaryAgent summary;
        private int step;
    }
}
