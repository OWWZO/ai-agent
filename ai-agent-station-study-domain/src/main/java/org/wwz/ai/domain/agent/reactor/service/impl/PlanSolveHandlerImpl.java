package org.wwz.ai.domain.agent.reactor.service.impl;


import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.reactor.agent.agent.AgentContext;
import org.wwz.ai.domain.agent.reactor.agent.agent.ExecutorAgent;
import org.wwz.ai.domain.agent.reactor.agent.agent.PlanningAgent;
import org.wwz.ai.domain.agent.reactor.agent.agent.SummaryAgent;
import org.wwz.ai.domain.agent.reactor.agent.dto.File;
import org.wwz.ai.domain.agent.reactor.agent.dto.SopRecallResponse;
import org.wwz.ai.domain.agent.reactor.agent.dto.TaskSummaryResult;
import org.wwz.ai.domain.agent.reactor.agent.enums.AgentState;
import org.wwz.ai.domain.agent.reactor.agent.enums.AgentType;
import org.wwz.ai.domain.agent.reactor.agent.util.ThreadUtil;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.reactor.service.AgentHandlerService;
import org.wwz.ai.domain.agent.reactor.service.SopRecallService;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

@Slf4j
@Component
public class PlanSolveHandlerImpl implements AgentHandlerService {

    @Autowired
    private ReactorConfig reactorConfig;

    @Autowired
    private SopRecallService sopRecallService;


    /**
     * 核心任务处理方法：负责协调规划Agent、执行Agent、总结Agent完成任务的全生命周期管理
     * 整体流程：SOP召回 → 规划任务 → 执行任务（单/多线程）→ 循环规划-执行 → 满足终止条件后总结/终止任务 → 返回结果
     * @param agentContext 代理上下文，存储任务执行过程中的所有上下文信息（如内存、文件、状态、打印机等）
     * @param request 代理请求，包含用户查询、请求参数等核心信息
     * @return 空字符串（结果通过printer发送，此处返回仅满足方法签名要求）
     */
    @Override
    public String handle(AgentContext agentContext, AgentRequest request) {

        // 1. 处理SOP（标准作业程序）召回逻辑，加载与当前任务匹配的SOP配置/规则
        handleSopRecall(agentContext, request);

        // 2. 初始化核心Agent组件
        // 规划Agent：负责拆解用户任务、生成执行步骤、判断任务是否完成
        PlanningAgent planning = new PlanningAgent(agentContext);
        // 执行Agent：负责执行规划Agent拆解的具体任务步骤
        ExecutorAgent executor = new ExecutorAgent(agentContext);
        // 总结Agent：负责任务完成后汇总执行结果、生成任务总结
        SummaryAgent summary = new SummaryAgent(agentContext);
        // 替换总结Agent的系统提示词中的{{query}}占位符为用户实际查询内容，确保总结贴合用户需求
        summary.setSystemPrompt(summary.getSystemPrompt().replace("{{query}}", request.getQuery()));

        // 3. 首次执行规划Agent，生成初始任务执行步骤
        String planningResult = planning.run(agentContext.getQuery());
        // 初始化步骤计数器，记录当前迭代执行的步数
        int stepIdx = 0;
        // 获取配置中允许的最大迭代步数，防止无限循环
        int maxStepNum = reactorConfig.getPlannerMaxSteps();

        // 4. 循环执行「规划-执行」流程，直到满足终止条件
        while (stepIdx <= maxStepNum) {
            // 将规划结果按<sep>分隔符拆分，转换为具体的任务列表，并为每个任务添加前缀说明
            List<String> planningResults = Arrays.stream(planningResult.split("<sep>"))
                    .map(task -> "你的任务是：" + task)
                    .collect(Collectors.toList());
            // 存储执行Agent的执行结果
            String executorResult;
            // 清空上下文中原有的任务产物文件，避免跨步骤文件干扰
            agentContext.getTaskProductFiles().clear();

            // 5. 判断任务数量，分「单任务」和「多任务」两种执行模式
            if (planningResults.size() == 1) {
                // 5.1 单任务模式：直接执行唯一的任务步骤
                executorResult = executor.run(planningResults.get(0));
            } else {
                // 5.2 多任务模式：使用多线程并行执行多个子任务，提升执行效率
                // 存储每个子任务的执行结果（任务内容为key，执行结果为value）
                Map<String, String> tmpTaskResult = new ConcurrentHashMap<>();
                // 创建倒计时锁存器，用于等待所有子线程执行完成（数量等于子任务数）
                CountDownLatch taskCount = ThreadUtil.getCountDownLatch(planningResults.size());
                // 记录执行Agent当前的内存索引，用于后续合并子Agent的内存信息
                int memoryIndex = executor.getMemory().size();
                // 存储子执行Agent实例，每个子任务对应一个独立的ExecutorAgent
                List<ExecutorAgent> slaveExecutors = new ArrayList<>();

                // 遍历所有子任务，为每个任务创建独立的执行Agent并启动线程
                for (String task : planningResults) {
                    // 创建子执行Agent，继承主Agent的上下文
                    ExecutorAgent slaveExecutor = new ExecutorAgent(agentContext);
                    // 同步主执行Agent的状态到子Agent
                    slaveExecutor.setState(executor.getState());
                    // 同步主执行Agent的内存消息到子Agent，保证上下文一致
                    slaveExecutor.getMemory().addMessages(executor.getMemory().getMessages());
                    // 将子Agent加入列表，用于后续合并内存和状态
                    slaveExecutors.add(slaveExecutor);

                    // 提交子任务到线程池执行
                    ThreadUtil.execute(() -> {
                        try {
                            // 执行当前子任务并获取结果
                            String taskResult = slaveExecutor.run(task);
                            // 将子任务结果存入线程安全的Map
                            tmpTaskResult.put(task, taskResult);
                        } finally {
                            // 无论任务执行成功/失败，都要减少倒计时锁存器计数
                            taskCount.countDown();
                        }
                    });
                }

                // 等待所有子线程执行完成（阻塞当前线程，直到锁存器计数为0）
                ThreadUtil.await(taskCount);

                // 6. 合并子执行Agent的内存和状态到主执行Agent
                for (ExecutorAgent slaveExecutor : slaveExecutors) {
                    // 复制子Agent新增的内存消息（从memoryIndex开始的部分）到主Agent
                    for (int i = memoryIndex; i < slaveExecutor.getMemory().size(); i++) {
                        executor.getMemory().addMessage(slaveExecutor.getMemory().get(i));
                    }
                    // 清空子Agent内存，释放资源
                    slaveExecutor.getMemory().clear();
                    // 同步子Agent的状态到主Agent
                    executor.setState(slaveExecutor.getState());
                }

                // 7. 将所有子任务的执行结果拼接为一个字符串，作为多任务执行的总结果
                executorResult = String.join("\n", tmpTaskResult.values());
            }

            // 8. 将本次执行结果传入规划Agent，生成下一轮的规划（或终止指令）
            planningResult = planning.run(executorResult);

            // 9. 终止条件1：规划Agent返回"finish"，表示任务完成，执行总结逻辑
            if ("finish".equals(planningResult)) {
                // 调用总结Agent，汇总执行内存中的消息和用户查询，生成任务总结结果
                TaskSummaryResult result = summary.summaryTaskResult(executor.getMemory().getMessages(), request.getQuery());

                // 构建最终返回的任务结果Map
                Map<String, Object> taskResult = new HashMap<>();
                // 添加任务总结文本
                taskResult.put("taskSummary", result.getTaskSummary());

                // 处理任务产物文件列表（优先使用总结Agent返回的文件，无则使用上下文文件）
                if (CollectionUtils.isEmpty(result.getFiles())) {
                    // 总结Agent无文件返回时，使用上下文的产物文件
                    if (!CollectionUtils.isEmpty(agentContext.getProductFiles())) {
                        List<File> fileResponses = agentContext.getProductFiles();
                        // 过滤掉内部中间搜索结果文件，只保留对外展示的产物文件
                        fileResponses.removeIf(file -> Objects.nonNull(file) && file.getIsInternalFile());
                        // 反转文件列表，让最新的文件排在前面
                        Collections.reverse(fileResponses);
                        taskResult.put("fileList", fileResponses);
                    }
                } else {
                    // 总结Agent有返回文件时，直接使用该文件列表
                    taskResult.put("fileList", result.getFiles());
                }

                // 通过上下文的打印机组件发送最终任务结果（包含总结和文件列表）
                agentContext.getPrinter().send("result", taskResult);

                // 任务完成，跳出循环
                break;
            }

            // 终止条件2：规划Agent或执行Agent进入空闲状态，达到最大迭代次数，终止任务
            if (planning.getState() == AgentState.IDLE || executor.getState() == AgentState.IDLE) {
                agentContext.getPrinter().send("result", "达到最大迭代次数，任务终止。");
                break;
            }

            // 终止条件3：规划Agent或执行Agent进入错误状态，任务执行异常，终止任务
            if (planning.getState() == AgentState.ERROR || executor.getState() == AgentState.ERROR) {
                agentContext.getPrinter().send("result", "任务执行异常，请联系管理员，任务终止。");
                break;
            }

            // 步骤计数器自增，进入下一轮循环
            stepIdx++;
        }

        // 方法返回空字符串（结果已通过printer发送，此处返回仅满足方法签名要求）
        return "";
    }

