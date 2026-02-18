package org.wwz.ai.domain.agent.service.execute.flow.step;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatResponse;
import org.wwz.ai.domain.agent.model.entity.AgentExecuteResultEntity;
import org.wwz.ai.domain.agent.model.entity.ExecuteCommandEntity;
import org.wwz.ai.domain.agent.model.entity.ExecutionPlanStep;
import org.wwz.ai.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import org.wwz.ai.domain.agent.model.valobj.enums.AiClientTypeEnumVO;
import org.wwz.ai.domain.agent.service.execute.flow.step.factory.DefaultFlowAgentExecuteStrategyFactory;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.*;

import org.wwz.ai.domain.agent.model.entity.JoyAgentEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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

            // 从上下文获取结构化执行计划（避免 Map+正则排序的低优雅实现）
            List<ExecutionPlanStep> executionPlan = dynamicContext.getExecutionPlan();
            if (executionPlan == null || executionPlan.isEmpty()) {
                return "执行计划为空，无法执行";
            }

            // 按顺序执行规划步骤（按 stepNumber 排序，避免正则解析）
            executeStepsInOrder(executorChatClient, executionPlan, dynamicContext, request);

            
            // 发送ai回答结果到【最终执行结果】区域
            sendSummaryResult(dynamicContext, request.getSessionId());

            
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
    private void executeStepsInOrder(ChatClient executorChatClient, List<ExecutionPlanStep> executionPlan, DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext, ExecuteCommandEntity request) {
        if (executionPlan == null || executionPlan.isEmpty()) {
            log.warn("执行计划为空，无法执行");
            return;
        }

        // 通过 stepNumber 排序，保证稳定执行顺序
        List<ExecutionPlanStep> sortedPlan = new ArrayList<>(executionPlan);
        sortedPlan.sort(Comparator.comparingInt(ExecutionPlanStep::stepNumber));

        // 按顺序执行每个步骤（每个步骤的信息来自结构化 plan，而不是字符串解析）
        for (ExecutionPlanStep planStep : sortedPlan) {
            int stepNumber = planStep.stepNumber();
            String stepKey = "第" + stepNumber + "步";
            executeStep(executorChatClient, planStep, stepKey, dynamicContext, request.getSessionId(), sortedPlan.size());
        }
    }
    
    /**
     * 执行单个步骤
     *
     * <p>
     * 参考 {@code jd-agent} 中的工具调用模式：
     * 1. 检测步骤是否需要调用工具（如 auto_analysis、nl2sql、table_rag）
     * 2. 如果需要，先调用工具获取结果
     * 3. 将工具结果作为上下文传递给 LLM 进行后续处理
     * </p>
     */
    private void executeStep(ChatClient executorChatClient, ExecutionPlanStep planStep, String stepKey,
                             DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                             String sessionId, int totalSteps) {
        // 将结构化步骤信息渲染为可读文本，用于日志与提示词（不再依赖规划输出的 Markdown 格式）
        String stepContent = buildStepContent(planStep, stepKey);
        Integer stepNumber = planStep.stepNumber();
        log.info("\n--- 开始执行 {} ---", stepKey);
        log.info("步骤内容: {}", stepContent.substring(0, Math.min(200, stepContent.length())));

        try {
            // 更新执行上下文
            dynamicContext.setValue("currentStep", stepNumber);
            dynamicContext.setValue("currentStepKey", stepKey);
            dynamicContext.setValue("currentStepContent", stepContent);

            int step = dynamicContext.getStep();
            String stepName = (step >= 1 && step < AgentExecuteResultEntity.STEP_NAMES.length)
                    ? AgentExecuteResultEntity.STEP_NAMES[step] : "执行步骤";

            // 构建执行提示词：引导模型通过 MCP 工具完成当前步骤
            String executionPrompt = buildStepExecutionPrompt(stepContent, stepNumber, totalSteps, dynamicContext);

            // 使用 streamTaskExecution 替代 callLlmWithMetrics，以支持 JoyAgentEvent
            String taskId = sessionId + "-" + stepNumber;
            String executionResult = streamTaskExecution(
                    executorChatClient,
                    executionPrompt,
                    dynamicContext,
                    sessionId,
                    taskId,
                    stepNumber
            );

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

    private String streamTaskExecution(ChatClient chatClient, String userMessage,
                                       DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                       String sessionId, String taskId, int stepNumber) {
        StringBuilder fullText = new StringBuilder();
        try {
             // 模拟流式输出 JoyAgentEvent
            // 先发送 task 开始事件
            Map<String, Object> startInnerMap = new HashMap<>();
            startInnerMap.put("data", "");
            startInnerMap.put("isFinal", false);
            startInnerMap.put("messageType", "markdown");

            Map<String, Object> startMap = new HashMap<>();
            startMap.put("messageType", "markdown");
            startMap.put("resultMap", startInnerMap);
            
            JoyAgentEvent startEvent = JoyAgentEvent.builder()
                    .taskId(taskId)
                    .messageType("task")
                    .resultMap(startMap)
                    .messageId(UUID.randomUUID().toString())
                    .build();
            sendSseResult(dynamicContext, startEvent);

            ChatResponse response = chatClient.prompt()
                    .user(userMessage)
                    .advisors(withMetrics(dynamicContext, null))
                    .call()
                    .chatResponse();
            
            String content = "";
            if (response != null && response.getResult() != null && response.getResult().getOutput() != null) {
                content = response.getResult().getOutput().getText();
            }
            
            if (content == null) content = "";
            fullText.append(content);

            // 将内容分块发送
            int chunkSize = 20;
            for (int i = 0; i < content.length(); i += chunkSize) {
                String chunk = content.substring(i, Math.min(content.length(), i + chunkSize));
                
                Map<String, Object> chunkInnerMap = new HashMap<>();
                chunkInnerMap.put("data", chunk);
                chunkInnerMap.put("isFinal", false);
                chunkInnerMap.put("messageType", "markdown");
                
                Map<String, Object> chunkMap = new HashMap<>();
                chunkMap.put("messageType", "markdown");
                chunkMap.put("resultMap", chunkInnerMap);
                
                JoyAgentEvent chunkEvent = JoyAgentEvent.builder()
                        .taskId(taskId)
                        .messageType("task")
                        .resultMap(chunkMap)
                        .messageId(UUID.randomUUID().toString())
                        .build();
                sendSseResult(dynamicContext, chunkEvent);
                
                try { Thread.sleep(50); } catch (InterruptedException ignored) {} // 模拟流式延迟
            }
            
            // 发送完成事件
            Map<String, Object> endInnerMap = new HashMap<>();
            endInnerMap.put("data", "");
            endInnerMap.put("isFinal", true);
            endInnerMap.put("messageType", "markdown");

            Map<String, Object> endMap = new HashMap<>();
            endMap.put("messageType", "markdown");
            endMap.put("resultMap", endInnerMap);
            
            JoyAgentEvent endEvent = JoyAgentEvent.builder()
                    .taskId(taskId)
                    .messageType("task")
                    .resultMap(endMap)
                    .messageId(UUID.randomUUID().toString())
                    .build();
            sendSseResult(dynamicContext, endEvent);

        } catch (Exception e) {
            log.error("Task execution failed", e);
        }
        return fullText.toString();
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
     *
     * <p>
     * 如果步骤中调用了工具，将工具结果包含在提示词中，供 LLM 参考。
     * </p>
     */
    private String buildStepExecutionPrompt(String stepContent, int stepNumber, int totalSteps,
                                           DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        boolean isLastStep = (stepNumber >= totalSteps);
        String prevResult = dynamicContext.getValue("step" + (stepNumber - 1) + "Result");

        StringBuilder sb = new StringBuilder();
        sb.append("执行以下步骤，完成用户请求。\n\n");

        sb.append("**步骤内容:**\n").append(stepContent).append("\n\n");

        sb.append("**用户请求:**\n").append(dynamicContext.getCurrentTask()).append("\n\n");

        // 强制约束：不需要工具就直接完成；需要外部信息/动作必须调用 MCP 工具，禁止臆造工具与结果
        sb.append("如果不需要任何工具，请直接完成该步骤。\n");
        sb.append("如果需要外部信息/发布/通知等动作，必须直接调用已接入的 MCP 工具获取真实结果，再基于结果生成回答。\n");
        sb.append("禁止虚构工具名称、工具参数或工具返回内容；未调用工具就不要声称“已调用”。\n\n");

        if (prevResult != null && !prevResult.trim().isEmpty()) {
            sb.append("**前一步结果（可引用）:**\n").append(prevResult.substring(0, Math.min(1500, prevResult.length()))).append("\n\n");
        }

        sb.append("**输出格式（严格遵守）:**\n");

        if (isLastStep) {
            sb.append("本步为最后一步，【用户回答】内必须给出面向用户的完整回复，直接解决用户请求。简洁或详细取决于请求。\n");
            sb.append("【用户回答】\n");
            sb.append( "[此处写用户可见的回复]\n");
            sb.append("【/用户回答】\n\n");
        }

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
        List<ExecutionPlanStep> executionPlan = dynamicContext.getExecutionPlan();
        int lastStepNum = 0;
        String lastStepResult = null;
        if (executionPlan != null && !executionPlan.isEmpty()) {
            lastStepNum = executionPlan.stream().mapToInt(ExecutionPlanStep::stepNumber).max().orElse(0);
            lastStepResult = dynamicContext.getValue("step" + lastStepNum + "Result");
        }

        if (lastStepResult != null && !lastStepResult.trim().isEmpty()) {
            String userAnswer = extractUserAnswer(lastStepResult);
            summaryContent.append(userAnswer != null ? userAnswer.trim() : lastStepResult.trim());
            summaryContent.append("\n\n---\n\n");
        }


        AgentExecuteResultEntity result = AgentExecuteResultEntity.createSummaryResult(
                summaryContent.toString(), sessionId);
        sendSseResult(dynamicContext, result);

        log.info("已发送总结结果到【最终执行结果】区域，含 AI 最终回答");
    }
    


    private String buildStepContent(ExecutionPlanStep planStep, String stepKey) {
        // 将结构化计划渲染成稳定的文本格式，方便日志与执行提示词复用
        StringBuilder sb = new StringBuilder();
        sb.append("### ").append(stepKey).append("：").append(planStep.stepName() != null ? planStep.stepName() : "未命名步骤").append("\n");
        // 标记 actionType，便于排查“为何某步骤没有触发 LLM 调用”
        sb.append("动作类型：").append(planStep.actionType() != null ? planStep.actionType() : "LLM").append("\n");
        sb.append("任务描述：").append(planStep.description() != null ? planStep.description() : "无").append("\n");
        sb.append("工具名称：").append(planStep.toolName() != null ? planStep.toolName() : "无").append("\n");
        if (planStep.toolParams() != null && !planStep.toolParams().isEmpty()) {
            sb.append("工具参数：").append(planStep.toolParams()).append("\n");
        }
        sb.append("预期输出：").append(planStep.expectedOutput() != null ? planStep.expectedOutput() : "无").append("\n");
        return sb.toString();
    }
}
