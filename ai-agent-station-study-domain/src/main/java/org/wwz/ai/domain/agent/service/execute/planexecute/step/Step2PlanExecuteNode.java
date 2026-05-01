package org.wwz.ai.domain.agent.service.execute.planexecute.step;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.reactor.agent.agent.AgentContext;
import org.wwz.ai.domain.agent.reactor.agent.agent.ExecutorAgent;
import org.wwz.ai.domain.agent.reactor.agent.agent.PlanningAgent;
import org.wwz.ai.domain.agent.reactor.agent.agent.SummaryAgent;
import org.wwz.ai.domain.agent.reactor.agent.dto.File;
import org.wwz.ai.domain.agent.reactor.agent.dto.Message;
import org.wwz.ai.domain.agent.reactor.agent.dto.TaskSummaryResult;
import org.wwz.ai.domain.agent.reactor.agent.enums.AgentState;
import org.wwz.ai.domain.agent.reactor.agent.util.ThreadUtil;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.model.ledger.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.reactor.service.ExecutionLedgerRunSupport;
import org.wwz.ai.domain.agent.service.execute.planexecute.step.factory.DefaultPlanSolveAgentExecuteStrategyFactory;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

/**
 * PlanSolve 逻辑树 - 步骤2：规划-执行循环
 * 初始化 Planning/Executor/Summary Agent，首次规划，循环执行直至终止
 */
@Slf4j
@Service
public class Step2PlanExecuteNode extends AbstractExecuteSupport {

    @Resource
    private ReactorConfig reactorConfig;

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
                Map<String, String> tmpTaskResult = new ConcurrentHashMap<>();
                CountDownLatch taskCount = ThreadUtil.getCountDownLatch(planningResults.size());
                int memoryIndex = executor.getMemory().size();
                List<ExecutorAgent> slaveExecutors = new ArrayList<>();

                for (String task : planningResults) {
                    ExecutorAgent slaveExecutor = new ExecutorAgent(agentContext);
                    slaveExecutor.setState(executor.getState());
                    slaveExecutor.getMemory().clear();
                    slaveExecutor.getMemory().addMessages(executor.getMemory().getMessages());
                    slaveExecutors.add(slaveExecutor);

                    ThreadUtil.execute(() -> {
                        try {
                            String taskResult = slaveExecutor.run(task);
                            tmpTaskResult.put(task, taskResult);
                        } finally {
                            taskCount.countDown();
                        }
                    });
                }

                ThreadUtil.await(taskCount);

                for (ExecutorAgent slaveExecutor : slaveExecutors) {
                    for (int i = memoryIndex; i < slaveExecutor.getMemory().size(); i++) {
                        executor.getMemory().addMessage(slaveExecutor.getMemory().get(i));
                    }
                    slaveExecutor.getMemory().clear();
                    executor.setState(slaveExecutor.getState());
                }

                executorResult = String.join("\n", tmpTaskResult.values());
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
}
