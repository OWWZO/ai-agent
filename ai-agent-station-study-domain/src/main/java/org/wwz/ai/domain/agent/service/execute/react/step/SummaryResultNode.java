package org.wwz.ai.domain.agent.service.execute.react.step;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.reactor.agent.agent.AgentContext;
import org.wwz.ai.domain.agent.reactor.agent.agent.SummaryAgent;
import org.wwz.ai.domain.agent.reactor.agent.dto.File;
import org.wwz.ai.domain.agent.reactor.agent.dto.TaskSummaryResult;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.service.execute.react.step.factory.DefaultReactAgentExecuteStrategyFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * React 逻辑树 - 步骤3：生成任务总结并发送结果
 * 基于 ReAct 执行记忆生成总结，构建结果 Map，通过 Printer 发送
 */
@Slf4j
@Service
public class SummaryResultNode extends AbstractExecuteSupport {

    @Override
    protected String doApply(AgentRequest requestParameter, DefaultReactAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("React Step3: Summary and send result for requestId: {}", requestParameter.getRequestId());

        AgentContext agentContext = dynamicContext.getAgentContext();

        SummaryAgent summary = dynamicContext.getSummary();

        if (agentContext == null || summary == null || dynamicContext.getExecutor() == null) {
            throw new IllegalStateException("React Step3: agentContext/executor/summary is null, Step2 must run first.");
        }

        TaskSummaryResult result = summary.summaryTaskResult(
                dynamicContext.getExecutor().getMemory().getMessages(),
                requestParameter.getQuery()
        );

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
        dynamicContext.setStep(3);

        return "success";
    }

    @Override
    public StrategyHandler<AgentRequest, DefaultReactAgentExecuteStrategyFactory.DynamicContext, String> get(
            AgentRequest requestParameter,
            DefaultReactAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return null;
    }
}
