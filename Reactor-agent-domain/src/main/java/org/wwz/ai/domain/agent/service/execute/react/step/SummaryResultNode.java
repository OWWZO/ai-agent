package org.wwz.ai.domain.agent.service.execute.react.step;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.ledger.ExecutionLedgerRunSupport;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.memory.SessionWorkingMemoryService;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.artifact.TaskSummaryArtifactProtocol;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.dto.TaskSummaryResult;
import org.wwz.ai.domain.agent.service.execute.react.step.factory.DefaultReactAgentExecuteStrategyFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * React 逻辑树 - 步骤3：发送终答结果。
 * React 终答正文复用 Summary 的 {@code $$$} + artifactKey 协议勾选交付文件。
 */
@Slf4j
@Service
public class SummaryResultNode extends AbstractExecuteSupport {

    @Resource
    private SessionWorkingMemoryService sessionWorkingMemoryService;

    @Override
    protected String doApply(AgentRequest requestParameter, DefaultReactAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("React Step3: Send React final answer for requestId: {}", requestParameter.getRequestId());

        AgentContext agentContext = dynamicContext.getAgentContext();
        if (agentContext == null || dynamicContext.getExecutor() == null) {
            throw new IllegalStateException("React Step3: agentContext/executor is null, Step2 must run first.");
        }

        String rawFinalAnswer = StringUtils.defaultString(dynamicContext.getFinalAnswer());
        TaskSummaryResult result = TaskSummaryArtifactProtocol.parse(
                rawFinalAnswer,
                agentContext.getVisibleArtifactBindings()
        );

        String taskSummary = StringUtils.defaultString(result.getTaskSummary());
        Map<String, Object> taskResult = new HashMap<>();
        taskResult.put("taskSummary", taskSummary);

        if (CollectionUtils.isEmpty(result.getFiles())) {
            // 模型未勾选交付物时，回退全部可见产物（与历史 SummaryResultNode 一致）
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
        persistWorkingMemory(agentContext, dynamicContext, ExecutionLedgerConstants.ENTRY_AGENT_REACT);
        dynamicContext.setStep(3);

        return "success";
    }

    private void persistWorkingMemory(AgentContext agentContext,
                                      DefaultReactAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                      String entryAgent) {
        if (sessionWorkingMemoryService == null || agentContext == null || dynamicContext.getExecutor() == null) {
            return;
        }
        Long runId = agentContext.getAgentRunState() == null ? null : agentContext.getAgentRunState().getRunId();
        sessionWorkingMemoryService.persistTurn(
                agentContext.getSessionId(),
                agentContext.getRequestId(),
                runId,
                entryAgent,
                dynamicContext.getExecutor().exportWorkingMemoryDelta()
        );
    }

    @Override
    public StrategyHandler<AgentRequest, DefaultReactAgentExecuteStrategyFactory.DynamicContext, String> get(
            AgentRequest requestParameter,
            DefaultReactAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return null;
    }
}
