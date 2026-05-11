package org.wwz.ai.domain.agent.service.armory.node;

import org.wwz.ai.domain.agent.model.entity.ArmoryCommandEntity;
import org.wwz.ai.domain.agent.model.valobj.enums.AiAgentEnumVO;
import org.wwz.ai.domain.agent.model.valobj.AiClientSystemPromptVO;
import org.wwz.ai.domain.agent.model.valobj.AiClientVO;
import org.wwz.ai.domain.agent.runtime.tool.mcp.runtime.McpRegistry;
import org.wwz.ai.domain.agent.service.armory.node.factory.DefaultArmoryStrategyFactory;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ai agent 客户端对话对象节点
 * 2025/7/19 09:17
 */
@Slf4j
@Service
public class AiClientNode extends AbstractArmorySupport {

    @Resource
    private McpRegistry mcpRegistry;

    @Override
    protected String doApply(ArmoryCommandEntity requestParameter, DefaultArmoryStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("Ai Agent 构建节点，客户端{}", JSON.toJSONString(requestParameter));

        List<AiClientVO> aiClientList = dynamicContext.getValue(dataName());

        if (null == aiClientList || aiClientList.isEmpty()) {
            return router(requestParameter, dynamicContext);
        }

        Map<String, AiClientSystemPromptVO> systemPromptMap = dynamicContext.getValue(AiAgentEnumVO.AI_CLIENT_SYSTEM_PROMPT.getDataName());

        for (AiClientVO aiClientVO : aiClientList) {
            // 1. 预设话术
            StringBuilder defaultSystem = new StringBuilder("Ai 智能体 \r\n");
            List<String> promptIdList = aiClientVO.getPromptIdList();
            for (String promptId : promptIdList) {
                AiClientSystemPromptVO aiClientSystemPromptVO = systemPromptMap.get(promptId);
                defaultSystem.append(aiClientSystemPromptVO.getPromptContent());
            }

            // 2. 对话模型
            ChatModel chatModel = aiClientRuntimeRegistry.getRequiredModel(aiClientVO.getModelId());

            // 3. MCP 服务
            List<ToolCallback> toolCallbacks = mcpRegistry.getToolCallbacksByMcpIds(aiClientVO.getMcpIdList());

            // 4. advisor 顾问角色
            List<Advisor> advisors = new ArrayList<>();
            List<String> advisorIdList = aiClientVO.getAdvisorIdList();
            if (advisorIdList != null) {
                for (String advisorId : advisorIdList) {
                    advisors.add(aiClientRuntimeRegistry.getRequiredAdvisor(advisorId));
                }
            }


            Advisor[] advisorArray = advisors.toArray(new Advisor[]{});

            // 6. 构建对话客户端
            ChatClient.Builder chatClientBuilder = ChatClient.builder(chatModel)
                    .defaultSystem(defaultSystem.toString())
                    .defaultAdvisors(advisorArray);

            // fix 策略继续使用 Spring AI 原生 ToolCallback，但改成装配阶段一次性生成并缓存，避免请求期重复 listTools。
            if (!toolCallbacks.isEmpty()) {
                chatClientBuilder.defaultToolCallbacks(toolCallbacks);
            }

            ChatClient chatClient = chatClientBuilder.build();

            aiClientRuntimeRegistry.registerChatClient(aiClientVO.getClientId(), chatClient);
        }

        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ArmoryCommandEntity, DefaultArmoryStrategyFactory.DynamicContext, String> get(ArmoryCommandEntity requestParameter, DefaultArmoryStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return defaultStrategyHandler;
    }

    @Override
    protected String beanName(String id) {
        return AiAgentEnumVO.AI_CLIENT.getBeanName(id);
    }

    @Override
    protected String dataName() {
        return AiAgentEnumVO.AI_CLIENT.getDataName();
    }

}
