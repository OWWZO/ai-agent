package org.wwz.ai.domain.agent.service.execute.react.step;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.ReActAgent;
import org.wwz.ai.domain.agent.runtime.agent.ReactImplAgent;
import org.wwz.ai.domain.agent.runtime.agent.SummaryAgent;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.service.execute.react.step.factory.DefaultReactAgentExecuteStrategyFactory;

/**
 * React 逻辑树 - 步骤2：执行 ReAct 推理与工具调用
 * 初始化 ReActAgent、SummaryAgent，执行 executor.run，将结果供 Step3 总结
 */
@Slf4j
@Service
public class RunReactNode extends AbstractExecuteSupport {

    @Resource
    private SummaryResultNode step3SummaryResultNode;

    @Override
    protected String doApply(AgentRequest requestParameter, DefaultReactAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("React Step2: Run ReAct loop for requestId: {}", requestParameter.getRequestId());

        AgentContext agentContext = dynamicContext.getAgentContext();
        if (agentContext == null) {
            throw new IllegalStateException("React Step2: agentContext is null, Step1 must run first.");
        }

        ReActAgent executor = new ReactImplAgent(agentContext);
        SummaryAgent summary = new SummaryAgent(agentContext);

        summary.setSystemPrompt(summary.getSystemPrompt().replace("{{query}}", requestParameter.getQuery()));

        executor.run(requestParameter.getQuery());

        dynamicContext.setExecutor(executor);
        dynamicContext.setSummary(summary);
        dynamicContext.setStep(2);

        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<AgentRequest, DefaultReactAgentExecuteStrategyFactory.DynamicContext, String> get(
            AgentRequest requestParameter,
            DefaultReactAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return step3SummaryResultNode;
    }
}
