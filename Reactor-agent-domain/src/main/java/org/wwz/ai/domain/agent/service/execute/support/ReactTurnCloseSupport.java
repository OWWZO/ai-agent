package org.wwz.ai.domain.agent.service.execute.support;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.ledger.ExecutionLedgerRunSupport;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.memory.SessionWorkingMemoryService;
import org.wwz.ai.domain.agent.memory.ltm.LtmTurnSyncSupport;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.ReActAgent;
import org.wwz.ai.domain.agent.runtime.artifact.TaskSummaryArtifactProtocol;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.dto.TaskSummaryResult;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceReadStateStore;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.List;
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

    public void closeSuccessfulTurn(AgentContext agentContext,
                                    ReActAgent executor,
                                    String rawFinalAnswer,
                                    String entryAgent) {
        if (agentContext == null) {
            throw new IllegalStateException("agentContext is null");
        }
        persistWorkspaceReadState(agentContext);

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
