package org.wwz.ai.domain.agent.service.execute.flow.step;

import org.wwz.ai.domain.agent.model.entity.AgentExecuteResultEntity;
import org.wwz.ai.domain.agent.model.entity.ExecuteCommandEntity;
import org.wwz.ai.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import org.wwz.ai.domain.agent.model.valobj.enums.AiClientTypeEnumVO;
import org.wwz.ai.domain.agent.service.execute.flow.step.factory.DefaultFlowAgentExecuteStrategyFactory;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import org.wwz.ai.domain.agent.model.entity.ExecutionPlanStep;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import org.wwz.ai.domain.agent.model.entity.JoyAgentEvent;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;
import java.util.UUID;

/**
 * 步骤2：执行步骤规划节点
 */
@Slf4j
@Service
public class Step2PlanningNode extends AbstractExecuteSupport {

     @Resource
     private Step3ParseStepsNode step3ParseStepsNode;

    @Override
    protected String doApply(ExecuteCommandEntity requestParameter, DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("\n--- 步骤2: 执行步骤规划 ---");

        // 获取配置信息
        AiAgentClientFlowConfigVO aiAgentClientFlowConfigVO = dynamicContext.getAiAgentClientFlowConfigVOMap().get(AiClientTypeEnumVO.PLANNING_CLIENT.getCode());

        // 获取规划客户端
        ChatClient planningChatClient = getChatClientByClientId(aiAgentClientFlowConfigVO.getClientId());

        String userRequest = dynamicContext.getCurrentTask();
        
        // 创建 BeanOutputConverter，指定 List<ExecutionPlanStep> 类型
        BeanOutputConverter<List<ExecutionPlanStep>> converter = new BeanOutputConverter<>(new ParameterizedTypeReference<>() {
        });
        
        String planningPrompt = buildStructuredPlanningPrompt(userRequest, converter);

        // 流式调用 LLM，前端实时看到输出
        int step = dynamicContext.getStep();
        String stepName = (step >= 1 && step < AgentExecuteResultEntity.STEP_NAMES.length)
                ? AgentExecuteResultEntity.STEP_NAMES[step] : "执行规划";

        // 使用自定义流式输出 plan_thought
        String planningResult = streamPlanThought(
                planningChatClient,
                planningPrompt,
                dynamicContext,
                requestParameter.getSessionId());
        
        log.info("执行步骤规划结果: {}", planningResult);
        
        // 保存规划阶段原始输出（便于排错/前端展示，不参与执行编排）
        dynamicContext.setPlanningResultRaw(planningResult);
        dynamicContext.setValue("planningResult", planningResult);

        // 将结构化计划转换为强类型对象，贯穿后续步骤，避免 JSON↔文本↔正则的反复转换
        List<ExecutionPlanStep> executionPlan = tryConvertExecutionPlan(converter, planningResult);
        // 兜底：模型常见会输出“看似 JSON 但不合法”的占位结构（例如 urlMap: {"xxx"}），这里自动触发一次自修复输出
        if (executionPlan == null || executionPlan.isEmpty()) {
            String repairPrompt = buildPlanningRepairPrompt(planningResult, converter);
            String repaired = callLlmWithMetrics(
                    planningChatClient,
                    repairPrompt,
                    dynamicContext,
                    requestParameter.getSessionId(),
                    step,
                    stepName,
                    "analysis_strategy_repair");
            dynamicContext.setPlanningResultRaw(repaired);
            dynamicContext.setValue("planningResult", repaired);
            executionPlan = tryConvertExecutionPlan(converter, repaired);
        }
        dynamicContext.setExecutionPlan(executionPlan);
        
        // --- 发送 JoyAgent 格式的 Plan 事件 ---
        if (executionPlan != null && !executionPlan.isEmpty()) {
            List<String> stages = new ArrayList<>();
            List<String> steps = new ArrayList<>();
            for (ExecutionPlanStep planStep : executionPlan) {
                stages.add(planStep.stepName());
                steps.add(planStep.description());
            }
            
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("stages", stages);
            resultMap.put("steps", steps);
            resultMap.put("title", "执行计划");
            resultMap.put("stepStatus", new ArrayList<>()); // 可选
            
            JoyAgentEvent planEvent = JoyAgentEvent.builder()
                    .taskId(null) // Plan event should not have a specific taskId to be handled correctly by frontend
                    .messageType("plan")
                    .resultMap(resultMap)
                    .messageId(java.util.UUID.randomUUID().toString())
                    .build();
            
            sendSseResult(dynamicContext, planEvent);
        }
        
        // 更新步骤
        dynamicContext.setStep(dynamicContext.getStep() + 1);
        
        return router(requestParameter, dynamicContext);
    }

