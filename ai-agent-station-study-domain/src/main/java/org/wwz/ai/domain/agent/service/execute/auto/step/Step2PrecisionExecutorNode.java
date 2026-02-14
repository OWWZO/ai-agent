package org.wwz.ai.domain.agent.service.execute.auto.step;

import org.wwz.ai.domain.agent.model.entity.ExecuteCommandEntity;
import org.wwz.ai.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import org.wwz.ai.domain.agent.model.valobj.enums.AiClientTypeEnumVO;
import org.wwz.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 精准执行节点
 */
@Slf4j
@Service
public class Step2PrecisionExecutorNode extends AbstractExecuteSupport{

    @Override
    protected String doApply(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("\n阶段2:执行当前步骤");
        
        // 从动态上下文中获取分析结果
        String analysisResult = dynamicContext.getValue("analysisResult");

        AiAgentClientFlowConfigVO aiAgentClientFlowConfigVO = dynamicContext.getAiAgentClientFlowConfigVOMap().get(AiClientTypeEnumVO.PRECISION_EXECUTOR_CLIENT.getCode());

        //当前步骤提示词+用户问题+上个节点分析的结果
        String executionPrompt = String.format(aiAgentClientFlowConfigVO.getStepPrompt(), requestParameter.getMessage(), analysisResult);

        // 获取对话客户端
        ChatClient chatClient = getChatClientByClientId(aiAgentClientFlowConfigVO.getClientId());

        // 流式调用 LLM，前端实时看到输出
        int step = dynamicContext.getStep();
        String stepName = "精准执行";

        String executionResult = streamLlmWithMetrics(
                chatClient,
                executionPrompt,
                dynamicContext,
                requestParameter.getSessionId(),
                step,
                stepName,
                "execution_target",
                a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, requestParameter.getSessionId())
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 1536));

        // 将执行结果保存到动态上下文中，供下一步使用
        dynamicContext.setValue("executionResult", executionResult);
        
        // 更新执行历史
        String stepSummary = String.format("""
                === 第 %d 步执行记录 ===
                【分析阶段】%s
                【执行阶段】%s
                """, dynamicContext.getStep(), analysisResult, executionResult);


        dynamicContext.getExecutionHistory().setLength(0);
        dynamicContext.getExecutionHistory().append(stepSummary);

        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ExecuteCommandEntity, DefaultAutoAgentExecuteStrategyFactory.DynamicContext, String> get(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return getBean("step3QualitySupervisorNode");
    }
    
}
