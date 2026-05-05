package org.wwz.ai.domain.agent.service.execute.react.step.factory;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.ReActAgent;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.agent.SummaryAgent;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.service.execute.react.step.RootNode;

/**
 * React 执行策略工厂，与 flow/auto 同构：树形节点 + 专用 DynamicContext
 */
@Service
public class DefaultReactAgentExecuteStrategyFactory {

    private final RootNode reactRootNode;

    public DefaultReactAgentExecuteStrategyFactory(RootNode reactRootNode) {
        this.reactRootNode = reactRootNode;
    }

    public StrategyHandler<AgentRequest, DefaultReactAgentExecuteStrategyFactory.DynamicContext, String> armoryStrategyHandler() {
        return reactRootNode;
    }

    /**
     * React 链路专用动态上下文（与 Flow/Auto 的 DynamicContext 同构）
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DynamicContext {

        private Printer printer;
        /** 由 Step1 构建并放入，Step2 使用；AgentRequest 由 requestParameter 贯穿传递 */
        private AgentContext agentContext;
        /** 由 Step2 放入，Step3 用于生成总结 */
        private ReActAgent executor;
        /** 由 Step2 放入，Step3 用于生成总结 */
        private SummaryAgent summary;

        private int step;
    }
}
