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
 * 任务分析节点
 */
@Slf4j
@Service
public class Step1AnalyzerNode extends AbstractExecuteSupport {

    @Override
    protected String doApply(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("\n === 执行第 {} 步 ===", dynamicContext.getStep());

        // 获取配置信息
        AiAgentClientFlowConfigVO aiAgentClientFlowConfigVO = dynamicContext.getAiAgentClientFlowConfigVOMap().get(AiClientTypeEnumVO.TASK_ANALYZER_CLIENT.getCode());

        // Plan 阶段：规划/修订计划（Plan-Act-Reflect 模式）
        log.info("\n阶段1: 规划/修订计划");
        String analysisPrompt = String.format(aiAgentClientFlowConfigVO.getStepPrompt(),
                requestParameter.getMessage(),
                dynamicContext.getStep(),
                dynamicContext.getMaxStep(),
                !dynamicContext.getExecutionHistory().isEmpty() ? dynamicContext.getExecutionHistory().toString() : "[首次执行]",
                dynamicContext.getCurrentTask()
        );


        ChatClient chatClient = getChatClientByClientId(aiAgentClientFlowConfigVO.getClientId());

        // 流式调用 LLM，前端实时看到输出
        int step = dynamicContext.getStep();
        String stepName = "任务分析";
        String analysisResult = streamLlmWithMetrics(
                chatClient,
                analysisPrompt,
                dynamicContext,
                requestParameter.getSessionId(),
                step,
                stepName,
                "analysis_status",
                a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, requestParameter.getSessionId())
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 1536));

        // 将分析结果保存到动态上下文中，供下一步使用
        dynamicContext.setValue("analysisResult", analysisResult);

        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ExecuteCommandEntity, DefaultAutoAgentExecuteStrategyFactory.DynamicContext, String> get(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        // 如果任务已完成或达到最大步数，进入总结阶段
        if (dynamicContext.isCompleted() || dynamicContext.getStep() > dynamicContext.getMaxStep()) {
            return getBean("step4LogExecutionSummaryNode");
        }
        
        // 否则继续执行下一步
        return getBean("step2PrecisionExecutorNode");
    }

}
