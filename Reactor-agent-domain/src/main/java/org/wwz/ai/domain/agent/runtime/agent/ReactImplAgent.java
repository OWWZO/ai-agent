package org.wwz.ai.domain.agent.runtime.agent;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolChoice;
import org.wwz.ai.domain.agent.runtime.enums.AgentState;
import org.wwz.ai.domain.agent.runtime.llm.LLM;
import org.wwz.ai.domain.agent.runtime.llm.LlmUserFacingError;
import org.wwz.ai.domain.agent.runtime.prompt.AgentPrompt;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 工具调用代理 - 处理工具/函数调用的基础代理类
 */
@Data
@Slf4j
@EqualsAndHashCode(callSuper = true)
public class ReactImplAgent extends ReActAgent {

    // ===================== 核心状态字段 =====================
    /**
     * 大模型决策出的工具调用指令列表
     * 来源：think阶段调用LLM的askTool方法返回，包含待执行的工具名称、参数、调用ID等信息
     * 用途：act阶段根据该列表执行具体工具，是"思考"到"执行"的核心桥梁
     */
    private List<ToolCall> toolCalls;

    /**
     * 工具结果最大截断长度
     * 用途：避免工具返回超长结果（如大文本、大数据集）导致Token超限或处理异常，仅保留前N个字符
     * 取值：由外部配置/业务逻辑设置，null表示不截断
     */
    private Integer maxObserve;

    /**
     * 构造方法：初始化ReAct智能体核心配置
     * 核心逻辑：加载配置→构建提示词→初始化核心组件→设置初始状态
     *
     * @param context 智能体上下文（携带请求ID、用户查询、工具集合、文件信息等全量上下文）
     */
    public ReactImplAgent(AgentContext context) {
        AgentBootstrap.configure(this, context, new AgentBootstrap.Profile(
                "react",
                "an agent that can execute tool calls.",
                config -> Map.of(),
                AgentPrompt.REACT_SYSTEM_PROMPT,
                ReactorConfig::getReactModelName,
                ReactorConfig::getReactMaxSteps,
                true,
                true
        ));
    }

    /**
     * 重写思考方法（ReAct核心：Reason阶段）
     * 核心逻辑：
     * 1. 使用固定 system prompt 和当前 Memory 构造模型请求；
     * 2. 补充用户消息，保证对话历史的合法性；
     * 3. 调用大模型生成工具调用指令；
     * 4. 处理大模型响应，更新智能体记忆和工具调用列表；
     * 5. 异常处理：捕获异常并记录，标记智能体为完成状态。
     *
     * @return boolean 思考是否成功：true=成功生成工具调用指令，false=异常失败
     */
    @Override
    public boolean think() {
        // system 固定；productFiles/nextStep 不注入（prompt cache）。
        ensureQueryMessage();
        try {
            context.setStreamMessageType("tool_thought");

            LLM.ToolCallResponse response = awaitFuture(
                    getLlm().askTool(
                            context,
                            getMemory().getMessages(),
                            Message.systemMessage(getSystemPrompt(), null),
                            availableTools,
                            ToolChoice.AUTO,
                            null,
                            context.getIsStream(),
                            300
                    )
            );

            setToolCalls(response.getToolCalls());

            if (!Boolean.TRUE.equals(context.getIsStream())) {
                if (response.getReasoningContent() != null && !response.getReasoningContent().isEmpty()) {
                    printer.send(org.wwz.ai.domain.agent.runtime.llm.ReasoningContentExtractor.EVENT_TYPE,
                            response.getReasoningContent());
                }
                if (response.getContent() != null
                        && !response.getContent().isEmpty()
                        && response.getToolCalls() != null
                        && !response.getToolCalls().isEmpty()) {
                    printer.send("tool_thought", response.getContent());
                }
            }

            appendAssistantMessage(response);

        } catch (Exception e) {
            log.error("{} react think error", context.getRequestId(), e);
            String userMsg = LlmUserFacingError.toUserMessage(e);
            setThinkFailureReason(userMsg);
            getMemory().addMessage(Message.assistantMessage(userMsg, null));
            // ERROR（非 FINISHED）：上层返回 Terminated: LLM think failed，禁止假成功
            setState(AgentState.ERROR);
            return false;
        }

        return true; // 思考成功
    }

    /**
     * 重写执行方法（ReAct核心：Action阶段）
     * 核心逻辑：
     * 1. 校验工具调用列表：无工具则标记完成，返回最后一条消息内容；
     * 2. 执行工具：调用executeTools执行所有工具，获取执行结果；
     * 3. 处理工具结果：流式推送、截断超长结果、更新智能体记忆；
     * 4. 兼容两种工具调用模式：struct_parse（更新现有消息）、function_call（新增工具消息）；
     * 5. 聚合工具结果：返回所有工具结果的拼接字符串。
     *
     * @return String 所有工具执行结果的聚合字符串（换行分隔）
     */
    @Override
    public String act() {
        // 无 tool_calls：本轮 assistant text 即面向用户的终答
        if (toolCalls == null || toolCalls.isEmpty()) {
            setState(AgentState.FINISHED);
            Message last = getMemory().getLastMessage();
            String finalText = last == null ? null : last.getContent();
            return finalText == null ? "" : finalText;
        }

        Map<String, ToolExecutionOutcome> toolOutcomes = executeToolOutcomes(toolCalls);
        List<String> results = new ArrayList<>();

        for (ToolCall command : toolCalls) {
            ToolExecutionOutcome outcome = toolOutcomes.get(command.getId());
            String toolResult = outcome == null ? "" : outcome.getToolResult();

            sendToolResult(command, toolResult);

            String result = writeToolObservationToMemory(command, outcome);
            results.add(result);
        }

        return String.join("\n\n", results);
    }

    @Override
    protected Integer resolveMaxObserveLength() {
        return maxObserve;
    }

}