    private String streamPlanThought(ChatClient chatClient, String userMessage,
                                     DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                     String sessionId) {
        StringBuilder fullText = new StringBuilder();
        try {
            var promptBuilder = chatClient.prompt()
                    .user(userMessage)
                    .advisors(withMetrics(dynamicContext, null));

            Flux<ChatResponse> flux = promptBuilder.stream().chatResponse();
            flux.doOnNext(cr -> {
                if (cr != null && cr.getResult() != null && cr.getResult().getOutput() != null) {
                    String text = cr.getResult().getOutput().getText();
                    if (text != null && !text.isEmpty()) {
                        fullText.append(text);
                        
                        Map<String, Object> resultMap = new HashMap<>();
                        resultMap.put("planThought", text);
                        resultMap.put("isFinal", false);
                        
                        JoyAgentEvent event = JoyAgentEvent.builder()
                                .taskId(sessionId) // Plan thought uses sessionId? Or maybe empty.
                                .messageType("plan_thought")
                                .resultMap(resultMap)
                                .messageId(UUID.randomUUID().toString())
                                .build();
                        sendSseResult(dynamicContext, event);
                    }
                }
            }).doOnError(e -> log.warn("LLM stream error: {}", e.getMessage())).blockLast();
        } catch (Exception e) {
            log.error("流式调用 LLM 异常: {}", e.getMessage(), e);
        }
        
        // Send final thought (optional, or just to mark end)
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("planThought", "");
        resultMap.put("isFinal", true);
        
        JoyAgentEvent event = JoyAgentEvent.builder()
                .taskId(sessionId)
                .messageType("plan_thought")
                .resultMap(resultMap)
                .messageId(UUID.randomUUID().toString())
                .build();
        sendSseResult(dynamicContext, event);

        return fullText.toString();
    }

    private List<ExecutionPlanStep> tryConvertExecutionPlan(BeanOutputConverter<List<ExecutionPlanStep>> converter, String planningResult) {
        // 优先走 Spring AI 的结构化输出转换，保持实现优雅与可维护
        try {
            return Objects.requireNonNullElse(converter.convert(sanitizePlanJson(planningResult)), List.of());
        } catch (Exception first) {
            // 兼容少数模型在 JSON 前后夹带解释性文本的情况：仅截取最外层 JSON 数组片段再尝试转换
            try {
                String trimmed = sanitizePlanJson(planningResult);
                int start = trimmed.indexOf('[');
                int end = trimmed.lastIndexOf(']');
                if (start >= 0 && end > start) {
                    String jsonArray = trimmed.substring(start, end + 1);
                    return Objects.requireNonNullElse(converter.convert(jsonArray), List.of());
                }
            } catch (Exception ignored) {
                // 保底：失败时交给 Step3 做校验并输出提示，不在这里抛出中断整个链路
            }
            return List.of();
        }
    }

