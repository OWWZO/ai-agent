package org.wwz.ai.domain.agent.service.execute.planexecute.step;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.genie.agent.agent.AgentContext;
import org.wwz.ai.domain.agent.genie.agent.agent.ExecutorAgent;
import org.wwz.ai.domain.agent.genie.agent.agent.PlanningAgent;
import org.wwz.ai.domain.agent.genie.agent.agent.SummaryAgent;
import org.wwz.ai.domain.agent.genie.agent.dto.File;
import org.wwz.ai.domain.agent.genie.agent.dto.Message;
import org.wwz.ai.domain.agent.genie.agent.dto.TaskSummaryResult;
import org.wwz.ai.domain.agent.genie.agent.enums.AgentState;
import org.wwz.ai.domain.agent.genie.agent.util.ThreadUtil;
import org.wwz.ai.domain.agent.genie.config.GenieConfig;
import org.wwz.ai.domain.agent.genie.model.req.AgentRequest;
import org.wwz.ai.domain.agent.service.execute.planexecute.step.factory.DefaultPlanSolveAgentExecuteStrategyFactory;

import jakarta.annotation.Resource;
import java.util.*;
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
    private GenieConfig genieConfig;

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

//        if (planning.getState().equals(AgentState.FINISHED)) {
//            sendSummaryResult(agentContext, summary,Message.assistantMessage(planningResult,null), requestParameter);
//
//            dynamicContext.setStep(2);
//            return "success";
//        }
        int stepIdx = 0;
        int maxStepNum = genieConfig.getPlannerMaxSteps() != null ? genieConfig.getPlannerMaxSteps() : 5;

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
                agentContext.getPrinter().send("result", "达到最大迭代次数，任务终止。");
                break;
            }

            if (planning.getState() == AgentState.ERROR || executor.getState() == AgentState.ERROR) {
                agentContext.getPrinter().send("result", "任务执行异常，请联系管理员，任务终止。");
                break;
            }

            stepIdx++;
        }
        return "";
    }

    private void sendSummaryResult(AgentContext agentContext, SummaryAgent summary, Message planResult, AgentRequest request) {
        TaskSummaryResult result = summary.summaryTaskResult(Collections.singletonList(planResult), request.getQuery());
        Map<String, Object> taskResult = new HashMap<>();
        taskResult.put("taskSummary", result.getTaskSummary());

        if (CollectionUtils.isEmpty(result.getFiles())) {
            if (!CollectionUtils.isEmpty(agentContext.getProductFiles())) {
                List<File> fileResponses = agentContext.getProductFiles();
                fileResponses.removeIf(file -> Objects.nonNull(file) && file.getIsInternalFile());
                Collections.reverse(fileResponses);
                taskResult.put("fileList", fileResponses);
            }
        } else {
            taskResult.put("fileList", result.getFiles());
        }

        agentContext.getPrinter().send("result", taskResult);
    }

    private void sendSummaryResult(AgentContext agentContext, SummaryAgent summary, ExecutorAgent executor, AgentRequest request) {
        TaskSummaryResult result = summary.summaryTaskResult(executor.getMemory().getMessages(), request.getQuery());
        Map<String, Object> taskResult = new HashMap<>();
        taskResult.put("taskSummary", result.getTaskSummary());

        if (CollectionUtils.isEmpty(result.getFiles())) {
            if (!CollectionUtils.isEmpty(agentContext.getProductFiles())) {
                List<File> fileResponses = agentContext.getProductFiles();
                fileResponses.removeIf(file -> Objects.nonNull(file) && file.getIsInternalFile());
                Collections.reverse(fileResponses);
                taskResult.put("fileList", fileResponses);
            }
        } else {
            taskResult.put("fileList", result.getFiles());
        }

        agentContext.getPrinter().send("result", taskResult);
    }

    @Override
    public StrategyHandler<AgentRequest, DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext, String> get(
            AgentRequest requestParameter,
            DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return null;
    }
}
