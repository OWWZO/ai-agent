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
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.service.execute.react.step.RootNode;

/**
 * React 执行策略工厂。
 * <p>
 * 工厂只暴露逻辑树入口，节点之间的状态通过专用 DynamicContext 传递，避免把执行状态散落到 Spring Bean 字段中。
 */
@Service
public class DefaultReactAgentExecuteStrategyFactory {

    private final RootNode reactRootNode;

    public DefaultReactAgentExecuteStrategyFactory(RootNode reactRootNode) {
        this.reactRootNode = reactRootNode;
    }

    public StrategyHandler<AgentRequest, DefaultReactAgentExecuteStrategyFactory.DynamicContext, String> armoryStrategyHandler() {
        // 调用方从根节点启动，后续节点由各自的 get 方法决定。
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
        /** 由 Step2 放入，Step3 发送 result / 持久化 working memory */
        private ReActAgent executor;
        /** 由 Step2 解析的 React 终答文本，Step3 作为 taskSummary 发出 */
        private String finalAnswer;

        private int step;
    }
}
