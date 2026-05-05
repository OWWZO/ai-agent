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
 * 装配支撑类
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
        // 缺省的
    }

    protected String beanName(String id) {
        return null;
    }

    protected String dataName() {
        return null;
    }

}
