package org.wwz.ai.domain.agent.service.execute.react.step;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.runtime.command.AgentExecutionCommand;
import org.wwz.ai.domain.agent.service.execute.react.step.factory.DefaultReactAgentExecuteStrategyFactory;
import org.wwz.ai.domain.agent.service.execute.support.PrepareAgentContextAssembly;

/**
 * React 逻辑树 - Prepare：准备 AgentContext / 工具 / 账本。
 * <p>
 * 装配细节与 PlanSolve 共用 {@link PrepareAgentContextAssembly}，本节点只提供
 * React 入口标识与工具池构造方式。
 */
@Slf4j
@Service("reactPrepareAgentContextNode")
public class PrepareAgentContextNode extends AbstractExecuteSupport {

    @Resource
    private PrepareAgentContextAssembly prepareAgentContextAssembly;

    @Resource
    private RunReactLoopNode runReactLoopNode;

    @Override
    protected String doApply(AgentExecutionCommand request, DefaultReactAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("React Prepare: context and tools for requestId: {}", request.getRequestId());

        // 根节点只负责把请求翻译成运行时上下文，并把后续节点需要的能力装配齐；它是
        // 请求边界进入 runtime 的唯一准备点，后续节点不应重新读取原始请求来拼装上下文。
        dynamicContext.setAgentContext(prepareAgentContextAssembly.assemble(
                request,
                dynamicContext.getPrinter(),
                ExecutionLedgerConstants.ENTRY_AGENT_REACT,
                false
        ).context());
        return router(request, dynamicContext);
    }

    @Override
    public StrategyHandler<AgentExecutionCommand, DefaultReactAgentExecuteStrategyFactory.DynamicContext, String> get(
            AgentExecutionCommand requestParameter,
            DefaultReactAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return runReactLoopNode;
    }
}
