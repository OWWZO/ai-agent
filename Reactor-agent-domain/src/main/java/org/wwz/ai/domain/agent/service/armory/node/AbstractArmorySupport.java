package org.wwz.ai.domain.agent.service.armory.node;

import org.wwz.ai.domain.agent.adapter.repository.IAgentRepository;
import org.wwz.ai.domain.agent.service.runtime.AiClientRuntimeRegistry;
import org.wwz.ai.domain.agent.model.entity.ArmoryCommandEntity;
import org.wwz.ai.domain.agent.service.armory.node.factory.DefaultArmoryStrategyFactory;
import cn.bugstack.wrench.design.framework.tree.AbstractMultiThreadStrategyRouter;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wwz.ai.types.agent.config.AgentExecutorNames;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;

/**
 * Agent 运行时装配节点基类。
 * <p>
 * 装配链把持久化配置转换为模型、API、Advisor、MCP 和 ChatClient 等运行时对象，并写入运行时注册表；
 * 节点本身不承载请求级业务执行。
 */
public abstract class AbstractArmorySupport extends AbstractMultiThreadStrategyRouter<ArmoryCommandEntity, DefaultArmoryStrategyFactory.DynamicContext, String> {

    private final Logger log = LoggerFactory.getLogger(AbstractArmorySupport.class);

    @Resource
    protected AiClientRuntimeRegistry aiClientRuntimeRegistry;

    @Resource(name = AgentExecutorNames.TOOL_EXECUTOR)
    protected Executor threadPoolExecutor;

    @Resource
    protected IAgentRepository repository;

    @Override
    protected void multiThread(ArmoryCommandEntity requestParameter, DefaultArmoryStrategyFactory.DynamicContext dynamicContext) throws ExecutionException, InterruptedException, TimeoutException {
        // 默认节点没有额外并行任务，具体数据加载策略由根节点按命令类型负责。
    }

    protected String beanName(String id) {
        return null;
    }

    protected String dataName() {
        return null;
    }

}
