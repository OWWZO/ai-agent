package org.wwz.ai.domain.agent.service.armory.business.data.impl;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.adapter.repository.IAgentRepository;
import org.wwz.ai.domain.agent.model.entity.ArmoryCommandEntity;
import org.wwz.ai.domain.agent.model.valobj.*;
import org.wwz.ai.domain.agent.model.valobj.enums.AiAgentEnumVO;
import org.wwz.ai.domain.agent.runtime.executor.AgentExecutorSupport;
import org.wwz.ai.domain.agent.service.armory.business.data.ILoadDataStrategy;
import org.wwz.ai.domain.agent.service.armory.node.factory.DefaultArmoryStrategyFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 以客户端串联，加载数据策略
 */
@Slf4j
@Service("aiClientLoadDataStrategy")
public class AiClientLoadDataStrategy implements ILoadDataStrategy {

    @Resource
    private IAgentRepository repository;

    @Resource
    protected Executor threadPoolExecutor;

    @Override
    public void loadData(ArmoryCommandEntity armoryCommandEntity, DefaultArmoryStrategyFactory.DynamicContext dynamicContext) {
        //获取指定agent的id列表
        List<String> clientIdList = armoryCommandEntity.getCommandIdList();

        //根据id列表 运用CompletableFuture 进行多线程查询 然后收集结果

        //api查询 根据每个clientId查询出modelId 再根据modelId来查询api的id 然后将api的信息封装成apiVO的形式返回 所有client的apiId都装在一起
        CompletableFuture<List<AiClientApiVO>> aiClientApiListFuture = AgentExecutorSupport.supplyAsync(threadPoolExecutor, "armoryAiClientApi", () -> {
            log.info("查询配置数据(ai_client_api) {}", clientIdList);
            return repository.queryAiClientApiVOListByClientIds(clientIdList);
        });

        //模型的参数查询
        CompletableFuture<List<AiClientModelVO>> aiClientModelListFuture = AgentExecutorSupport.supplyAsync(threadPoolExecutor, "armoryAiClientModel", () -> {
            log.info("查询配置数据(ai_client_model) {}", clientIdList);
            return repository.AiClientModelVOByClientIds(clientIdList);
        });

        //mcp工具信息的查询
        CompletableFuture<List<AiClientToolMcpVO>> aiClientToolMcpListFuture = AgentExecutorSupport.supplyAsync(threadPoolExecutor, "armoryAiClientToolMcp", () -> {
            log.info("查询配置数据(ai_client_tool_mcp) {}", clientIdList);
            return repository.AiClientToolMcpVOByClientIds(clientIdList);
        });

        //提示词信息的查询
        CompletableFuture<Map<String, AiClientSystemPromptVO>> aiClientSystemPromptListFuture = AgentExecutorSupport.supplyAsync(threadPoolExecutor, "armoryAiClientPrompt", () -> {
            log.info("查询配置数据(ai_client_system_prompt) {}", clientIdList);
            return repository.queryAiClientSystemPromptMapByClientIds(clientIdList);
        });

        //查询顾问参数数据
        CompletableFuture<List<AiClientAdvisorVO>> aiClientAdvisorListFuture = AgentExecutorSupport.supplyAsync(threadPoolExecutor, "armoryAiClientAdvisor", () -> {
            log.info("查询配置数据(ai_client_advisor) {}", clientIdList);
            return repository.AiClientAdvisorVOByClientIds(clientIdList);
        });

        //
        CompletableFuture<List<AiClientVO>> aiClientListFuture = AgentExecutorSupport.supplyAsync(threadPoolExecutor, "armoryAiClient", () -> {
            log.info("查询配置数据(ai_client) {}", clientIdList);
            return repository.AiClientVOByClientIds(clientIdList);
        });

        CompletableFuture.allOf(aiClientApiListFuture, aiClientModelListFuture, aiClientSystemPromptListFuture,
                aiClientToolMcpListFuture, aiClientAdvisorListFuture, aiClientListFuture).thenRun(() -> {
            dynamicContext.setValue(AiAgentEnumVO.AI_CLIENT_API.getDataName(), aiClientApiListFuture.join());
            dynamicContext.setValue(AiAgentEnumVO.AI_CLIENT_MODEL.getDataName(), aiClientModelListFuture.join());
            dynamicContext.setValue(AiAgentEnumVO.AI_CLIENT_SYSTEM_PROMPT.getDataName(), aiClientSystemPromptListFuture.join());
            dynamicContext.setValue(AiAgentEnumVO.AI_CLIENT_TOOL_MCP.getDataName(), aiClientToolMcpListFuture.join());
            dynamicContext.setValue(AiAgentEnumVO.AI_CLIENT_ADVISOR.getDataName(), aiClientAdvisorListFuture.join());
            dynamicContext.setValue(AiAgentEnumVO.AI_CLIENT.getDataName(), aiClientListFuture.join());

        }).join();

    }

}
