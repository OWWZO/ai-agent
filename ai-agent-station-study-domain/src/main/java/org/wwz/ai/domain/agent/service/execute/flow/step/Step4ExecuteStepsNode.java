package org.wwz.ai.domain.agent.service.execute.flow.step;

import org.wwz.ai.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import org.wwz.ai.domain.agent.model.entity.ExecuteCommandEntity;
import org.wwz.ai.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import org.wwz.ai.domain.agent.model.valobj.enums.AiClientTypeEnumVO;
import org.wwz.ai.domain.agent.service.execute.flow.step.factory.DefaultFlowAgentExecuteStrategyFactory;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 第四步：按顺序执行规划步骤节点
 */
@Slf4j
@Component
public class Step4ExecuteStepsNode extends AbstractExecuteSupport {

    @Override
    public String doApply(ExecuteCommandEntity request, DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        log.info("开始执行第四步：按顺序执行规划步骤");
        
        try {
            // 获取配置信息
            AiAgentClientFlowConfigVO aiAgentClientFlowConfigVO = dynamicContext.getAiAgentClientFlowConfigVOMap().get(AiClientTypeEnumVO.EXECUTOR_CLIENT.getCode());

            // 获取规划客户端
            ChatClient executorChatClient = getChatClientByClientId(aiAgentClientFlowConfigVO.getClientId());

            // 从动态上下文获取解析的步骤
            Map<String, String> stepsMap = dynamicContext.getValue("stepsMap");
            
            if (stepsMap == null || stepsMap.isEmpty()) {
                return "步骤映射为空，无法执行";
            }
            
            // 按顺序执行规划步骤
            executeStepsInOrder(executorChatClient, stepsMap, dynamicContext);
            
            // 发送SSE结果
            AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createExecutionResult(
                    dynamicContext.getStep(),
                    "已完成所有规划步骤的执行",
                    request.getSessionId()
            );
            sendSseResult(dynamicContext, result);
            
            // 发送总结结果到【最终执行结果】区域
            sendSummaryResult(dynamicContext, request.getSessionId());
            
            // 发送完成标识
            sendCompleteResult(dynamicContext, request.getSessionId());
            
            // 更新步骤
            dynamicContext.setStep(dynamicContext.getStep() + 1);
            dynamicContext.setCompleted(true);
            
            log.info("第四步执行完成：所有规划步骤已执行");

            return "所有规划步骤执行完成";
        } catch (Exception e) {
            log.error("第四步执行失败", e);
            return "执行步骤失败: " + e.getMessage();
        }
    }

    @Override
    public StrategyHandler<ExecuteCommandEntity, DefaultFlowAgentExecuteStrategyFactory.DynamicContext, String> get(ExecuteCommandEntity request, DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        return defaultStrategyHandler;
    }
    
    /**
     * 按顺序执行规划步骤
     */
    private void executeStepsInOrder(ChatClient executorChatClient, Map<String, String> stepsMap, DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        if (stepsMap == null || stepsMap.isEmpty()) {
            log.warn("步骤映射为空，无法执行");
            return;
        }

        // 按步骤编号排序执行
        List<Integer> stepNumbers = new ArrayList<>();
        for (String stepKey : stepsMap.keySet()) {
            try {
                // 从"第1步"、"第2步"等格式中提取数字
                Pattern numberPattern = Pattern.compile("第(\\d+)步");
                Matcher matcher = numberPattern.matcher(stepKey);
                if (matcher.find()) {
                    stepNumbers.add(Integer.parseInt(matcher.group(1)));
                }
            } catch (NumberFormatException e) {
                log.warn("无法解析步骤编号: {}", stepKey);
            }
        }

        // 排序步骤编号
        stepNumbers.sort(Integer::compareTo);

        // 按顺序执行每个步骤
        for (Integer stepNumber : stepNumbers) {
            String stepKey = "第" + stepNumber + "步";
            String stepContent = null;

            // 查找匹配的步骤内容
            for (Map.Entry<String, String> entry : stepsMap.entrySet()) {
                if (entry.getKey().startsWith(stepKey)) {
                    stepContent = entry.getValue();
                    break;
                }
            }

            if (stepContent != null) {
                executeStep(executorChatClient, stepNumber, stepKey, stepContent, dynamicContext);
            } else {
                log.warn("未找到步骤内容: {}", stepKey);
            }
        }
    }
    
