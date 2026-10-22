package org.wwz.ai.domain.agent.service.execute.support;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.ledger.ExecutionLedgerRunSupport;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.memory.SessionWorkingMemoryService;
import org.wwz.ai.domain.agent.memory.ltm.LtmTurnSyncSupport;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.ReActAgent;
import org.wwz.ai.domain.agent.runtime.artifact.TaskSummaryArtifactProtocol;
import org.wwz.ai.domain.agent.runtime.dto.TaskSummaryResult;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceReadStateStore;

import jakarta.annotation.Resource;
import java.util.Map;

/**
 * React / PlanSolve 共用收口：发 result、结束 ledger、投影 working memory / LTM / workspace read-state。
 */
@Slf4j
@Component
public class ReactTurnCloseSupport {

    @Resource
    private SessionWorkingMemoryService sessionWorkingMemoryService;

    @Resource
    private WorkspaceReadStateStore workspaceReadStateStore;

    /**
     * 两个 Close 节点的公共收口入口。
     * <p>
     * React 与 PlanSolve 的 Close 节点各有自己的 DynamicContext 泛型（wrench 策略树
     * 要求节点绑定各自的上下文类型），但节点体逐字相同，只有链路名前缀、异常文案与
     * ledger 入口标识三个字符串不同。这里收掉节点体，两个节点只传自己的标识。
     *
     * @param linkName 链路名，用于日志与异常消息定位（"React" / "PlanSolve"）
     * @param entryAgent ledger 入口标识（ENTRY_AGENT_REACT / ENTRY_AGENT_PLAN_SOLVE）
     */
    public void closeFromNode(AgentContext agentContext,
                              ReActAgent executor,
                              String finalAnswer,
                              String linkName,
                              String entryAgent) {
        if (agentContext == null || executor == null) {
            throw new IllegalStateException(linkName + " Close: agentContext/executor is null, Run must run first.");
        }
        closeSuccessfulTurn(agentContext, executor, finalAnswer, entryAgent);
    }

    public void closeSuccessfulTurn(AgentContext agentContext,
                                    ReActAgent executor,
                                    String rawFinalAnswer,
                                    String entryAgent) {
        if (agentContext == null) {
            throw new IllegalStateException("agentContext is null");
        }
        persistWorkspaceReadState(agentContext);

        TaskSummaryResult result = TaskSummaryArtifactProtocol.resolveForDelivery(
                StringUtils.defaultString(rawFinalAnswer),
                agentContext.getVisibleArtifactBindings()
        );

        String taskSummary = StringUtils.defaultString(result.getTaskSummary());
        Map<String, Object> taskResult = TaskSummaryArtifactProtocol.toEventPayload(
                result,
                agentContext.getVisibleArtifactBindings()
        );

        agentContext.getPrinter().send("result", taskResult);
        ExecutionLedgerRunSupport.finishRun(
                agentContext,
                ExecutionLedgerConstants.STATUS_SUCCESS,
                taskSummary,
                null,
                null
        );
        persistWorkingMemory(agentContext, executor, entryAgent);
        if (executor != null) {
            LtmTurnSyncSupport.syncSuccessfulTurn(agentContext, executor);
        }
    }

    private void persistWorkingMemory(AgentContext agentContext, ReActAgent executor, String entryAgent) {
        if (sessionWorkingMemoryService == null || agentContext == null || executor == null) {
            return;
        }
        Long runId = agentContext.getAgentRunState() == null ? null : agentContext.getAgentRunState().getRunId();
        sessionWorkingMemoryService.persistTurn(
                agentContext.getSessionId(),
                agentContext.getRequestId(),
                runId,
                entryAgent,
                executor.exportWorkingMemoryDelta()
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
}
