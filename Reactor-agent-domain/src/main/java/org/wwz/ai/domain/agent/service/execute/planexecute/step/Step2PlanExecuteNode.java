package org.wwz.ai.domain.agent.service.execute.planexecute.step;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.ledger.ExecutionLedgerRunSupport;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.memory.SessionWorkingMemoryService;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.ExecutorAgent;
import org.wwz.ai.domain.agent.runtime.agent.ReActAgent;
import org.wwz.ai.domain.agent.runtime.agent.ReactImplAgent;
import org.wwz.ai.domain.agent.runtime.agent.SummaryAgent;
import org.wwz.ai.domain.agent.runtime.artifact.TaskSummaryArtifactProtocol;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.SubTaskExecutionResult;
import org.wwz.ai.domain.agent.runtime.dto.TaskSummaryResult;
import org.wwz.ai.domain.agent.runtime.enums.AgentState;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;
import org.wwz.ai.domain.agent.runtime.executor.AgentExecutorSupport;
import org.wwz.ai.domain.agent.runtime.llm.LLM;
import org.wwz.ai.domain.agent.runtime.planmode.PlanModePromptInjector;
import org.wwz.ai.domain.agent.runtime.prompt.PlanSolvePrompt;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.factory.AgentToolCollectionFactory;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceReadStateStore;
import org.wwz.ai.domain.agent.service.execute.planexecute.step.factory.DefaultPlanSolveAgentExecuteStrategyFactory;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PlanSolve 逻辑树 - 步骤2：单主代理 ReAct 循环（对齐 React / cchaha）。
 * <p>
 * 主路径：ReactImplAgent（system 固定 + messages append-only）→ 无 tool 文本即终答 → 打包 result。
 * 不再 new PlanningAgent / ExecutorAgent / SummaryAgent 外循环。
 * <p>
 * 下方 parallel helper 仅保留给历史单测/兼容调用，主路径不使用。
 */
@Slf4j
@Service
public class Step2PlanExecuteNode extends AbstractExecuteSupport {

    private static final int DEFAULT_PLANNER_MAX_PARALLEL_TASKS = 2;

    private static final Pattern FINISH_BRACKET = Pattern.compile(
            "(?is)^\\s*Finish\\s*\\[\\s*(.*?)\\s*]\\s*$");
    private static final Pattern FINISH_INLINE = Pattern.compile(
            "(?is)Finish\\s*\\[\\s*(.*?)\\s*]");

    @Resource
    private SessionWorkingMemoryService sessionWorkingMemoryService;

    @Resource
    private WorkspaceReadStateStore workspaceReadStateStore;

    @Resource
    private ReactorConfig reactorConfig;

    @Resource
    private AgentToolCollectionFactory agentToolCollectionFactory;

    @Override
    protected String doApply(AgentRequest requestParameter, DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("PlanSolve Step2: single planner ReAct for requestId: {}", requestParameter.getRequestId());

        AgentContext agentContext = dynamicContext.getAgentContext();
        if (agentContext == null) {
            throw new IllegalStateException("PlanSolve Step2: agentContext is null, Step1 must run first.");
        }

        // 当前主路径是单个 ReactImplAgent 完成规划、工具执行和终答，不再套 Planning/Executor/Summary 外循环。
        ReactImplAgent planner = createPlanSolvePlanner(agentContext);
        String runResult = planner.run(agentContext.getQuery());
        String finalAnswer = resolveFinalAnswer(planner, runResult);

        dynamicContext.setExecutor(planner);
        dynamicContext.setFinalAnswer(finalAnswer);
        dynamicContext.setStep(2);

        // 先向前端发送规范化结果并结束账本，再把本轮增量投影为下一轮工作记忆。
        sendFinalResult(agentContext, finalAnswer);
        persistWorkingMemory(agentContext, planner, ExecutionLedgerConstants.ENTRY_AGENT_PLAN_SOLVE);
        org.wwz.ai.domain.agent.memory.ltm.LtmTurnSyncSupport.syncSuccessfulTurn(agentContext, planner);
        persistWorkspaceReadState(agentContext);
        return "success";
    }