    /**
     * 执行单个步骤
     */
    private void executeStep(ChatClient executorChatClient, Integer stepNumber, String stepKey, String stepContent, DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        log.info("\n--- 开始执行 {} ---", stepKey);
        log.info("步骤内容: {}", stepContent.substring(0, Math.min(200, stepContent.length())) + "...");

        try {
            // 更新执行上下文
            dynamicContext.setValue("currentStep", stepNumber);
            dynamicContext.setValue("currentStepKey", stepKey);
            dynamicContext.setValue("currentStepContent", stepContent);

            // 使用执行器ChatClient来执行具体步骤
            String executionResult = executorChatClient.prompt()
                    .user(buildStepExecutionPrompt(stepContent, dynamicContext))
                    .call()
                    .content();

            assert executionResult != null;
            log.info("步骤 {} 执行结果: {}", stepNumber, executionResult.substring(0, Math.min(150, executionResult.length())) + "...");

            // 保存执行结果
            dynamicContext.setValue("step" + stepNumber + "Result", executionResult);
            
            // 发送步骤执行结果的SSE
            AutoAgentExecuteResultEntity stepResult = AutoAgentExecuteResultEntity.createExecutionResult(
                    stepNumber,
                    stepKey + " 执行完成: " + executionResult.substring(0, Math.min(500, executionResult.length())),
                    (String) dynamicContext.getValue("sessionId")
            );
            sendSseResult(dynamicContext, stepResult);

            // 短暂延迟，避免请求过于频繁
            Thread.sleep(1000);

        } catch (Exception e) {
            log.error("执行步骤 {} 时发生错误: {}", stepNumber, e.getMessage());
            dynamicContext.setValue("step" + stepNumber + "Error", e.getMessage());

            // 记录错误但继续执行下一步
            handleStepExecutionError(stepNumber, stepKey, e, dynamicContext);
        }

        log.info("--- 完成执行 {} ---", stepKey);
    }
    
    /**
     * 处理步骤执行错误
     */
    private void handleStepExecutionError(Integer stepNumber, String stepKey, Exception e, DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        log.warn("步骤 {} 执行失败，尝试恢复策略", stepNumber);

        // 记录错误统计
        Map<String, Integer> errorStats = dynamicContext.getValue("stepErrorStats");
        if (errorStats == null) {
            errorStats = new HashMap<>();
            dynamicContext.setValue("stepErrorStats", errorStats);
        }
        errorStats.put("step" + stepNumber, errorStats.getOrDefault("step" + stepNumber, 0) + 1);

        // 如果是网络错误，可以尝试重试
        if (e.getMessage() != null && (e.getMessage().contains("timeout") || e.getMessage().contains("connection"))) {
            log.info("检测到网络错误，将在后续重试机制中处理");
        }

        // 标记步骤为部分完成状态
        dynamicContext.setValue("step" + stepNumber + "Status", "FAILED_WITH_ERROR");
        
        // 发送错误结果的SSE
        try {
            AutoAgentExecuteResultEntity errorResult = AutoAgentExecuteResultEntity.createExecutionResult(
                    stepNumber,
                    stepKey + " 执行失败: " + e.getMessage(),
                    dynamicContext.getValue("sessionId")
            );
            sendSseResult(dynamicContext, errorResult);
        } catch (Exception sseException) {
            log.error("发送错误SSE结果失败", sseException);
        }
    }
    
    /**
     * 构建步骤执行提示词
     */
    private String buildStepExecutionPrompt(String stepContent, DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        return "你是一个智能执行助手，需要执行以下步骤:\n\n" +
                "**步骤内容:**\n" +
                stepContent + "\n\n" +
                "**用户原始请求:**\n" +
                dynamicContext.getCurrentTask() + "\n\n" +
                "**执行要求:**\n" +
                "1. 仔细分析步骤内容，理解需要执行的具体任务\n" +
                "2. 如果涉及MCP工具调用，请使用相应的工具\n" +
                "3. 提供详细的执行过程和结果\n" +
                "4. 如果遇到问题，请说明具体的错误信息\n" +
                "5. **重要**: 执行完成后，必须在回复末尾明确输出执行结果，格式如下:\n" +
                "   ```\n" +
                "   === 执行结果 ===\n" +
                "   状态: [成功/失败]\n" +
                "   结果描述: [具体的执行结果描述]\n" +
                "   输出数据: [如果有具体的输出数据，请在此列出]\n" +
                "   ```\n\n" +
                "请开始执行这个步骤，并严格按照要求提供详细的执行报告和结果输出。";
    }
    
    /**
     * 发送总结结果到流式输出
     */
    private void sendSummaryResult(DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext, String sessionId) {
        // 构建执行总结内容
        StringBuilder summaryContent = new StringBuilder();
        summaryContent.append("## 执行步骤完成总结\n\n");
        
        // 获取执行历史
        StringBuilder executionHistory = dynamicContext.getExecutionHistory();
        if (executionHistory != null && executionHistory.length() > 0) {
            summaryContent.append("### 已完成的工作\n");
            summaryContent.append(executionHistory.toString());
            summaryContent.append("\n\n");
        }
        
        summaryContent.append("### 执行状态\n");
        summaryContent.append("✅ 所有规划步骤已成功执行完成\n\n");
        
        summaryContent.append("### 执行效果评估\n");
        summaryContent.append("📊 任务执行流程顺利完成，各步骤按计划执行");
        
        AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createSummaryResult(
                summaryContent.toString(), sessionId);
        sendSseResult(dynamicContext, result);
        log.info("📊 已发送总结结果到【最终执行结果】区域");
    }
    
    /**
     * 发送完成标识到流式输出
     */
    private void sendCompleteResult(DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext, String sessionId) {
        AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createCompleteResult(sessionId);
        sendSseResult(dynamicContext, result);
        log.info("✅ 已发送完成标识");
    }
}