    private String sanitizePlanJson(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        // 移除 Markdown code fence（```json / ```）
        s = s.replaceAll("(?s)```json\\s*", "");
        s = s.replaceAll("(?s)```\\s*", "");
        s = s.trim();

        // 如果模型把 JSON 数组整体包成一个字符串（例如 \"[ {...} ]\"），去掉最外层引号
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1);
        }

        // 修复常见的非法占位：": { \"xxx\" }" 这种不是合法对象，转成字符串占位
        // 例： "urlMap": {"从第一步搜索结果中获取的URL映射"} -> "urlMap":"从第一步搜索结果中获取的URL映射"
        s = s.replaceAll(":\\s*\\{\\s*\"([^\"]+)\"\\s*\\}", ":\"$1\"");

        return s;
    }

    private String buildPlanningRepairPrompt(String lastOutput, BeanOutputConverter<List<ExecutionPlanStep>> converter) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("# 执行计划 JSON 修复\n\n");
        prompt.append("你上一次输出的执行计划 **无法被程序解析为合法 JSON**（常见原因：toolParams 内出现非法对象占位，如 urlMap: {\"xxx\"}）。\n\n");
        prompt.append("## 修复要求（严格）\n");
        prompt.append("1. 只输出 **一个合法的 JSON 数组**，不要输出任何解释文字、Markdown 标记。\n");
        prompt.append("2. 允许存在不需要工具的步骤：actionType=LLM 时 toolName/toolParams 置空。\n");
        prompt.append("3. toolParams 必须是合法 JSON：\n");
        prompt.append("   - 不确定的值请用字符串占位（例如 \"<从上一步结果提取>\"），不要写非法对象。\n");
        prompt.append("   - 需要 map 的字段请写成 {\"key\":\"value\"} 形式。\n\n");
        prompt.append("## 输出格式（必须严格遵守）\n");
        prompt.append(converter.getFormat()).append("\n\n");
        prompt.append("## 上一次输出（供你修复参考）\n");
        prompt.append("```text\n");
        prompt.append(lastOutput != null ? lastOutput : "");
        prompt.append("\n```\n");
        return prompt.toString();
    }

    /**
     * 构建结构化的规划提示词（统一提示，由模型根据 MCP 分析结果自行判断）
     */
    private String buildStructuredPlanningPrompt(String userRequest, BeanOutputConverter<List<ExecutionPlanStep>> converter) {
        StringBuilder prompt = new StringBuilder();

        // 1. 任务分析部分 - 通用化用户需求分析
        prompt.append("# 智能执行计划生成\n\n");
        prompt.append("##用户需求分析\n");
        prompt.append("**完整用户请求：**\n");
        prompt.append("```\n");
        prompt.append(userRequest);
        prompt.append("\n```\n\n");
        
        // 4. 执行计划要求
        prompt.append("##执行计划要求\n");
        prompt.append("### 核心要求\n");
        prompt.append("1. **完整保留用户需求**: 必须将用户请求中的所有详细信息完整传递到每个执行步骤中\n");
        // 仅允许使用 MCP 工具（JDReactor 工具逻辑尚未完善，规划阶段禁止选择/引用）
        prompt.append("2. **仅使用MCP工具**: 只能选择并调用已接入的 MCP 工具来完成任务，不要选择或引用 JDReactor 相关工具\n");
        prompt.append("3. **精确mcp工具映射**: 仅当 actionType=TOOL 时才填写 toolName，且必须使用确切的函数名称，不允许模糊或错误的工具名\n");
        prompt.append("4. **参数完整性**: 所有工具调用必须包含用户原始需求中的完整参数信息\n");
        prompt.append("5. **依赖关系明确**: 基于MCP的分析安排合理的步骤顺序\n");
        prompt.append("6. **合理粒度**: 避免过度细分，每个步骤应该是完整且独立的功能单元\n\n");
        // 通过 actionType 显式标记步骤类型：能用工具完成的步骤优先标记为 TOOL，最终面向用户输出标记为 LLM
        prompt.append("7. **动作类型明确**: 每个步骤必须指定 actionType；数据获取/计算等用工具即可完成的步骤标记为 TOOL，最终汇总/写用户可见答案的步骤标记为 LLM\n\n");
        // 允许“无需工具”的步骤存在，避免为了满足格式硬塞一个不存在的工具名导致模型虚构
        prompt.append("8. **允许无工具步骤**: 如果某一步不需要任何工具，请将 actionType 设为 LLM，并将 toolName/toolParams 置空\n\n");
        // 明确禁止虚构工具，尽量让模型在不确定时选择 LLM 而不是编造 toolName
        prompt.append("9. **禁止编造工具**: 不要虚构任何工具名称/参数；如果不确定是否存在对应工具，选择 LLM 并留空 toolName/toolParams\n\n");
        // 避免无效 JSON：toolParams 必须始终是合法 JSON
        prompt.append("10. **JSON合法性**: toolParams 必须是合法 JSON；不确定的值用字符串占位（例如\"<从上一步提取>\"），不要输出非法对象\n\n");

        // 4. 格式规范 - 使用 BeanOutputConverter 生成的格式
        prompt.append("### 格式规范\n");
        prompt.append("请严格按照以下 JSON 格式输出执行计划：\n");
        prompt.append(converter.getFormat()).append("\n\n");

        // 8. 质量检查
        prompt.append("### 质量检查清单\n");
        prompt.append("生成计划后请确认：\n");
        prompt.append("- [ ] 输出的是合法的 JSON 数组\n");
        // toolName/toolParams 并非每一步都必须；当 actionType=LLM 时可为空，避免“为了满足清单而编造工具”
        prompt.append("- [ ] 每个步骤都有明确的 stepNumber 和 actionType\n");
        prompt.append("- [ ] 当 actionType=TOOL 时，toolName/toolParams 不为空且参数类型正确\n");
        prompt.append("- [ ] description 完整传递了用户需求\n");
        prompt.append("- [ ] 步骤逻辑连贯且无冗余\n\n");

        prompt.append("现在请开始生成 JSON 格式的执行步骤规划：\n");

        return prompt.toString();
    }


    @Override
    public StrategyHandler<ExecuteCommandEntity, DefaultFlowAgentExecuteStrategyFactory.DynamicContext, String> get(ExecuteCommandEntity requestParameter, DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return step3ParseStepsNode;
    }

}
