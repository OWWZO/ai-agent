package org.wwz.ai.domain.agent.service.execute.react.step;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.ReActAgent;
import org.wwz.ai.domain.agent.runtime.agent.ReactImplAgent;
import org.wwz.ai.domain.agent.runtime.planmode.PlanModePromptInjector;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.enums.AgentState;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.service.execute.react.step.factory.DefaultReactAgentExecuteStrategyFactory;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * React 逻辑树 - 步骤2：执行 ReAct 推理与工具调用。
 * 终答仅取「无 tool_calls 的 assistant 文本」（用户向），不用中间 thought / tool observation。
 */
@Slf4j
@Service
public class RunReactNode extends AbstractExecuteSupport {

    private static final Pattern FINISH_BRACKET = Pattern.compile(
            "(?is)^\\s*Finish\\s*\\[\\s*(.*?)\\s*]\\s*$");
    private static final Pattern FINISH_INLINE = Pattern.compile(
            "(?is)Finish\\s*\\[\\s*(.*?)\\s*]");

    @Resource
    private SummaryResultNode step3SummaryResultNode;

    @Override
    protected String doApply(AgentRequest requestParameter, DefaultReactAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("React Step2: Run ReAct loop for requestId: {}", requestParameter.getRequestId());

        // Step1 已把请求、工具集合和 ledger recorder 固定在 AgentContext 中；Step2
        // 只创建一个执行器并让它完成完整 ReAct 生命周期，不能在节点层重复拼装工具或记忆。
        AgentContext agentContext = dynamicContext.getAgentContext();
        if (agentContext == null) {
            throw new IllegalStateException("React Step2: agentContext is null, Step1 must run first.");
        }

        // ReActAgent 内部负责 LLM 轮次、工具调用、取消和账本事件；节点只负责传递执行结果。
        ReactImplAgent executor = new ReactImplAgent(agentContext);
        PlanModePromptInjector.applyIfPlanMode(agentContext, executor);
        String runResult = executor.run(requestParameter.getQuery());
        // 只有无 tool_calls 的 assistant 文本才可作为用户终答，避免把中间观察结果泄露到结果区。
        String finalAnswer = resolveFinalAnswer(executor, runResult);

        dynamicContext.setExecutor(executor);
        dynamicContext.setFinalAnswer(finalAnswer);
        dynamicContext.setStep(2);

        return router(requestParameter, dynamicContext);
    }

    /**
     * 仅接受「纯文本 assistant 轮」（无 tool_calls）作为用户终答。
     * 不回退到带 tool_calls 的中间思考，也不把 tool observation 拼串当回复。
     */
    static String resolveFinalAnswer(ReActAgent executor, String runResult) {
        // 终答来源按可靠性排序：优先取执行器记忆中最后一条无 tool_calls 的 assistant
        // 文本，其次才接受 FINISHED 状态下的 run 返回值；中途停止或工具观察结果均拒绝。
        String fromMemory = findLastUserFacingAssistantText(executor);
        if (StringUtils.isNotBlank(fromMemory)) {
            return sanitizeUserFacingText(fromMemory);
        }

        // 仅当 run 以 FINISHED 结束时，才信任 run() 返回值（通常即最后一轮无 tool 文本）
        if (executor != null
                && executor.getState() == AgentState.FINISHED
                && isPlausibleUserFacingRunResult(runResult)) {
            return sanitizeUserFacingText(runResult);
        }

        log.warn("React final answer missing user-facing assistant text, request may have stopped mid-tools");
        return "任务已执行完成，但未能生成面向用户的最终说明。请补充问题后重试，或查看过程中的工具结果。";
    }

    private static String findLastUserFacingAssistantText(ReActAgent executor) {
        if (executor == null || executor.getMemory() == null) {
            return null;
        }
        List<Message> messages = executor.getMemory().getMessages();
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);
            if (message == null || message.getRole() != RoleType.ASSISTANT) {
                continue;
            }
            // 带 tool_calls 的 content 是过程叙述，不是终答
            if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
                continue;
            }
            if (StringUtils.isNotBlank(message.getContent())) {
                return message.getContent().trim();
            }
        }
        return null;
    }

    /**
     * 过滤明显是工具聚合 / 内部终止文案，避免误当用户回复。
     */
    private static boolean isPlausibleUserFacingRunResult(String runResult) {
        if (StringUtils.isBlank(runResult)) {
            return false;
        }
        String text = runResult.trim();
        if (text.startsWith("Terminated:")) {
            return false;
        }
        if ("No steps executed".equals(text) || "Thinking complete - no action needed".equals(text)) {
            return false;
        }
        // tool observation 常见形态：多段工具结果拼接
        if (text.contains("工具执行结果为:") || text.contains("Tool execution")) {
            return false;
        }
        return true;
    }

    static String sanitizeUserFacingText(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        Matcher whole = FINISH_BRACKET.matcher(text);
        if (whole.matches()) {
            return whole.group(1).trim();
        }
        Matcher inline = FINISH_INLINE.matcher(text);
        if (inline.find() && text.length() < 500) {
            String inner = inline.group(1).trim();
            if (StringUtils.isNotBlank(inner)) {
                return inner;
            }
        }
        return text;
    }

    @Override
    public StrategyHandler<AgentRequest, DefaultReactAgentExecuteStrategyFactory.DynamicContext, String> get(
            AgentRequest requestParameter,
            DefaultReactAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return step3SummaryResultNode;
    }
}
