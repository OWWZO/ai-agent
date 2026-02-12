package org.wwz.ai.domain.agent.service.execute.auto.step;

import org.wwz.ai.domain.agent.model.entity.AgentExecuteResultEntity;
import org.wwz.ai.domain.agent.model.entity.ExecuteCommandEntity;
import org.wwz.ai.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import org.wwz.ai.domain.agent.model.valobj.enums.AiClientTypeEnumVO;
import org.wwz.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 执行总结节点
 */
@Slf4j
@Service
public class Step4LogExecutionSummaryNode extends AbstractExecuteSupport {

    @Override
    protected String doApply(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("\n=== 执行第 {} 步 ===", dynamicContext.getStep());

        // 第四阶段：执行总结
        log.info("\n阶段4: 执行总结分析");

        // 生成最终总结报告（无论任务是否完成都需要生成）
        generateFinalReport(requestParameter, dynamicContext);
        
        log.info("\n=== 动态多轮执行结束 ====");
        
        return "ai agent execution summary completed!";
    }

    @Override
    public StrategyHandler<ExecuteCommandEntity, DefaultAutoAgentExecuteStrategyFactory.DynamicContext, String> get(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        // 总结节点是最后一个节点，返回null表示执行结束
        return defaultStrategyHandler;
    }
    
    /**
     * 记录执行总结
     */
    private void logExecutionSummary(int maxSteps, StringBuilder executionHistory, boolean isCompleted) {
        log.info("\n📊 === 动态多轮执行总结 ====");
        
        int actualSteps = Math.min(maxSteps, executionHistory.toString().split("=== 第").length - 1);
        log.info("📈 总执行步数: {} 步", actualSteps);
        
        if (isCompleted) {
            log.info("✅ 任务完成状态: 已完成");
        } else {
            log.info("⏸️ 任务完成状态: 未完成（达到最大步数限制）");
        }
        
        // 计算执行效率
        double efficiency = isCompleted ? 100.0 : (double) actualSteps / maxSteps * 100;
        log.info("📊 执行效率: {}%", efficiency);
    }
    
    /**
     * 生成最终总结报告
     */
    private void generateFinalReport(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        try {
            boolean isCompleted = dynamicContext.isCompleted();

            log.info("\n--- 生成{}任务的最终答案 ---", isCompleted ? "已完成" : "未完成");

            AiAgentClientFlowConfigVO aiAgentClientFlowConfigVO = dynamicContext.getAiAgentClientFlowConfigVOMap().get(AiClientTypeEnumVO.RESPONSE_ASSISTANT.getCode());

            String summaryPrompt = getSummaryPrompt(aiAgentClientFlowConfigVO, requestParameter, dynamicContext, isCompleted);

            // 获取对话客户端 - 使用任务分析客户端进行总结
            ChatClient chatClient = getChatClientByClientId(aiAgentClientFlowConfigVO.getClientId());
            
            // 流式调用 LLM，前端实时看到输出
            int step = dynamicContext.getStep();
            String stepName = "执行总结";
            String summaryResult = streamLlmWithMetrics(
                    chatClient,
                    summaryPrompt,
                    dynamicContext,
                    requestParameter.getSessionId(),
                    step,
                    stepName,
                    "summary_overview",
                    a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, requestParameter.getSessionId() + "-summary")
                            .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 50));

            //面向用户结果
            logFinalReport(dynamicContext, summaryResult, requestParameter.getSessionId());

            // 将总结结果保存到动态上下文中
            dynamicContext.setValue("finalSummary", summaryResult);
            
        } catch (Exception e) {
            log.error("生成最终总结报告时出现异常: {}", e.getMessage(), e);
        }
    }

    private static String getSummaryPrompt(AiAgentClientFlowConfigVO aiAgentClientFlowConfigVO, ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext, boolean isCompleted) {
        String summaryPrompt;
        if (isCompleted) {
            summaryPrompt = String.format(aiAgentClientFlowConfigVO.getStepPrompt(),
                    requestParameter.getMessage(),
                    dynamicContext.getExecutionHistory().toString());
        } else {
            summaryPrompt = String.format("""
                    虽然任务未完全执行完成，但请基于已有的执行过程，尽力回答用户的原始问题：
                    
                    **用户原始问题:** %s
                    
                    **已执行的过程和获得的信息:**
                    %s
                    
                    **要求:**
                    1. 基于已有信息，尽力回答用户的原始问题
                    2. 如果信息不足，说明哪些部分无法完成并给出原因
                    3. 提供已能确定的部分答案
                    4. 给出完成剩余部分的具体建议
                    5. 以MD语法的表格形式，优化展示结果数据
                    
                    请基于现有信息给出用户问题的答案：
                    """,
                    requestParameter.getMessage(),
                    dynamicContext.getExecutionHistory().toString());
        }
        return summaryPrompt;
    }

    /**
     * 输出最终总结报告（仅发送一次完整总结，避免分段重复）
     */
    private void logFinalReport(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext, String summaryResult, String sessionId) {
        boolean isCompleted = dynamicContext.isCompleted();
        log.info("\n  === {}任务最终总结报告 ===", isCompleted ? "已完成" : "未完成");
        
        // 仅发送一次完整总结，面向用户
        sendSummaryResult(dynamicContext, summaryResult, sessionId);

    }
    
    /**
     * 发送总结结果到流式输出
     */
    private void sendSummaryResult(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext, 
                                 String summaryResult, String sessionId) {
        AgentExecuteResultEntity result = AgentExecuteResultEntity.createSummaryResult(
                 summaryResult, sessionId);
        sendSseResult(dynamicContext, result);
    }
    
    /**
     * 发送完成标识到流式输出（携带 token/成本 等指标）
     */
    private void sendCompleteResult(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext, String sessionId) {
        var collector = dynamicContext.getLlmMetricsCollector();
        var metrics = collector != null ? collector.build() : null;
        AgentExecuteResultEntity result = AgentExecuteResultEntity.createCompleteResult(sessionId, metrics);
        sendSseResult(dynamicContext, result);
        if (metrics != null) {
            log.info("✅ 已发送完成标识 | 总Token: {} | 预估成本: {} 元 | 耗时: {} ms",
                    metrics.getTotalTokens(), metrics.getEstimatedCost(), metrics.getTotalDurationMs());
        } else {
            log.info("✅ 已发送完成标识");
        }
    }

}
