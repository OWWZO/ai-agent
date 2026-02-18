package org.wwz.ai.domain.agent.genie.service.impl;


import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.genie.agent.agent.AgentContext;
import org.wwz.ai.domain.agent.genie.agent.agent.ReActAgent;
import org.wwz.ai.domain.agent.genie.agent.agent.ReactImplAgent;
import org.wwz.ai.domain.agent.genie.agent.agent.SummaryAgent;
import org.wwz.ai.domain.agent.genie.agent.dto.File;
import org.wwz.ai.domain.agent.genie.agent.dto.TaskSummaryResult;
import org.wwz.ai.domain.agent.genie.agent.enums.AgentType;
import org.wwz.ai.domain.agent.genie.config.GenieConfig;
import org.wwz.ai.domain.agent.genie.model.req.AgentRequest;
import org.wwz.ai.domain.agent.genie.service.AgentHandlerService;

import java.util.*;

@Component
public class ReactHandlerImpl implements AgentHandlerService {

    @Autowired
    private GenieConfig genieConfig;

    /**
     * 简化版Agent任务处理核心方法：基于ReAct模式完成任务执行，再通过总结Agent生成任务总结并返回最终结果
     * 整体流程：初始化ReAct执行Agent和总结Agent → 执行用户查询任务 → 生成任务总结 → 处理产物文件 → 发送最终结果
     * ReAct模式核心：Reason（推理）→ Action（行动）→ Observation（观察），本版本为单次执行（无循环迭代）
     * @param agentContext 代理上下文，存储任务执行的全量上下文信息（如Agent内存、产物文件、结果输出组件等）
     * @param request 代理请求对象，包含用户原始查询、请求参数等核心信息
     * @return 空字符串（结果通过Printer组件输出，返回值仅满足方法签名要求，无实际业务意义）
     */
    @Override
    public String handle(AgentContext agentContext, AgentRequest request) {

        // 1. 初始化核心Agent组件
        // 创建ReAct模式的执行Agent（ReactImplAgent是ReActAgent的具体实现类）
        // ReActAgent负责按照「推理-行动-观察」的逻辑完成用户查询的全流程执行
        ReActAgent executor = new ReactImplAgent(agentContext);
        // 创建总结Agent，用于任务执行完成后，汇总执行过程和结果生成结构化的任务总结
        SummaryAgent summary = new SummaryAgent(agentContext);
        // 替换总结Agent系统提示词中的{{query}}占位符为用户实际查询内容
        // 确保总结内容精准贴合用户的原始需求，而非固定模板
        summary.setSystemPrompt(summary.getSystemPrompt().replace("{{query}}", request.getQuery()));

        // 2. 执行核心任务：调用ReAct执行Agent的run方法，传入用户查询内容
        // 执行过程中所有的交互记录、操作结果、中间状态都会被存储到executor的内存（Memory）中
        executor.run(request.getQuery());

        // 3. 生成任务总结：基于执行Agent的内存消息（完整执行过程）和用户原始查询，生成任务总结结果
        // TaskSummaryResult包含总结文本、产物文件列表等结构化信息
        TaskSummaryResult result = summary.summaryTaskResult(executor.getMemory().getMessages(), request.getQuery());

        // 4. 构建最终返回的结果Map，用于前端/调用方解析展示
        Map<String, Object> taskResult = new HashMap<>();
        // 将任务总结文本存入结果Map，key为"taskSummary"（约定好的字段名）
        taskResult.put("taskSummary", result.getTaskSummary());

        // 5. 处理任务产物文件列表（优先级：总结Agent返回的文件 > 上下文存储的文件）
        if (CollectionUtils.isEmpty(result.getFiles())) {
            // 分支1：总结Agent未返回文件时，使用上下文（agentContext）中的产物文件
            if (!CollectionUtils.isEmpty(agentContext.getProductFiles())) {
                // 获取上下文存储的所有产物文件
                List<File> fileResponses = agentContext.getProductFiles();
                // 过滤掉内部中间搜索结果文件（标记为isInternalFile=true的文件）
                // 仅保留对外展示的最终产物文件，避免返回无关的中间文件
                fileResponses.removeIf(file -> Objects.nonNull(file) && file.getIsInternalFile());
                // 反转文件列表顺序，让最新生成的文件排在前面，符合用户查看习惯
                Collections.reverse(fileResponses);
                // 将处理后的文件列表存入结果Map
                taskResult.put("fileList", fileResponses);
            }
        } else {
            // 分支2：总结Agent返回了文件列表，直接使用（总结Agent已完成文件的筛选/排序）
            taskResult.put("fileList", result.getFiles());
        }

        // 6. 通过上下文的Printer组件发送最终结果
        // Printer是Agent框架中统一的结果输出组件，"result"为结果类型标识，taskResult为具体结果内容
        agentContext.getPrinter().send("result", taskResult);

        // 返回空字符串：该方法的核心结果已通过Printer组件输出，返回值仅满足方法签名要求
        return "";
    }

    @Override
    public Boolean support(AgentContext agentContext, AgentRequest request) {
        return AgentType.REACT.getValue().equals(request.getAgentType());
    }
}
