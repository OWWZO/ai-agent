package org.wwz.ai.domain.agent.service.execute.flow.step;

import org.wwz.ai.domain.agent.model.entity.AgentExecuteResultEntity;
import org.wwz.ai.domain.agent.model.entity.ExecuteCommandEntity;
import org.wwz.ai.domain.agent.model.entity.ExecutionPlanStep;
import org.wwz.ai.domain.agent.service.execute.flow.step.factory.DefaultFlowAgentExecuteStrategyFactory;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

        // 从上下文直接获取结构化执行计划（由 Step2 解析并写入，避免再次做字符串解析）
        List<ExecutionPlanStep> executionPlan = dynamicContext.getExecutionPlan();
        if (executionPlan == null || executionPlan.isEmpty()) {
            log.warn("结构化执行计划为空，无法继续执行（planningResultRawLength={})",
                    dynamicContext.getPlanningResultRaw() != null ? dynamicContext.getPlanningResultRaw().length() : 0);
            throw new RuntimeException("结构化执行计划为空，无法解析步骤");
        }

        // 规划校验：提前发现 stepNumber 重复/缺失/异常，避免执行阶段再踩坑
        validateExecutionPlan(executionPlan);

        // 构建解析结果摘要（仅用于前端展示，不再产出执行用 stepsMap）
        String parseResult = buildPlanSummary(executionPlan);

        // 发送SSE结果
        AgentExecuteResultEntity result = AgentExecuteResultEntity.createAnalysisSubResult(
                dynamicContext.getStep(), 
                "analysis_progress", 
                parseResult, 
                requestParameter.getSessionId());

        sendSseResult(dynamicContext, result);
        
        // 更新步骤
        dynamicContext.setStep(dynamicContext.getStep() + 1);
        
        return router(requestParameter, dynamicContext);
    }

    /**
     * 校验结构化执行计划，避免后续执行阶段出现不可预期的错误
     */
    private void validateExecutionPlan(List<ExecutionPlanStep> executionPlan) {
        // 通过 Set 检测 stepNumber 是否重复，同时做基本边界校验
        Set<Integer> seen = new HashSet<>();
        for (ExecutionPlanStep step : executionPlan) {
            if (step == null) {
                throw new RuntimeException("执行计划中存在空步骤");
            }
            if (step.stepNumber() <= 0) {
                throw new RuntimeException("stepNumber 必须从 1 开始");
            }
            if (!seen.add(step.stepNumber())) {
                throw new RuntimeException("stepNumber 重复: " + step.stepNumber());
            }
            // actionType 用于表达“是否需要工具”，允许 LLM 步骤完全不使用工具
            String actionType = step.actionType() != null ? step.actionType().trim().toUpperCase() : "LLM";
            if (!"LLM".equals(actionType) && !"TOOL".equals(actionType)) {
                throw new RuntimeException("actionType 仅允许 LLM/TOOL，当前为: " + step.actionType());
            }
            if ("TOOL".equals(actionType) && (step.toolName() == null || step.toolName().isBlank())) {
                throw new RuntimeException("actionType=TOOL 时必须提供 toolName（禁止留空/虚构）");
            }
        }
    }

    private String buildPlanSummary(List<ExecutionPlanStep> executionPlan) {
        // 将计划按 stepNumber 排序后输出摘要，便于前端快速展示而不暴露内部实现细节
        List<ExecutionPlanStep> sorted = new ArrayList<>(executionPlan);
        sorted.sort(Comparator.comparingInt(ExecutionPlanStep::stepNumber));

        StringBuilder sb = new StringBuilder();
        sb.append("## 步骤解析结果\n\n");
        sb.append(String.format("成功解析 %d 个执行步骤：\n\n", sorted.size()));
        for (ExecutionPlanStep step : sorted) {
            sb.append(String.format("- **第%d步** %s（工具：%s）\n",
                    step.stepNumber(),
                    step.stepName() != null ? step.stepName() : "未命名步骤",
                    step.toolName() != null ? step.toolName() : "无"));
            // 同步展示 actionType，便于确认哪些步骤会跳过 LLM 调用以提速
            sb.append(String.format("  - actionType: %s\n", step.actionType() != null ? step.actionType() : "LLM"));
        }
        return sb.toString();
    }

    @Override
    public StrategyHandler<ExecuteCommandEntity, DefaultFlowAgentExecuteStrategyFactory.DynamicContext, String> get(ExecuteCommandEntity requestParameter, DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return step4ExecuteStepsNode;
    }

}
