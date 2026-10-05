package org.wwz.ai.application.agent.desktopcontrol;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.wwz.ai.application.agent.dispatch.IAgentDispatchService;
import org.wwz.ai.application.agent.query.AgentQuerySubmitResult;
import org.wwz.ai.application.agent.query.GptQueryApplicationService;
import org.wwz.ai.application.agent.run.AgentRunLaunchGate;
import org.wwz.ai.application.agent.stream.AgentResponseProjectionStream;
import org.wwz.ai.application.agent.stream.AgentSessionEventBus;
import org.wwz.ai.application.agent.stream.SessionEventClock;
import org.wwz.ai.application.agent.stream.SessionProjectionRegistry;
import org.wwz.ai.application.agent.authorization.ConversationSessionAuthorizationService;
import org.wwz.ai.application.agent.authorization.SessionOwnershipDeniedException;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.runtime.askuser.UserQuestionResumeContext;
import org.wwz.ai.domain.agent.runtime.cancel.ActiveAgentRunRegistry;
import org.wwz.ai.domain.agent.runtime.desktopcontrol.DesktopControlObservationSupport;
import org.wwz.ai.domain.agent.runtime.desktopcontrol.DesktopControlRecord;
import org.wwz.ai.domain.agent.runtime.desktopcontrol.DesktopControlStatuses;
import org.wwz.ai.domain.agent.runtime.desktopcontrol.IDesktopControlRepository;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;
import org.wwz.ai.domain.agent.runtime.executor.AgentExecutorSupport;
import org.wwz.ai.domain.agent.runtime.handler.AgentResponseHandler;
import org.wwz.ai.types.agent.config.AgentExecutorNames;
import org.wwz.ai.types.agent.exception.AgentConcurrentRunException;
import org.wwz.ai.types.agent.exception.AgentExecutorBusyException;
import org.wwz.ai.types.agent.user.UserRequestContext;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class DesktopControlResumeApplicationService {

    private final IDesktopControlRepository desktopControlRepository;
    private final IAgentDispatchService agentDispatchService;
    private final ConversationSessionAuthorizationService conversationSessionAuthorizationService;
    private final ActiveAgentRunRegistry activeAgentRunRegistry;
    private final AgentSessionEventBus agentSessionEventBus;
    private final AgentRunLaunchGate agentRunLaunchGate;
    private final SessionEventClock sessionEventClock;
    private final SessionProjectionRegistry sessionProjectionRegistry;

    @Resource
    private Map<AgentType, AgentResponseHandler> handlerMap;

    @Resource
    @Qualifier(AgentExecutorNames.DISPATCH_EXECUTOR)
    private Executor dispatchExecutor;

    public AgentQuerySubmitResult resume(String resumeRequestId) {
        if (StringUtils.isBlank(resumeRequestId)) {
            throw new IllegalArgumentException("resumeRequestId 不能为空");
        }
        String userId = UserRequestContext.currentUserId();
        if (StringUtils.isBlank(userId)) {
            throw new IllegalArgumentException("userId不能为空");
        }

        DesktopControlRecord record = desktopControlRepository.findByResumeRequestId(resumeRequestId.trim()).orElse(null);
        if (record == null) {
            throw new IllegalArgumentException("resume 记录不存在");
        }
        if (StringUtils.isNotBlank(record.getUserId()) && !record.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权恢复该桌面控制");
        }

        try {
            conversationSessionAuthorizationService.ensureExistingSessionAccessible(
                    userId, record.getSessionId());
        } catch (SessionOwnershipDeniedException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }

        if (DesktopControlStatuses.COMPLETED.equals(record.getStatus())) {
            return accepted(record);
        }
        if (DesktopControlStatuses.RESUMING.equals(record.getStatus())) {
            throw new AgentConcurrentRunException(
                    "续跑已在进行中", record.getResumeRequestId(), record.getSessionId());
        }
        if (!DesktopControlStatuses.RESUME_PENDING.equals(record.getStatus())) {
            throw new IllegalStateException("桌面控制状态不可 resume: " + record.getStatus());
        }

        boolean claimed = desktopControlRepository.casClaimResume(resumeRequestId.trim(), userId);
        if (!claimed) {
            throw new AgentConcurrentRunException(
                    "claim 失败或续跑已被认领", record.getResumeRequestId(), record.getSessionId());
        }

        AgentRequest agentRequest = buildContinuationRequest(record, userId);
        try {
            activeAgentRunRegistry.begin(
                    agentRequest.getRequestId(), agentRequest.getSessionId(), userId);
        } catch (AgentConcurrentRunException e) {
            desktopControlRepository.markStatus(record.getControlId(), DesktopControlStatuses.RESUME_PENDING);
            throw e;
        }

        AgentResponseProjectionStream projectingStream =
                new AgentResponseProjectionStream(null, agentRequest, handlerMap, agentSessionEventBus, sessionEventClock)
                        .bindRegistry(sessionProjectionRegistry);
        agentRunLaunchGate.defer(agentRequest.getRequestId(), agentRequest.getSessionId(), () -> {
            try {
                AgentExecutorSupport.execute(dispatchExecutor, "desktopControlResume", agentRequest.getRequestId(),
                        () -> dispatchContinuation(record, agentRequest, projectingStream));
            } catch (AgentExecutorBusyException e) {
                log.warn("{} deferred desktop-control resume busy", agentRequest.getRequestId(), e);
                activeAgentRunRegistry.end(agentRequest.getRequestId());
                desktopControlRepository.markStatus(record.getControlId(), DesktopControlStatuses.RESUME_PENDING);
                projectingStream.completeWithError(e);
            }
        });
        return accepted(record);
    }

    private static AgentQuerySubmitResult accepted(DesktopControlRecord record) {
        return AgentQuerySubmitResult.builder()
                .accepted(true)
                .sessionId(record.getSessionId())
                .requestId(record.getResumeRequestId())
                .build();
    }

    private void dispatchContinuation(DesktopControlRecord record,
                                      AgentRequest agentRequest,
                                      AgentResponseProjectionStream projectingStream) {
        try {
            agentDispatchService.dispatch(agentRequest, projectingStream);
            desktopControlRepository.markCompleted(record.getControlId());
            GptQueryApplicationService.completeProjectionUnlessBackgroundRunning(
                    agentRequest, projectingStream, activeAgentRunRegistry);
        } catch (Exception e) {
            log.error("{} desktop-control resume failed controlId={}",
                    agentRequest.getRequestId(), record.getControlId(), e);
            desktopControlRepository.markStatus(record.getControlId(), DesktopControlStatuses.FAILED);
            if (projectingStream.isAborted()) {
                projectingStream.complete();
                GptQueryApplicationService.endRunUnlessBackground(agentRequest, activeAgentRunRegistry);
                return;
            }
            projectingStream.completeWithError(e);
            GptQueryApplicationService.endRunUnlessBackground(agentRequest, activeAgentRunRegistry);
        }
    }

    private AgentRequest buildContinuationRequest(DesktopControlRecord record, String userId) {
        UserQuestionResumeContext resumeContext = UserQuestionResumeContext.fromJson(record.getResumeContextJson());
        Integer agentType = resumeContext.getAgentType();
        return AgentRequest.builder()
                .requestId(record.getResumeRequestId())
                .sessionId(record.getSessionId())
                .userId(userId)
                .query("")
                .agentType(agentType)
                .model(resumeContext.getModel())
                .thinking(resumeContext.getThinking())
                .thinkingEffort(resumeContext.getThinkingEffort())
                .isStream(true)
                .resumeDesktopControlId(record.getControlId())
                .resumeContextJson(record.getResumeContextJson())
                .build();
    }

    public static List<Message> appendCompletedObservation(List<Message> working,
                                                           DesktopControlRecord record) {
        List<Message> messages = working == null ? new ArrayList<>() : new ArrayList<>(working);
        if (record == null) {
            return messages;
        }
        String toolCallId = DesktopControlObservationSupport.resolveDesktopToolCallId(
                messages, record.getToolCallId());
        if (StringUtils.isBlank(toolCallId)) {
            return messages;
        }
        String observation = DesktopControlObservationSupport.buildCompletedObservation(
                record.getReason(), record.getControlId());
        messages.removeIf(message -> message != null
                && message.getRole() == org.wwz.ai.domain.agent.runtime.enums.RoleType.TOOL
                && toolCallId.equals(message.getToolCallId()));
        messages.add(Message.toolMessage(observation, toolCallId, null));
        return messages;
    }
}