    /**
     * 构造 PlanSolve 主代理：复用 ReactImplAgent 循环，叠加编排约定与 planner 模型/步数。
     */
    private ReactImplAgent createPlanSolvePlanner(AgentContext agentContext) {
        ReactImplAgent planner = new ReactImplAgent(agentContext);
        planner.setName("plan-solve");
        planner.setDescription("plan-execute main agent: plan mode, dispatch Agent subagents, final user reply");
        // PlanSolve 入口已 auto-enter plan mode：编排约定 + cchaha 硬只读 plan 指引
        planner.setSystemPrompt(PlanModePromptInjector.ensurePlanSolveWithPlanModeGuidance(planner.getSystemPrompt()));
        PlanModePromptInjector.applyIfPlanMode(agentContext, planner);

        if (reactorConfig != null) {
            Integer maxSteps = reactorConfig.getPlannerMaxSteps();
            if (maxSteps != null && maxSteps > 0) {
                planner.setMaxSteps(maxSteps);
            }
            String plannerModel = reactorConfig.getPlannerModelName();
            if (StringUtils.isNotBlank(plannerModel) && agentContext.getRuntimeDependencies() != null) {
                planner.setLlm(new LLM(plannerModel, "", agentContext.getRuntimeDependencies()));
            }
        }
        return planner;
    }

    private void sendFinalResult(AgentContext agentContext, String rawFinalAnswer) {
        TaskSummaryResult result = TaskSummaryArtifactProtocol.parse(
                StringUtils.defaultString(rawFinalAnswer),
                agentContext.getVisibleArtifactBindings()
        );

        String taskSummary = StringUtils.defaultString(result.getTaskSummary());
        Map<String, Object> taskResult = new HashMap<>();
        taskResult.put("taskSummary", taskSummary);

        if (CollectionUtils.isEmpty(result.getFiles())) {
            List<File> fileResponses = agentContext.getReversedVisibleArtifactFiles();
            if (!CollectionUtils.isEmpty(fileResponses)) {
                taskResult.put("fileList", fileResponses);
            }
        } else {
            taskResult.put("fileList", result.getFiles());
        }

        agentContext.getPrinter().send("result", taskResult);
        ExecutionLedgerRunSupport.finishRun(
                agentContext,
                ExecutionLedgerConstants.STATUS_SUCCESS,
                taskSummary,
                null,
                null
        );
    }

