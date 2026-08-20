package org.wwz.ai.domain.agent.runtime.askuser;

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
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.AskUserQuestionTool;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * AskUserQuestion 让步收口：事务内写 question + working_memory + WAITING_INPUT，提交后再发卡片。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserQuestionYieldService {

    private static final long DEFAULT_TIMEOUT_MINUTES = 30L;

    private final IUserQuestionRepository userQuestionRepository;
    private final SessionWorkingMemoryService sessionWorkingMemoryService;

    @Transactional(rollbackFor = Exception.class)
    public UserQuestionRecord yieldAndNotify(AgentContext agentContext,
                                             ReActAgent executor,
                                             AgentRequest request,
                                             UserInputRequiredException signal,
                                             String entryAgent) {
        if (agentContext == null || signal == null) {
            throw new IllegalStateException("yield requires agentContext and signal");
        }
        String questionId = "uq_" + UUID.randomUUID().toString().replace("-", "");
        Long runId = agentContext.getAgentRunState() == null ? null : agentContext.getAgentRunState().getRunId();
        // yield 时 memory 只有 assistant.tool_calls、尚无 tool result；先补 waiting 占位，避免网关 400。
        String toolCallId = AskUserQuestionObservationSupport.resolveAskUserToolCallId(
                executor == null || executor.getMemory() == null ? null : executor.getMemory().getMessages(),
                signal.getToolCallId());
        if (StringUtils.isNotBlank(toolCallId)
                && executor != null
                && executor.getMemory() != null
                && !AskUserQuestionObservationSupport.hasToolResult(executor.getMemory().getMessages(), toolCallId)) {
            executor.getMemory().addMessage(org.wwz.ai.domain.agent.runtime.dto.Message.toolMessage(
                    AskUserQuestionObservationSupport.buildWaitingObservation(signal.getQuestions(), questionId),
                    toolCallId,
                    null));
        }
        Long toolInvocationId = null;
        if (agentContext.getAgentRunState() != null && StringUtils.isNotBlank(toolCallId)) {
            toolInvocationId = agentContext.getAgentRunState().resolveToolInvocationId(toolCallId);
        }
        UserQuestionResumeContext resumeContext = UserQuestionResumeContext.from(agentContext, request, entryAgent);
        String visitorId = request == null ? null : request.getVisitorId();

        UserQuestionRecord record = UserQuestionRecord.builder()
                .questionId(questionId)
                .visitorId(visitorId)
                .sessionId(agentContext.getSessionId())
                .sourceRunId(runId)
                .sourceRequestId(agentContext.getRequestId())
                .toolInvocationId(toolInvocationId)
                .toolCallId(toolCallId)
                .questions(signal.getQuestions())
                .status(UserQuestionStatuses.PENDING)
                .expiresAt(LocalDateTime.now().plusMinutes(DEFAULT_TIMEOUT_MINUTES))
                .resumeContextJson(UserQuestionResumeContext.toJson(resumeContext))
                .build();

        userQuestionRepository.insert(record);
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
                    .toolName(AskUserQuestionTool.NAME)
                    .status(ExecutionLedgerConstants.STATUS_WAITING_INPUT)
                    .finishedAt(LocalDateTime.now())
                    .build());
        }
        ExecutionLedgerRunSupport.finishRun(
                agentContext,
                ExecutionLedgerConstants.STATUS_WAITING_INPUT,
                null,
                "WAITING_USER_INPUT",
                "等待用户回答 AskUserQuestion"
        );

        scheduleAskCardAfterCommit(agentContext, record);
        log.info("{} yielded AskUserQuestion questionId={} toolCallId={}",
                agentContext.getRequestId(), questionId, toolCallId);
        return record;
    }

    private void scheduleAskCardAfterCommit(AgentContext agentContext, UserQuestionRecord record) {
        Runnable send = () -> {
            if (agentContext.getPrinter() == null) {
                return;
            }
            agentContext.getPrinter().send(
                    record.getQuestionId(),
                    "ask_user_question",
                    AskUserQuestionObservationSupport.toClientPayload(record),
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
