package org.wwz.ai.domain.agent.service.execute.auto.step;

import org.wwz.ai.domain.agent.model.entity.AgentExecuteResultEntity;
import org.wwz.ai.domain.agent.model.entity.ExecuteCommandEntity;
import org.wwz.ai.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import org.wwz.ai.domain.agent.model.valobj.enums.AiClientTypeEnumVO;
import org.wwz.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import lombok.Data;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 质量监督节点
 */
@Slf4j
@Service
public class Step3QualitySupervisorNode extends AbstractExecuteSupport {

//    @Resource
//    private Step1AnalyzerNode step1AnalyzerNode;

    @Resource
    private Step4LogExecutionSummaryNode step4LogExecutionSummaryNode;

    @Override
    protected String doApply(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("\n阶段3:反思与计划修订");

        // 从动态上下文中获取执行结果
        String executionResult = dynamicContext.getValue("executionResult");

        AiAgentClientFlowConfigVO aiAgentClientFlowConfigVO = dynamicContext.getAiAgentClientFlowConfigVOMap().get(AiClientTypeEnumVO.QUALITY_SUPERVISOR_CLIENT.getCode());

        // 根据用户的提示词 组装质量监督提示词
        String supervisionPrompt = String.format(aiAgentClientFlowConfigVO.getStepPrompt(),
                requestParameter.getMessage(), executionResult);

        // 获取对话客户端
        ChatClient chatClient = getChatClientByClientId(aiAgentClientFlowConfigVO.getClientId());

        // 流式调用
        int step = dynamicContext.getStep();
        String stepName = "质量监督";

        String supervisionResult = streamLlmWithMetrics(
                chatClient,
                supervisionPrompt,
                dynamicContext,
                requestParameter.getSessionId(),
                step,
                stepName,
                "assessment",
                a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, requestParameter.getSessionId())
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 1024));


        dynamicContext.setValue("supervisionResult", supervisionResult);

        // 如果不通过 就将质量监督的结果放进任务里
        if (supervisionResult.contains("是否通过: FAIL")) {
            log.info("质量检查未通过，需要重新优化生成");
            dynamicContext.getCurrentTask().append(supervisionResult);
        }else {
            log.info("质量检查通过");
            dynamicContext.setCompleted(true);
        }


        String stepSummary = String.format("""
                === 第 %d 步完整记录 ===
                【分析阶段】%s
                【执行阶段】%s
                【反思和监督阶段】%s
                """, dynamicContext.getStep(),
                dynamicContext.getValue("analysisResult"),
                executionResult,
                supervisionResult);

        dynamicContext.getExecutionHistory().setLength(0);
        dynamicContext.getExecutionHistory().append(stepSummary);


        dynamicContext.setStep(dynamicContext.getStep() + 1);

        // 如果任务已完成或达到最大步数，进入总结阶段
        if (dynamicContext.isCompleted() || dynamicContext.getStep() > dynamicContext.getMaxStep()) {
            return router(requestParameter, dynamicContext);
        }

        // 否则继续下一轮执行，返回到Step1AnalyzerNode
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ExecuteCommandEntity, DefaultAutoAgentExecuteStrategyFactory.DynamicContext, String> get(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return null;
    }

//    @Override
//    public StrategyHandler<ExecuteCommandEntity, DefaultAutoAgentExecuteStrategyFactory.DynamicContext, String> get(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
//        // 如果任务已完成或达到最大步数，进入总结阶段
//        if (dynamicContext.isCompleted() || dynamicContext.getStep() > dynamicContext.getMaxStep()) {
//            return step4LogExecutionSummaryNode;
//        }
//
//        // 否则返回到Step1AnalyzerNode进行下一轮分析
//        return step1AnalyzerNode;
//    }

}