    /**
     * 仅接受「纯文本 assistant 轮」（无 tool_calls）作为用户终答。与 React RunReactNode 同语义。
     */
    public static String resolveFinalAnswer(ReActAgent executor, String runResult) {
        String fromMemory = findLastUserFacingAssistantText(executor);
        if (StringUtils.isNotBlank(fromMemory)) {
            return sanitizeUserFacingText(fromMemory);
        }

        if (executor != null
                && executor.getState() == AgentState.FINISHED
                && isPlausibleUserFacingRunResult(runResult)) {
            return sanitizeUserFacingText(runResult);
        }

        log.warn("PlanSolve final answer missing user-facing assistant text, request may have stopped mid-tools");
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
            if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
                continue;
            }
            if (StringUtils.isNotBlank(message.getContent())) {
                return message.getContent().trim();
            }
        }
        return null;
    }

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

    private void persistWorkingMemory(AgentContext agentContext, ReActAgent planner, String entryAgent) {
        if (sessionWorkingMemoryService == null || agentContext == null || planner == null) {
            return;
        }
        Long runId = agentContext.getAgentRunState() == null ? null : agentContext.getAgentRunState().getRunId();
        sessionWorkingMemoryService.persistTurn(
                agentContext.getSessionId(),
                agentContext.getRequestId(),
                runId,
                entryAgent,
                planner.exportWorkingMemoryDelta()
        );
    }

    private void persistWorkspaceReadState(AgentContext agentContext) {
        if (workspaceReadStateStore == null || agentContext == null) {
            return;
        }
        try {
            workspaceReadStateStore.persist(agentContext);
        } catch (Exception e) {
            log.warn("persist workspace read-state failed, requestId={}", agentContext.getRequestId(), e);
        }
    }

    @Override
    public StrategyHandler<AgentRequest, DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext, String> get(
            AgentRequest requestParameter,
            DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return null;
    }

    // -------------------------------------------------------------------------
    // 以下为旧 plan↔executor 并行路径保留实现（主路径不再调用；兼容历史单测子类）
    // -------------------------------------------------------------------------

    /**
     * @deprecated 主路径已改为单 React 主代理；保留供历史 Summary 兼容。
     */
    @Deprecated
    private void sendSummaryResult(AgentContext agentContext, SummaryAgent summary, Message planResult, AgentRequest request) {
        TaskSummaryResult result = summary.summaryTaskResult(Collections.singletonList(planResult), request.getQuery());
        sendLegacySummaryResult(agentContext, result);
    }

    /**
     * @deprecated 主路径已改为单 React 主代理终答。
     */
    @Deprecated
    private void sendSummaryResult(AgentContext agentContext, SummaryAgent summary, ExecutorAgent executor, AgentRequest request) {
        TaskSummaryResult result = summary.summaryTaskResult(executor.getMemory().getMessages(), request.getQuery());
        sendLegacySummaryResult(agentContext, result);
        if (executor != null) {
            persistWorkingMemory(agentContext, executor, ExecutionLedgerConstants.ENTRY_AGENT_PLAN_SOLVE);
            persistWorkspaceReadState(agentContext);
        }
    }

    private void sendLegacySummaryResult(AgentContext agentContext, TaskSummaryResult result) {
        Map<String, Object> taskResult = new HashMap<>();
        taskResult.put("taskSummary", result.getTaskSummary());
        if (CollectionUtils.isEmpty(result.getFiles())) {
            List<File> fileResponses = agentContext.getReversedVisibleArtifactFiles();
            if (!CollectionUtils.isEmpty(fileResponses)) {
                taskResult.put("fileList", fileResponses);
            }
        } else {
            taskResult.put("fileList", result.getFiles());
        }
        agentContext.getPrinter().send("result", taskResult);
        ExecutionLedgerRunSupport.finishRun(
                agentContext,
                ExecutionLedgerConstants.STATUS_SUCCESS,
                result.getTaskSummary(),
                null,
                null
        );
    }

    private void finishNonSuccessRun(AgentContext agentContext, int status, String errorCode, String errorMsg) {
        ExecutionLedgerRunSupport.finishRun(
                agentContext,
                status,
                null,
                errorCode,
                errorMsg
        );
    }

    protected Executor resolveTaskExecutor(AgentContext agentContext) {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            return Runnable::run;
        }
        return agentContext.getRuntimeDependencies().requireTaskExecutor();
    }

    protected List<SubTaskExecutionResult> executeParallelTasks(AgentContext parentContext,
                                                                AgentRequest request,
                                                                ExecutorAgent parentExecutor,
                                                                List<String> tasks) {
        int maxParallelTasks = resolvePlannerMaxParallelTasks();
        Map<String, SubTaskExecutionResult> resultMap = new ConcurrentHashMap<>();
        Executor taskExecutor = resolveTaskExecutor(parentContext);

        for (List<String> taskBatch : partitionTasks(tasks, maxParallelTasks)) {
            List<CompletableFuture<Void>> futures = new ArrayList<>(taskBatch.size());
            for (String task : taskBatch) {
                futures.add(AgentExecutorSupport.supplyAsync(taskExecutor, "planSolveExecutorTask", parentContext, () -> {
                    resultMap.put(task, executeSingleParallelTask(parentContext, request, parentExecutor, task));
                    return null;
                }));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }

        List<SubTaskExecutionResult> orderedResults = new ArrayList<>(tasks.size());
        for (String task : tasks) {
            orderedResults.add(resultMap.get(task));
        }
        return orderedResults;
    }

    protected SubTaskExecutionResult executeSingleParallelTask(AgentContext parentContext,
                                                               AgentRequest request,
                                                               ExecutorAgent parentExecutor,
                                                               String task) {
        AgentContext childContext = parentContext.forkForParallelTask(task);
        ToolCollection childToolCollection = agentToolCollectionFactory.buildForParallelTask(
                childContext,
                request,
                parentContext.getToolCollection()
        );
        childContext.setToolCollection(childToolCollection);

        ExecutorAgent childExecutor = new ExecutorAgent(childContext);
        childExecutor.setState(parentExecutor.getState());
        childExecutor.getMemory().clear();
        childExecutor.getMemory().addMessages(copyMessages(parentExecutor.getMemory().getMessages()));
        int baselineMemorySize = childExecutor.getMemory().size();

        String taskResult = childExecutor.run(task);
        List<Message> memoryIncrementMessages = new ArrayList<>();
        for (int i = baselineMemorySize; i < childExecutor.getMemory().size(); i++) {
            memoryIncrementMessages.add(childExecutor.getMemory().get(i));
        }
        return SubTaskExecutionResult.builder()
                .task(task)
                .taskResult(taskResult)
                .state(childExecutor.getState())
                .memoryIncrementMessages(memoryIncrementMessages)
                .build();
    }

    protected void mergeChildResultsIntoParent(ExecutorAgent parentExecutor, List<SubTaskExecutionResult> childResults) {
        if (childResults == null || childResults.isEmpty()) {
            return;
        }
        for (SubTaskExecutionResult childResult : childResults) {
            if (childResult == null || childResult.getMemoryIncrementMessages() == null) {
                continue;
            }
            for (Message message : childResult.getMemoryIncrementMessages()) {
                parentExecutor.getMemory().addMessage(message);
            }
        }
        parentExecutor.setState(reduceParentState(childResults));
    }

    protected AgentState reduceParentState(List<SubTaskExecutionResult> childResults) {
        boolean hasIdle = false;
        boolean allFinished = true;
        for (SubTaskExecutionResult childResult : childResults) {
            AgentState childState = childResult == null ? null : childResult.getState();
            if (childState == AgentState.ERROR) {
                return AgentState.ERROR;
            }
            if (childState == AgentState.IDLE) {
                hasIdle = true;
            }
            if (childState != AgentState.FINISHED) {
                allFinished = false;
            }
        }
        if (hasIdle) {
            return AgentState.IDLE;
        }
        if (allFinished) {
            return AgentState.FINISHED;
        }
        return AgentState.IDLE;
    }

    protected String joinTaskResults(List<SubTaskExecutionResult> childResults) {
        Map<String, String> orderedResults = new LinkedHashMap<>();
        for (SubTaskExecutionResult childResult : childResults) {
            if (childResult == null) {
                continue;
            }
            orderedResults.put(childResult.getTask(), childResult.getTaskResult());
        }
        return String.join("\n", orderedResults.values());
    }

    protected int resolvePlannerMaxParallelTasks() {
        Integer configuredLimit = reactorConfig.getPlannerMaxParallelTasks();
        if (configuredLimit == null || configuredLimit <= 0) {
            return DEFAULT_PLANNER_MAX_PARALLEL_TASKS;
        }
        return configuredLimit;
    }

    protected List<List<String>> partitionTasks(List<String> tasks, int batchSize) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        List<List<String>> batches = new ArrayList<>();
        for (int start = 0; start < tasks.size(); start += batchSize) {
            int end = Math.min(start + batchSize, tasks.size());
            batches.add(new ArrayList<>(tasks.subList(start, end)));
        }
        return batches;
    }

    private List<Message> copyMessages(List<Message> sourceMessages) {
        if (sourceMessages == null || sourceMessages.isEmpty()) {
            return List.of();
        }
        List<Message> copies = new ArrayList<>(sourceMessages.size());
        for (Message sourceMessage : sourceMessages) {
            if (sourceMessage == null) {
                continue;
            }
            copies.add(Message.builder()
                    .role(sourceMessage.getRole())
                    .content(sourceMessage.getContent())
                    .base64Image(sourceMessage.getBase64Image())
                    .toolCallId(sourceMessage.getToolCallId())
                    .toolCalls(sourceMessage.getToolCalls() == null
                            ? null
                            : new ArrayList<>(sourceMessage.getToolCalls()))
                    .build());
        }
        return copies;
    }
}
