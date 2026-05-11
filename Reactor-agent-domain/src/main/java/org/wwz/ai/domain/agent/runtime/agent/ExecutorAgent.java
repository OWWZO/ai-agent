package org.wwz.ai.domain.agent.runtime.agent;


import com.alibaba.fastjson.JSON;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolChoice;
import org.wwz.ai.domain.agent.runtime.enums.AgentState;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;
import org.wwz.ai.domain.agent.runtime.llm.LLM;
import org.wwz.ai.domain.agent.runtime.prompt.ToolCallPrompt;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.util.FileUtil;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.model.response.AgentResponse;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 工具调用代理 - 处理工具/函数调用的基础代理类
 */
@Data
@Slf4j
@EqualsAndHashCode(callSuper = true)
public class ExecutorAgent extends ReActAgent {

    private List<ToolCall> toolCalls;
    private Integer maxObserve;
    private String systemPromptSnapshot;
    private String nextStepPromptSnapshot;

    private Integer taskId;

    public ExecutorAgent(AgentContext context) {
        setName("executor");
        setDescription("an agent that can execute tool calls.");
        ReactorRuntimeDependencies runtimeDependencies = requireRuntimeDependencies(context);
        ReactorConfig reactorConfig = runtimeDependencies.requireReactorConfig();

        StringBuilder toolPrompt = new StringBuilder();
        for (BaseTool tool : context.getToolCollection().getToolMap().values()) {
            toolPrompt.append(String.format("工具名：%s 工具描述：%s\n", tool.getName(), tool.getDescription()));
        }

        String promptKey = "default";
        String sopPromptKey = "default";
        String nextPromptKey = "default";
        setSystemPrompt(injectHistoryDialogue(
                reactorConfig.getExecutorSystemPromptMap().getOrDefault(promptKey, ToolCallPrompt.SYSTEM_PROMPT)
                        .replace("{{tools}}", toolPrompt.toString())
                        .replace("{{query}}", context.getQuery())
                        .replace("{{date}}", context.getDateInfo())
                        .replace("{{sopPrompt}}", context.getSopPrompt())
                        .replace("{{executorSopPrompt}}", reactorConfig.getExecutorSopPromptMap().getOrDefault(sopPromptKey, "")),
                context.getHistoryDialogue()));
        setNextStepPrompt(
                reactorConfig.getExecutorNextStepPromptMap().getOrDefault(nextPromptKey, ToolCallPrompt.NEXT_STEP_PROMPT)
                        .replace("{{tools}}", toolPrompt.toString())
                        .replace("{{query}}", context.getQuery())
                        .replace("{{date}}", context.getDateInfo())
                        .replace("{{sopPrompt}}", context.getSopPrompt())
                        .replace("{{executorSopPrompt}}", reactorConfig.getExecutorSopPromptMap().getOrDefault(sopPromptKey, "")));

        setSystemPromptSnapshot(getSystemPrompt());
        setNextStepPromptSnapshot(getNextStepPrompt());

        setPrinter(context.printer);
        setMaxSteps(reactorConfig.getPlannerMaxSteps());
        setLlm(new LLM(reactorConfig.getExecutorModelName(), "", runtimeDependencies));

        setContext(context);
        setMaxObserve(Integer.parseInt(reactorConfig.getMaxObserve()));

        // 初始化工具集合
        availableTools = context.getToolCollection();
        setDigitalEmployeePrompt(reactorConfig.getDigitalEmployeePrompt());

        setTaskId(0);
    }

    @Override
    public boolean think() {
        // 获取文件内容
        String filesStr = FileUtil.formatFileInfo(context.getProductFiles(), true);
        setSystemPrompt(getSystemPromptSnapshot().replace("{{files}}", filesStr));
        setNextStepPrompt(getNextStepPromptSnapshot().replace("{{files}}", filesStr));

        if (!getMemory().getLastMessage().getRole().equals(RoleType.USER)) {
            Message userMsg = Message.userMessage(getNextStepPrompt(), null);
            getMemory().addMessage(userMsg);
        }

        try {
            // 获取带工具选项的响应
            log.info("{} executor ask tool {}", context.getRequestId(), JSON.toJSONString(availableTools));
            CompletableFuture<LLM.ToolCallResponse> future = getLlm().askTool(
                    context,
                    getMemory().getMessages(),
                    Message.systemMessage(getSystemPrompt(), null),
                    availableTools,
                    ToolChoice.AUTO, null, false, 300
            );

            LLM.ToolCallResponse response = future.get();
            setToolCalls(response.getToolCalls());

            // 记录响应信息
            if (response.getContent() != null && !response.getContent().trim().isEmpty()) {
                String thinkResult = response.getContent();
                String subType = "taskThought";
                if (toolCalls.isEmpty()) {
                    Map<String, Object> taskSummary = new HashMap<>();
                    taskSummary.put("taskSummary", response.getContent());
                    taskSummary.put("fileList", context.getTaskProductFiles());
                    thinkResult = JSON.toJSONString(taskSummary);
                    subType = "taskSummary";
                    printer.send("task_summary", taskSummary);
                } else {
                    printer.send("tool_thought", response.getContent());
                }

            }

            // 创建并添加助手消息
            Message assistantMsg = response.getToolCalls() != null && !response.getToolCalls().isEmpty() && !"struct_parse".equals(llm.getFunctionCallType()) ?
                    Message.fromToolCalls(response.getContent(), response.getToolCalls()) :
                    Message.assistantMessage(response.getContent(), null);
            getMemory().addMessage(assistantMsg);

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
            if (!Arrays.asList("code_interpreter", "report_tool", "file_tool", "deep_search", "multimodalagent_tool", "data_analysis").contains(command.getFunction().getName())) {
                String toolName = command.getFunction().getName();
                printer.send("tool_result", AgentResponse.ToolResult.builder()
                                .toolName(toolName)
                                .toolParam(parseToolParam(command))
                                .toolResult(toolResult)
                                .toolCallId(command.getId())
                                .build(), null);
            }
            String result = writeToolObservationToMemory(command, outcome);
            results.add(result);
        }
        return String.join("\n\n", results);
    }

    private Map<String, Object> parseToolParam(ToolCall command) {
        try {
            return JSON.parseObject(command.getFunction().getArguments(), Map.class);
        } catch (Exception e) {
            log.warn("{} invalid tool arguments, fallback empty map. tool={}, args={}",
                    context.getRequestId(), command.getFunction().getName(), command.getFunction().getArguments());
            return Map.of();
        }
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

    private ReactorRuntimeDependencies requireRuntimeDependencies(AgentContext context) {
        if (context == null || context.getRuntimeDependencies() == null) {
            throw new IllegalStateException("ExecutorAgent 缺少 ReactorRuntimeDependencies");
        }
        return context.getRuntimeDependencies();
    }

}