    @Override
    public Boolean support(AgentContext agentContext, AgentRequest request) {
        return AgentType.PLAN_SOLVE.getValue().equals(request.getAgentType());
    }

        /**
     * 处理SOP召回逻辑
     * 
     * @param agentContext 代理上下文
     * @param request 请求对象
     */
    private void handleSopRecall(AgentContext agentContext, AgentRequest request) {
        try {
            log.info("{} 开始执行SOP召回", request.getRequestId());
            
            // 调用SOP召回服务
            SopRecallResponse sopResponse = sopRecallService.sopRecall(
                    request.getRequestId(),
                    request.getQuery()
            );
            
            // 检查召回结果
            if (sopRecallService.isValidSopResult(sopResponse)) {
                String sopContent = sopResponse.getData().getChoosed_sop_string();
                String sopMode = sopResponse.getData().getSop_mode();
                
                log.info("{} SOP召回成功，模式：{}，内容长度：{}", 
                        request.getRequestId(), sopMode, sopContent.length());

                // 注入sopPrompt
                String sopPrompt = agentContext.getSopPrompt().replace("{{sop}}", sopContent);
                agentContext.setSopPrompt(sopPrompt);

            } else {
                log.warn("{} SOP召回失败或结果无效", request.getRequestId());
            }
            
        } catch (Exception e) {
            log.error("{} SOP召回处理异常", request.getRequestId(), e);
            // SOP召回失败不影响主流程，继续执行
        }
    }
}
