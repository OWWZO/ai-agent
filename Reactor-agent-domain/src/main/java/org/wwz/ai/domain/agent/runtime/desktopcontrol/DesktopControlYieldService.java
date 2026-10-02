package org.wwz.ai.domain.agent.runtime.desktopcontrol;

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
import org.wwz.ai.domain.agent.memory.ltm.LtmOwner;
import org.wwz.ai.domain.agent.memory.ltm.LtmOwnerResolver;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.ReActAgent;
import org.wwz.ai.domain.agent.runtime.askuser.UserQuestionResumeContext;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.RequestDesktopControlTool;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DesktopControlYieldService {

    private final IDesktopControlRepository desktopControlRepository;
    private final SessionWorkingMemoryService sessionWorkingMemoryService;

    @Transactional(rollbackFor = Exception.class)
    public DesktopControlRecord yieldAndNotify(AgentContext agentContext,
                                               ReActAgent executor,
                                               AgentRequest request,
                                               DesktopControlRequiredException signal,
                                               String entryAgent) {
        if (agentContext == null || signal == null) {
            throw new IllegalStateException("yield requires agentContext and signal");
        }
        String controlId = "dc_" + UUID.randomUUID().toString().replace("-", "");
        Long runId = agentContext.getAgentRunState() == null ? null : agentContext.getAgentRunState().getRunId();
        String toolCallId = DesktopControlObservationSupport.resolveDesktopToolCallId(
                executor == null || executor.getMemory() == null ? null : executor.getMemory().getMessages(),
                signal.getToolCallId());
        if (StringUtils.isNotBlank(toolCallId)
                && executor != null
                && executor.getMemory() != null
                && !DesktopControlObservationSupport.hasToolResult(executor.getMemory().getMessages(), toolCallId)) {
            executor.getMemory().addMessage(org.wwz.ai.domain.agent.runtime.dto.Message.toolMessage(
                    DesktopControlObservationSupport.buildWaitingObservation(signal.getReason(), controlId),
                    toolCallId,
                    null));
        }
        Long toolInvocationId = null;
        if (agentContext.getAgentRunState() != null && StringUtils.isNotBlank(toolCallId)) {
            toolInvocationId = agentContext.getAgentRunState().resolveToolInvocationId(toolCallId);
        }
        UserQuestionResumeContext resumeContext = UserQuestionResumeContext.from(agentContext, request, entryAgent);
        String visitorId = request == null ? null : request.getVisitorId();
        LtmOwner owner = agentContext.getLtmOwner();
        if (owner == null) {
            owner = LtmOwnerResolver.resolve(visitorId, null);
        }

        DesktopControlRecord record = DesktopControlRecord.builder()
                .controlId(controlId)
                .visitorId(visitorId)
                .sessionId(agentContext.getSessionId())
                .ownerKey(owner.asOwnerKey())
                .sourceRunId(runId)
                .sourceRequestId(agentContext.getRequestId())
                .toolInvocationId(toolInvocationId)
                .toolCallId(toolCallId)
                .reason(signal.getReason())
                .streamUrl(signal.getStreamUrl())
                .holdUntil(signal.getHoldUntil())
                .status(DesktopControlStatuses.PENDING)
                .resumeContextJson(UserQuestionResumeContext.toJson(resumeContext))
                .build();

        desktopControlRepository.insert(record);
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
                    .toolName(RequestDesktopControlTool.NAME)
                    .status(ExecutionLedgerConstants.STATUS_WAITING_INPUT)
                    .llmObservation(DesktopControlObservationSupport.buildWaitingObservation(
                            signal.getReason(), controlId))
                    .finishedAt(LocalDateTime.now())
                    .build());
        }
        ExecutionLedgerRunSupport.finishRun(
                agentContext,
                ExecutionLedgerConstants.STATUS_WAITING_INPUT,
                null,
                "WAITING_USER_INPUT",
                "等待用户完成桌面操作"
        );

        scheduleCardAfterCommit(agentContext, record);
        log.info("{} yielded RequestDesktopControl controlId={} toolCallId={}",
                agentContext.getRequestId(), controlId, toolCallId);
        return record;
    }

    private void scheduleCardAfterCommit(AgentContext agentContext, DesktopControlRecord record) {
        Runnable send = () -> {
            if (agentContext.getPrinter() == null) {
                return;
            }
            agentContext.getPrinter().send(
                    record.getControlId(),
                    "desktop_control",
                    DesktopControlObservationSupport.toClientPayload(record),
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
