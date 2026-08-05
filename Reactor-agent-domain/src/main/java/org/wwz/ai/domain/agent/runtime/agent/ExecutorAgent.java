package org.wwz.ai.domain.agent.runtime.agent;

import com.alibaba.fastjson.JSON;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolChoice;
import org.wwz.ai.domain.agent.runtime.enums.AgentState;
import org.wwz.ai.domain.agent.runtime.llm.LLM;
import org.wwz.ai.domain.agent.runtime.llm.LlmRequestRetry;
import org.wwz.ai.domain.agent.runtime.prompt.ToolCallPrompt;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具调用代理 - 处理工具/函数调用的基础代理类
 */
@Data
@Slf4j
@EqualsAndHashCode(callSuper = true)
public class ExecutorAgent extends ReActAgent {

    private List<ToolCall> toolCalls;
    private Integer maxObserve;
    private Integer taskId;

    public ExecutorAgent(AgentContext context) {
        ReactorConfig reactorConfig = AgentBootstrap.configure(this, context, new AgentBootstrap.Profile(
                "executor",
                "an agent that can execute tool calls.",
                ReactorConfig::getExecutorSystemPromptMap,
                ToolCallPrompt.SYSTEM_PROMPT,
                ReactorConfig::getExecutorModelName,
                ReactorConfig::getPlannerMaxSteps,
                true,
                true
        ));
        setMaxObserve(Integer.parseInt(reactorConfig.getMaxObserve()));
        setTaskId(0);
    }

    @Override
    public boolean think() {
        // system 固定；productFiles/nextStep 不注入（prompt cache）。
        ensureQueryMessage();
        try {
            // 获取带工具选项的响应；超时等瞬态错误走指数退避重试
            log.info("{} executor ask tool {}", context.getRequestId(), JSON.toJSONString(availableTools));
            LLM.ToolCallResponse response = LlmRequestRetry.call(
                    "executor-think:" + context.getRequestId(),
                    () -> awaitFuture(getLlm().askTool(
                            context,
                            getMemory().getMessages(),
                            Message.systemMessage(getSystemPrompt(), null),
                            availableTools,
                            ToolChoice.AUTO, null, false, 300
                    ))
            );
            setToolCalls(response.getToolCalls());

            if (response.getReasoningContent() != null && !response.getReasoningContent().isBlank()) {
                printer.send(org.wwz.ai.domain.agent.runtime.llm.ReasoningContentExtractor.EVENT_TYPE,
                        response.getReasoningContent());
            }
            if (response.getContent() != null && !response.getContent().trim().isEmpty()) {
                if (toolCalls.isEmpty()) {
                    Map<String, Object> taskSummary = new HashMap<>();
                    taskSummary.put("taskSummary", response.getContent());
                    taskSummary.put("fileList", context.getVisibleArtifactFiles());
                    printer.send("task_summary", taskSummary);
                } else {
                    printer.send("tool_thought", response.getContent());
                }
            }

            appendAssistantMessage(response);

        } catch (Exception e) {
            log.error("Oops! The " + getName() + "'s thinking process hit a snag: " + e.getMessage());
            getMemory().addMessage(Message.assistantMessage(
                    "Error encountered while processing: " + e.getMessage(), null));
            setState(AgentState.FINISHED);
            return false;
        }
        return true;
    }

    @Override
    public String act() {
        if (toolCalls.isEmpty()) {
            ReactorConfig reactorConfig = requireRuntimeDependencies(context).requireReactorConfig();
            setState(AgentState.FINISHED);
            // 删除工具结果
            if ("1".equals(reactorConfig.getClearToolMessage())) {
                getMemory().clearToolContext();
            }
            // 返回固定话术
            if (!reactorConfig.getTaskCompleteDesc().isEmpty()) {
                return reactorConfig.getTaskCompleteDesc();
            }
            return getMemory().getLastMessage().getContent();
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
    public String run(String request) {
        generateDigitalEmployee(request);
        ReactorConfig reactorConfig = requireRuntimeDependencies(context).requireReactorConfig();
        request = reactorConfig.getTaskPrePrompt() + request;
        context.setTask(request);
        return super.run(request);
    }

    @Override
    protected Integer resolveMaxObserveLength() {
        return maxObserve;
    }

}
