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
import org.wwz.ai.domain.agent.memory.ltm.LtmTurnSyncSupport;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceReadStateStore;
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

    @Resource
    private WorkspaceReadStateStore workspaceReadStateStore;

    
    private void persistWorkspaceReadState(AgentContext agentContext) {
        // read-state 属于会话工作区的辅助状态，持久化失败只记录告警，不阻断终答和
        // ledger 收口；下一轮仍可从可用的文件引用继续执行。
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
    protected String doApply(AgentRequest requestParameter, DefaultReactAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("React Step3: Send React final answer for requestId: {}", requestParameter.getRequestId());

        // Step3 是本轮成功收口的唯一节点：先解析可见 artifact 并发送 result，再结束
        // ledger，随后投影 working memory/LTM。这样失败执行不会被错误写入下一轮上下文。
        AgentContext agentContext = dynamicContext.getAgentContext();
        if (agentContext == null || dynamicContext.getExecutor() == null) {
            throw new IllegalStateException("React Step3: agentContext/executor is null, Step2 must run first.");
        }
        persistWorkspaceReadState(agentContext);

        // 终答中的 artifactKey 由协议解析为可交付文件；未显式勾选时回退到本轮全部可见产物。
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
            // 该回退只作用于当前 result 展示，不改变 artifact registry 的可见性集合。
            List<File> fileResponses = agentContext.getReversedVisibleArtifactFiles();
            if (!CollectionUtils.isEmpty(fileResponses)) {
                taskResult.put("fileList", fileResponses);
            }
        } else {
            taskResult.put("fileList", result.getFiles());
        }

        agentContext.getPrinter().send("result", taskResult);
        // 先结束 Execution Ledger，再写 working memory，确保两者都只由本轮成功结果驱动。
        ExecutionLedgerRunSupport.finishRun(
                agentContext,
                ExecutionLedgerConstants.STATUS_SUCCESS,
                taskSummary,
                null,
                null
        );
        persistWorkingMemory(agentContext, dynamicContext, ExecutionLedgerConstants.ENTRY_AGENT_REACT);
        LtmTurnSyncSupport.syncSuccessfulTurn(agentContext, dynamicContext.getExecutor());
        dynamicContext.setStep(3);

        return "success";
    }

    private void persistWorkingMemory(AgentContext agentContext,
                                      DefaultReactAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                      String entryAgent) {
        // working memory 只接收本轮执行器导出的增量，并关联 runId/requestId；不回写整段
        // 历史消息，避免把 ledger 事实源和下一轮提示词投影混成第二套主账本。
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
