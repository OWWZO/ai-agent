package org.wwz.ai.domain.agent.service.armory.business.data.impl;

import org.wwz.ai.domain.agent.adapter.repository.IAgentRepository;
import org.wwz.ai.domain.agent.model.entity.ArmoryCommandEntity;
import org.wwz.ai.domain.agent.model.valobj.AiClientApiVO;
import org.wwz.ai.domain.agent.model.valobj.AiClientModelVO;
import org.wwz.ai.domain.agent.model.valobj.enums.AiAgentEnumVO;
import org.wwz.ai.domain.agent.runtime.executor.AgentExecutorSupport;
import org.wwz.ai.domain.agent.service.armory.business.data.ILoadDataStrategy;
import org.wwz.ai.domain.agent.service.armory.node.factory.DefaultArmoryStrategyFactory;
import org.wwz.ai.types.agent.config.AgentExecutorNames;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 按模型 ID 加载模型及其关联 API 配置的策略。
 * <p>
 * 模型节点创建 OpenAiChatModel 需要 API 对象，因此这里同时加载两类配置。
 */
@Slf4j
@Service("aiClientModelLoadDataStrategy")
public class AiClientModelLoadDataStrategy implements ILoadDataStrategy {

    @Resource
    private IAgentRepository repository;

    @Resource(name = AgentExecutorNames.TOOL_EXECUTOR)
    protected Executor threadPoolExecutor;

    @Override
    public void loadData(ArmoryCommandEntity armoryCommandEntity, DefaultArmoryStrategyFactory.DynamicContext dynamicContext) {
        List<String> modelIdList = armoryCommandEntity.getCommandIdList();

        //查询api参数
        CompletableFuture<List<AiClientApiVO>> aiClientApiListFuture = AgentExecutorSupport.supplyAsync(threadPoolExecutor, "armoryModelApiLoad", () -> {
            log.info("查询配置数据(ai_client_api) {}", modelIdList);
            return repository.queryAiClientApiVOListByModelIds(modelIdList);
        });

        //查询对应的ai客户端参数
        CompletableFuture<List<AiClientModelVO>> aiClientModelListFuture = AgentExecutorSupport.supplyAsync(threadPoolExecutor, "armoryModelLoad", () -> {
            log.info("查询配置数据(ai_client_model) {}", modelIdList);
            return repository.AiClientModelVOByModelIds(modelIdList);
        });

        CompletableFuture.allOf(aiClientApiListFuture, aiClientModelListFuture).thenRun(() -> {
            dynamicContext.setValue(AiAgentEnumVO.AI_CLIENT_API.getDataName(), aiClientApiListFuture.join());
            dynamicContext.setValue(AiAgentEnumVO.AI_CLIENT_MODEL.getDataName(), aiClientModelListFuture.join());
        }).join();

    }

}
