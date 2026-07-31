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
import org.wwz.ai.domain.agent.reactor.model.response.AgentResponse;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;

import java.util.*;
import java.util.concurrent.ExecutionException;

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
        setName("executor");
        setDescription("an agent that can execute tool calls.");
        ReactorRuntimeDependencies runtimeDependencies = requireRuntimeDependencies(context);
        ReactorConfig reactorConfig = runtimeDependencies.requireReactorConfig();

        setContext(context);
        // toolPrompt 仅兼容旧签名；system 不再注入 tools 正文（走 API tools[]）
        String toolPrompt = buildToolPrompt(context.getToolCollection());
        String promptKey = "default";
        Map<String, String> executorMap = reactorConfig.getExecutorSystemPromptMap();
        if (executorMap == null) {
            executorMap = Map.of();
        }
        String executorTemplate = ToolCallPrompt.ensureUserFacingReplyContract(
                executorMap.getOrDefault(promptKey, ToolCallPrompt.SYSTEM_PROMPT));
        setSystemPrompt(buildStableSystemPrompt(executorTemplate, toolPrompt, null, null));
        setNextStepPrompt(null);
        setPrinter(context.printer);
        setMaxSteps(reactorConfig.getPlannerMaxSteps());
        setLlm(new LLM(reactorConfig.getExecutorModelName(), "", runtimeDependencies));

                setMaxObserve(Integer.parseInt(reactorConfig.getMaxObserve()));

        // 初始化工具集合
        availableTools = context.getToolCollection();
        setDigitalEmployeePrompt(reactorConfig.getDigitalEmployeePrompt());

        setTaskId(0);
    }

    @Override
    public boolean think() {
        // system 固定；productFiles/nextStep 不注入（prompt cache）
        // 不再注入 nextStep user；仅记忆为空时用 query 垫底
        Message lastMessage = getMemory().getLastMessage();
        if (lastMessage == null) {
            String seed = context.getQuery() == null ? "" : context.getQuery();
            getMemory().addMessage(Message.userMessage(seed, null));
        }
        try {
            // 获取带工具选项的响应；超时等瞬态错误走指数退避重试
            log.info("{} executor ask tool {}", context.getRequestId(), JSON.toJSONString(availableTools));
            LLM.ToolCallResponse response = LlmRequestRetry.call(
                    "executor-think:" + context.getRequestId(),
                    () -> awaitAskTool(getLlm().askTool(
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
                    taskSummary.put("fileList", context.getTaskProductFiles());
                    printer.send("task_summary", taskSummary);
                } else {
                    printer.send("tool_thought", response.getContent());
                }
            }

            Message assistantMsg = response.getToolCalls() != null && !response.getToolCalls().isEmpty() && !"struct_parse".equals(llm.getFunctionCallType()) ?
                    Message.fromToolCalls(response.getContent(), response.getReasoningContent(), response.getToolCalls()) :
                    Message.assistantMessage(response.getContent(), response.getReasoningContent(), null);
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
            if (!Arrays.asList("code_interpreter", "report_tool", "document_generate", "slides_generate", "excel_generator", "checklist_generate", "template_filler", "document_template", "theme_designer", "chart_generator", "file_tool", "deep_search", "multimodalagent_tool", "data_analysis", "canvas_publish", "get_html_canvas_guide", "get_genui_guide", "list_ui_components", "emit_ui_tree", "emit_ui_patch").contains(command.getFunction().getName())) {
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

    private static LLM.ToolCallResponse awaitAskTool(
            java.util.concurrent.CompletableFuture<LLM.ToolCallResponse> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(cause);
        }
    }

}
