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

            // 获取执行客户端
            ChatClient executorChatClient = getChatClientByClientId(aiAgentClientFlowConfigVO.getClientId());

            // 从动态上下文获取解析的步骤
            Map<String, String> stepsMap = dynamicContext.getValue("stepsMap");
            
            if (stepsMap == null || stepsMap.isEmpty()) {
                return "步骤映射为空，无法执行";
            }
            
            // 按顺序执行规划步骤
            executeStepsInOrder(executorChatClient, stepsMap, dynamicContext, request);
            
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
    private void executeStepsInOrder(ChatClient executorChatClient, Map<String, String> stepsMap, DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext, ExecuteCommandEntity request) {
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
                executeStep(executorChatClient, stepNumber, stepKey, stepContent, dynamicContext, request.getSessionId());
            } else {
                log.warn("未找到步骤内容: {}", stepKey);
            }
        }
    }
    
    /**
     * 执行单个步骤
     */
    private void executeStep(ChatClient executorChatClient, Integer stepNumber, String stepKey, String stepContent, DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext, String sessionId) {
        log.info("\n--- 开始执行 {} ---", stepKey);
        log.info("步骤内容: {}", stepContent.substring(0, Math.min(200, stepContent.length())));

        try {
            // 更新执行上下文
            dynamicContext.setValue("currentStep", stepNumber);
            dynamicContext.setValue("currentStepKey", stepKey);
            dynamicContext.setValue("currentStepContent", stepContent);

            Map<String, String> stepsMap = dynamicContext.getValue("stepsMap");
            int totalSteps = stepsMap != null ? stepsMap.size() : 1;
            int step = dynamicContext.getStep();
            String stepName = (step >= 1 && step < AutoAgentExecuteResultEntity.STEP_NAMES.length)
                    ? AutoAgentExecuteResultEntity.STEP_NAMES[step] : "执行步骤";
            String executionResult = streamLlmWithMetrics(executorChatClient,
                    buildStepExecutionPrompt(stepContent, stepNumber, totalSteps, dynamicContext),
                    dynamicContext,
                    sessionId,
                    step,
                    stepName,
                    "execution_process");

            assert executionResult != null;
            log.info("步骤 {} 执行结果: {}", stepNumber, executionResult.substring(0, Math.min(150, executionResult.length())));

            // 保存执行结果（不向前端发送每个子步骤，用户只需知道大步骤进度）
            dynamicContext.setValue("step" + stepNumber + "Result", executionResult);

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

        // 标记步骤为部分完成状态（不向前端发送子步骤错误，仅记录日志，总结时会体现）
        dynamicContext.setValue("step" + stepNumber + "Status", "FAILED_WITH_ERROR");
    }
    
    /**
     * 构建步骤执行提示词
     * 中间步骤：【用户回答】内写「本步为中间步骤」；最后一步：【用户回答】内为面向用户的完整回复
     */
    private String buildStepExecutionPrompt(String stepContent, int stepNumber, int totalSteps,
                                           DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        boolean isLastStep = (stepNumber >= totalSteps);
        String prevResult = dynamicContext.getValue("step" + (stepNumber - 1) + "Result");

        StringBuilder sb = new StringBuilder();
        sb.append("执行以下步骤，完成用户请求。\n\n");
        sb.append("**步骤内容:**\n").append(stepContent).append("\n\n");
        sb.append("**用户请求:**\n").append(dynamicContext.getCurrentTask()).append("\n\n");
        if (prevResult != null && !prevResult.trim().isEmpty()) {
            sb.append("**前一步结果（可引用）:**\n").append(prevResult.substring(0, Math.min(1500, prevResult.length()))).append("\n\n");
        }
        sb.append("**输出格式（严格遵守）:**\n");
        if (isLastStep) {
            sb.append("本步为最后一步，【用户回答】内必须给出面向用户的完整回复，直接解决用户请求。简洁或详细取决于请求。\n");
        } else {
            sb.append("本步为中间步骤，【用户回答】内写「本步为中间步骤」。\n");
        }
        sb.append("【用户回答】\n");
        sb.append(isLastStep ? "[此处写用户可见的回复]\n" : "本步为中间步骤\n");
        sb.append("【/用户回答】\n\n");
        sb.append("不要输出执行报告、JSON、API结构等内部细节。请执行并输出。");
        return sb.toString();
    }
    
    /**
     * 从步骤输出中提取【用户回答】内的内容
     */
    private String extractUserAnswer(String stepResult) {
        if (stepResult == null) return null;
        int start = stepResult.indexOf("【用户回答】");
        if (start == -1) return null;
        start += "【用户回答】".length();
        int end = stepResult.indexOf("【/用户回答】", start);
        if (end == -1) return null;
        return stepResult.substring(start, end).trim();
    }

    /**
     * 发送总结结果到流式输出，包含 AI 对各步骤的执行结果及最终回答
     */
    private void sendSummaryResult(DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext, String sessionId) {
        StringBuilder summaryContent = new StringBuilder();

        // 1. 收集各步骤执行结果，最后一步作为 AI 最终回答
        Map<String, String> stepsMap = dynamicContext.getValue("stepsMap");
        int lastStepNum = 0;
        String lastStepResult = null;
        if (stepsMap != null && !stepsMap.isEmpty()) {
            List<Integer> nums = new ArrayList<>();
            for (String key : stepsMap.keySet()) {
                Matcher m = Pattern.compile("第(\\d+)步").matcher(key);
                if (m.find()) nums.add(Integer.parseInt(m.group(1)));
            }
            if (!nums.isEmpty()) {
                nums.sort(Integer::compareTo);
                lastStepNum = nums.get(nums.size() - 1);
                lastStepResult = dynamicContext.getValue("step" + lastStepNum + "Result");
            }
        }

        if (lastStepResult != null && !lastStepResult.trim().isEmpty()) {
            String userAnswer = extractUserAnswer(lastStepResult);
            summaryContent.append(userAnswer != null ? userAnswer.trim() : lastStepResult.trim());
            summaryContent.append("\n\n---\n\n");
        }


        AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createSummaryResult(
                summaryContent.toString(), sessionId);
        sendSseResult(dynamicContext, result);
        log.info("已发送总结结果到【最终执行结果】区域，含 AI 最终回答");
    }
    
    /**
     * 发送完成标识到流式输出（携带 token/成本 等指标）
     */
    private void sendCompleteResult(DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext, String sessionId) {
        var collector = dynamicContext.getLlmMetricsCollector();
        var metrics = collector != null ? collector.build() : null;
        AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createCompleteResult(sessionId, metrics);
        sendSseResult(dynamicContext, result);
        if (metrics != null) {
            log.info("✅ 已发送完成标识 | 总Token: {} | 预估成本: {} 元 | 耗时: {} ms",
                    metrics.getTotalTokens(), metrics.getEstimatedCost(), metrics.getTotalDurationMs());
        } else {
            log.info("✅ 已发送完成标识");
        }
    }
}