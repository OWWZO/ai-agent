package org.wwz.ai.domain.agent.service.execute.planexecute.step;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.ExecutorAgent;
import org.wwz.ai.domain.agent.runtime.agent.PlanningAgent;
import org.wwz.ai.domain.agent.runtime.agent.SummaryAgent;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.SubTaskExecutionResult;
import org.wwz.ai.domain.agent.runtime.dto.TaskSummaryResult;
import org.wwz.ai.domain.agent.runtime.enums.AgentState;
import org.wwz.ai.domain.agent.runtime.executor.AgentExecutorSupport;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.factory.AgentToolCollectionFactory;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.ledger.ExecutionLedgerRunSupport;
import org.wwz.ai.domain.agent.service.execute.planexecute.step.factory.DefaultPlanSolveAgentExecuteStrategyFactory;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * PlanSolve 逻辑树 - 步骤2：规划-执行循环
 * 初始化 Planning/Executor/Summary Agent，首次规划，循环执行直至终止
 */
@Slf4j
@Service
public class Step2PlanExecuteNode extends AbstractExecuteSupport {

    private static final int DEFAULT_PLANNER_MAX_PARALLEL_TASKS = 2;

    @Resource
    private ReactorConfig reactorConfig;

    @Resource
    private AgentToolCollectionFactory agentToolCollectionFactory;

    @Override
    protected String doApply(AgentRequest requestParameter, DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("PlanSolve Step2: Plan-execute loop for requestId: {}", requestParameter.getRequestId());

        AgentContext agentContext = dynamicContext.getAgentContext();
        if (agentContext == null) {
            throw new IllegalStateException("PlanSolve Step2: agentContext is null, Step1 must run first.");
        }

        PlanningAgent planning = new PlanningAgent(agentContext);
        ExecutorAgent executor = new ExecutorAgent(agentContext);
        SummaryAgent summary = new SummaryAgent(agentContext);
        summary.setSystemPrompt(summary.getSystemPrompt().replace("{{query}}", requestParameter.getQuery()));

        dynamicContext.setPlanning(planning);
        dynamicContext.setExecutor(executor);
        dynamicContext.setSummary(summary);

        String planningResult = planning.run(agentContext.getQuery());

        int stepIdx = 0;
        int maxStepNum = reactorConfig.getPlannerMaxSteps() != null ? reactorConfig.getPlannerMaxSteps() : 5;

        while (stepIdx <= maxStepNum) {
            List<String> planningResults = Arrays.stream(planningResult.split("<sep>"))
                    .map(task -> "你的任务是：" + task)
                    .collect(Collectors.toList());
            String executorResult;
            agentContext.getTaskProductFiles().clear();

            if (planningResults.size() == 1) {
                executorResult = executor.run(planningResults.get(0));
            } else {
                List<SubTaskExecutionResult> childResults = executeParallelTasks(agentContext, requestParameter, executor, planningResults);
                mergeChildResultsIntoParent(executor, childResults);
                executorResult = joinTaskResults(childResults);
            }

            planningResult = planning.run(executorResult);

            if ("finish".equals(planningResult)) {
                sendSummaryResult(agentContext, summary, executor, requestParameter);
                break;
            }

            if (planning.getState() == AgentState.IDLE || executor.getState() == AgentState.IDLE) {
                String message = "达到最大迭代次数，任务终止。";
                agentContext.getPrinter().send("result", message);
                finishNonSuccessRun(agentContext, ExecutionLedgerConstants.STATUS_STOPPED, "PLAN_SOLVE_STOPPED", message);
                break;
            }

            if (planning.getState() == AgentState.ERROR || executor.getState() == AgentState.ERROR) {
                String message = "任务执行异常，请联系管理员，任务终止。";
                agentContext.getPrinter().send("result", message);
                finishNonSuccessRun(agentContext, ExecutionLedgerConstants.STATUS_FAILED, "PLAN_SOLVE_ERROR", message);
                break;
            }

            stepIdx++;
        }
        if (stepIdx > maxStepNum) {
            String message = "达到最大迭代次数，任务终止。";
            agentContext.getPrinter().send("result", message);
            finishNonSuccessRun(agentContext, ExecutionLedgerConstants.STATUS_STOPPED, "PLAN_SOLVE_MAX_STEP", message);
        }
        return "";
    }

    private void sendSummaryResult(AgentContext agentContext, SummaryAgent summary, Message planResult, AgentRequest request) {
        TaskSummaryResult result = summary.summaryTaskResult(Collections.singletonList(planResult), request.getQuery());
        sendSummaryResult(agentContext, result);
    }

    private void sendSummaryResult(AgentContext agentContext, SummaryAgent summary, ExecutorAgent executor, AgentRequest request) {
        TaskSummaryResult result = summary.summaryTaskResult(executor.getMemory().getMessages(), request.getQuery());
        sendSummaryResult(agentContext, result);
    }

    /**
     * 汇总最终展示结果，并以成功态结束本次 run。
     */
    private void sendSummaryResult(AgentContext agentContext, TaskSummaryResult result) {
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

    @Override
    public StrategyHandler<AgentRequest, DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext, String> get(
            AgentRequest requestParameter,
            DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return null;
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

    /**
     * PlanSolve 外层 task 并发统一走独立 taskExecutor。
     */
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
                futures.add(AgentExecutorSupport.supplyAsync(taskExecutor, "planSolveExecutorTask", () -> {
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
