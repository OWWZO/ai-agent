package org.wwz.ai.domain.agent.service.execute.flow.step;

import org.wwz.ai.domain.agent.model.entity.AgentExecuteResultEntity;
import org.wwz.ai.domain.agent.model.entity.ExecuteCommandEntity;
import org.wwz.ai.domain.agent.service.execute.flow.step.factory.DefaultFlowAgentExecuteStrategyFactory;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 步骤3：规划步骤解析节点
 */
@Slf4j
@Service
public class Step3ParseStepsNode extends AbstractExecuteSupport {

    @Resource
    private Step4ExecuteStepsNode step4ExecuteStepsNode;

    @Override
    protected String doApply(ExecuteCommandEntity requestParameter, DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("\n--- 步骤3: 规划步骤解析 ---");
        
        String planningResult = dynamicContext.getValue("planningResult");
        
        if (planningResult == null || planningResult.trim().isEmpty()) {
            log.warn("规划结果为空，无法解析步骤");
            throw new RuntimeException("规划结果为空，无法解析步骤");
        }
        
        Map<String, String> stepsMap = parseExecutionSteps(planningResult);
        
        log.info("成功解析 {} 个执行步骤", stepsMap.size());
        
        // 保存解析结果到上下文
        dynamicContext.setValue("stepsMap", stepsMap);
        
        // 构建解析结果摘要
        StringBuilder parseResult = new StringBuilder();
        parseResult.append("## 步骤解析结果\n\n");
        parseResult.append(String.format("成功解析 %d 个执行步骤：\n\n", stepsMap.size()));
        
        for (Map.Entry<String, String> entry : stepsMap.entrySet()) {
            parseResult.append(String.format("- **%s**: %s\n", 
                entry.getKey(), 
                entry.getValue().split("\n")[0])); // 只显示标题部分
        }
        
        // 发送SSE结果
        AgentExecuteResultEntity result = AgentExecuteResultEntity.createAnalysisSubResult(
                dynamicContext.getStep(), 
                "analysis_progress", 
                parseResult.toString(), 
                requestParameter.getSessionId());

        sendSseResult(dynamicContext, result);
        
        // 更新步骤
        dynamicContext.setStep(dynamicContext.getStep() + 1);
        
        return router(requestParameter, dynamicContext);
    }

    /**
     * 解析执行步骤
     */
    private Map<String, String> parseExecutionSteps(String planningResult) {
        Map<String, String> stepsMap = new HashMap<>();

        if (planningResult == null || planningResult.trim().isEmpty()) {
            return stepsMap;
        }

        try {
            // 使用正则表达式匹配步骤标题和详细内容
            Pattern stepPattern = Pattern.compile("### (第\\d+步：[^\\n]+)([\\s\\S]*?)(?=### 第\\d+步：|$)");
            Matcher matcher = stepPattern.matcher(planningResult);

            while (matcher.find()) {
                String stepTitle = matcher.group(1).trim();
                String stepContent = matcher.group(2).trim();

                // 提取步骤编号
                Pattern numberPattern = Pattern.compile("第(\\d+)步：");
                Matcher numberMatcher = numberPattern.matcher(stepTitle);

                if (numberMatcher.find()) {
                    String stepNumber = "第" + numberMatcher.group(1) + "步";
                    String fullStepInfo = stepTitle + "\n" + stepContent;
                    stepsMap.put(stepNumber, fullStepInfo);
                    log.debug("解析步骤: {} -> {}", stepNumber, stepTitle);
                }
            }

            // 如果没有匹配到详细步骤，尝试匹配简单的步骤列表
            if (stepsMap.isEmpty()) {
                Pattern simpleStepPattern = Pattern.compile("\\[ \\] (第\\d+步：[^\\n]+)");
                Matcher simpleMatcher = simpleStepPattern.matcher(planningResult);

                while (simpleMatcher.find()) {
                    String stepTitle = simpleMatcher.group(1).trim();
                    Pattern numberPattern = Pattern.compile("第(\\d+)步：");
                    Matcher numberMatcher = numberPattern.matcher(stepTitle);

                    if (numberMatcher.find()) {
                        String stepNumber = "第" + numberMatcher.group(1) + "步";
                        stepsMap.put(stepNumber, stepTitle);
                        log.debug("解析简单步骤: {} -> {}", stepNumber, stepTitle);
                    }
                }
            }

            log.info("成功解析 {} 个执行步骤", stepsMap.size());

        } catch (Exception e) {
            log.error("解析规划结果时发生错误", e);
        }

        return stepsMap;
    }

    @Override
    public StrategyHandler<ExecuteCommandEntity, DefaultFlowAgentExecuteStrategyFactory.DynamicContext, String> get(ExecuteCommandEntity requestParameter, DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return step4ExecuteStepsNode;
    }

}