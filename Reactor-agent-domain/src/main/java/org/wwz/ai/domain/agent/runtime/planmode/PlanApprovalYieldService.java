package org.wwz.ai.domain.agent.runtime.planmode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.wwz.ai.domain.agent.ledger.ExecutionLedgerRunSupport;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationFinishRecord;
import org.wwz.ai.domain.agent.memory.SessionWorkingMemoryService;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.ReActAgent;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.TaskToolNames;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ExitPlanMode 让步收口：事务内写 approval + working_memory + WAITING_INPUT，提交后再发卡片。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanApprovalYieldService {

    private static final long DEFAULT_TIMEOUT_MINUTES = 30L;

    private final IPlanApprovalRepository planApprovalRepository;
    private final SessionWorkingMemoryService sessionWorkingMemoryService;

    @Transactional(rollbackFor = Exception.class)
    public PlanApprovalRecord yieldAndNotify(AgentContext agentContext,
                                             ReActAgent executor,
                                             AgentRequest request,
                                             PlanApprovalRequiredException signal,
                                             String entryAgent) {
        if (agentContext == null || signal == null) {
            throw new IllegalStateException("yield requires agentContext and signal");
        }
        String approvalId = "pa_" + UUID.randomUUID().toString().replace("-", "");
        Long runId = agentContext.getAgentRunState() == null ? null : agentContext.getAgentRunState().getRunId();
        String toolCallId = PlanApprovalObservationSupport.resolveExitPlanToolCallId(
                executor == null || executor.getMemory() == null ? null : executor.getMemory().getMessages(),
                signal.getToolCallId());
        if (StringUtils.isNotBlank(toolCallId)
                && executor != null
                && executor.getMemory() != null
                && !PlanApprovalObservationSupport.hasToolResult(executor.getMemory().getMessages(), toolCallId)) {
            executor.getMemory().addMessage(org.wwz.ai.domain.agent.runtime.dto.Message.toolMessage(
                    PlanApprovalObservationSupport.buildWaitingObservation(signal.getPlanContent(), approvalId),
                    toolCallId,
                    null));
        }
        Long toolInvocationId = null;
        if (agentContext.getAgentRunState() != null && StringUtils.isNotBlank(toolCallId)) {
            toolInvocationId = agentContext.getAgentRunState().resolveToolInvocationId(toolCallId);
        }
        PlanApprovalResumeContext resumeContext = PlanApprovalResumeContext.from(agentContext, request, entryAgent);
        String visitorId = request == null ? null : request.getVisitorId();

        PlanApprovalRecord record = PlanApprovalRecord.builder()
                .approvalId(approvalId)
                .visitorId(visitorId)
                .sessionId(agentContext.getSessionId())
                .sourceRunId(runId)
                .sourceRequestId(agentContext.getRequestId())
                .toolInvocationId(toolInvocationId)
                .toolCallId(toolCallId)
                .planContent(signal.getPlanContent())
                .planFilePath(signal.getPlanFilePath())
                .status(PlanApprovalStatuses.PENDING)
                .expiresAt(LocalDateTime.now().plusMinutes(DEFAULT_TIMEOUT_MINUTES))
                .resumeContextJson(PlanApprovalResumeContext.toJson(resumeContext))
                .build();

        planApprovalRepository.insert(record);
        if (sessionWorkingMemoryService != null && executor != null) {
            sessionWorkingMemoryService.persistTurn(
                    agentContext.getSessionId(),
                    agentContext.getRequestId(),
                    runId,
                    entryAgent,
                    executor.exportWorkingMemoryDelta()
            );
        }
        if (toolInvocationId != null && agentContext.getExecutionRecorder() != null) {
            agentContext.getExecutionRecorder().finishToolInvocation(ToolInvocationFinishRecord.builder()
                    .toolInvocationId(toolInvocationId)
                    .runId(runId)
                    .requestId(agentContext.getRequestId())
                    .sessionId(agentContext.getSessionId())
                    .toolCallId(toolCallId)
                    .toolName(TaskToolNames.EXIT_PLAN_MODE)
                    .status(ExecutionLedgerConstants.STATUS_WAITING_INPUT)
                    .finishedAt(LocalDateTime.now())
                    .build());
        }
        ExecutionLedgerRunSupport.finishRun(
                agentContext,
                ExecutionLedgerConstants.STATUS_WAITING_INPUT,
                null,
                "WAITING_PLAN_APPROVAL",
                "等待用户批准 ExitPlanMode 计划"
        );

        scheduleCardAfterCommit(agentContext, record);
        log.info("{} yielded PlanApproval approvalId={} toolCallId={}",
                agentContext.getRequestId(), approvalId, toolCallId);
        return record;
    }

    private void scheduleCardAfterCommit(AgentContext agentContext, PlanApprovalRecord record) {
        Runnable send = () -> {
            if (agentContext.getPrinter() == null) {
                return;
            }
            agentContext.getPrinter().send(
                    record.getApprovalId(),
                    "plan_approval",
                    PlanApprovalObservationSupport.toClientPayload(record),
                    false
            );
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
            return;
        }
        send.run();
    }
}
