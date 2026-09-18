package org.wwz.ai.application.agent.askuser;

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
import org.wwz.ai.application.agent.visitor.ConversationSessionOwnershipApplicationService;
import org.wwz.ai.application.agent.visitor.SessionOwnershipDeniedException;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.runtime.askuser.AskUserQuestionObservationSupport;
import org.wwz.ai.domain.agent.runtime.askuser.IUserQuestionRepository;
import org.wwz.ai.domain.agent.runtime.askuser.UserQuestionRecord;
import org.wwz.ai.domain.agent.runtime.askuser.UserQuestionResumeContext;
import org.wwz.ai.domain.agent.runtime.askuser.UserQuestionStatuses;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.cancel.ActiveAgentRunRegistry;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;
import org.wwz.ai.domain.agent.runtime.executor.AgentExecutorSupport;
import org.wwz.ai.domain.agent.runtime.handler.AgentResponseHandler;
import org.wwz.ai.types.agent.config.AgentExecutorNames;
import org.wwz.ai.types.agent.exception.AgentConcurrentRunException;
import org.wwz.ai.types.agent.exception.AgentExecutorBusyException;
import org.wwz.ai.types.agent.visitor.VisitorRequestContext;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * AskUserQuestion resume：claim 后派发 continuation Run B（空 query + tool observation）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AskUserResumeApplicationService {

    private final IUserQuestionRepository userQuestionRepository;
    private final IAgentDispatchService agentDispatchService;
    private final ConversationSessionOwnershipApplicationService conversationSessionOwnershipApplicationService;
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

    /**
     * CAS claim 后提交 continuation run。观察流走 GET session stream。
     */
    public AgentQuerySubmitResult resume(String resumeRequestId) {
        if (StringUtils.isBlank(resumeRequestId)) {
            throw new IllegalArgumentException("resumeRequestId 不能为空");
        }
        String visitorId = VisitorRequestContext.currentVisitorId();
        if (StringUtils.isBlank(visitorId)) {
            throw new IllegalArgumentException("visitorId不能为空");
        }

        UserQuestionRecord record = userQuestionRepository.findByResumeRequestId(resumeRequestId.trim()).orElse(null);
        if (record == null) {
            throw new IllegalArgumentException("resume 记录不存在");
        }
        if (StringUtils.isNotBlank(record.getVisitorId()) && !record.getVisitorId().equals(visitorId)) {
            throw new IllegalArgumentException("无权恢复该问题");
        }

        try {
            conversationSessionOwnershipApplicationService.ensureExistingSessionAccessible(
                    visitorId, record.getSessionId());
        } catch (SessionOwnershipDeniedException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }

        if (UserQuestionStatuses.ANSWERED.equals(record.getStatus())) {
            return accepted(record);
        }
        if (UserQuestionStatuses.RESUMING.equals(record.getStatus())) {
            throw new AgentConcurrentRunException(
                    "续跑已在进行中", record.getResumeRequestId(), record.getSessionId());
        }
        if (!UserQuestionStatuses.RESUME_PENDING.equals(record.getStatus())) {
            throw new IllegalStateException("问题状态不可 resume: " + record.getStatus());
        }

        boolean claimed = userQuestionRepository.casClaimResume(resumeRequestId.trim(), visitorId);
        if (!claimed) {
            throw new AgentConcurrentRunException(
                    "claim 失败或续跑已被认领", record.getResumeRequestId(), record.getSessionId());
        }

        AgentRequest agentRequest = buildContinuationRequest(record, visitorId);
        try {
            activeAgentRunRegistry.begin(
                    agentRequest.getRequestId(), agentRequest.getSessionId(), visitorId);
        } catch (AgentConcurrentRunException e) {
            userQuestionRepository.markStatus(record.getQuestionId(), UserQuestionStatuses.RESUME_PENDING);
            throw e;
        }

        AgentResponseProjectionStream projectingStream =
                new AgentResponseProjectionStream(null, agentRequest, handlerMap, agentSessionEventBus, sessionEventClock)
                        .bindRegistry(sessionProjectionRegistry);
        agentRunLaunchGate.defer(agentRequest.getRequestId(), agentRequest.getSessionId(), () -> {
            try {
                AgentExecutorSupport.execute(dispatchExecutor, "askUserResume", agentRequest.getRequestId(),
                        () -> dispatchContinuation(record, agentRequest, projectingStream));
            } catch (AgentExecutorBusyException e) {
                log.warn("{} deferred ask-user resume busy", agentRequest.getRequestId(), e);
                activeAgentRunRegistry.end(agentRequest.getRequestId());
                userQuestionRepository.markStatus(record.getQuestionId(), UserQuestionStatuses.RESUME_PENDING);
                projectingStream.completeWithError(e);
            }
        });
        return accepted(record);
    }

    private static AgentQuerySubmitResult accepted(UserQuestionRecord record) {
        return AgentQuerySubmitResult.builder()
                .accepted(true)
                .sessionId(record.getSessionId())
                .requestId(record.getResumeRequestId())
                .build();
    }

    private void dispatchContinuation(UserQuestionRecord record,
                                      AgentRequest agentRequest,
                                      AgentResponseProjectionStream projectingStream) {
        try {
            agentDispatchService.dispatch(agentRequest, projectingStream);
            userQuestionRepository.markAnswered(record.getQuestionId());
            GptQueryApplicationService.completeProjectionUnlessBackgroundRunning(
                    agentRequest, projectingStream, activeAgentRunRegistry);
        } catch (Exception e) {
            log.error("{} ask-user resume failed questionId={}",
                    agentRequest.getRequestId(), record.getQuestionId(), e);
            userQuestionRepository.markStatus(record.getQuestionId(), UserQuestionStatuses.FAILED);
            if (projectingStream.isAborted()) {
                projectingStream.complete();
                GptQueryApplicationService.endRunUnlessBackground(agentRequest, activeAgentRunRegistry);
                return;
            }
            projectingStream.completeWithError(e);
            GptQueryApplicationService.endRunUnlessBackground(agentRequest, activeAgentRunRegistry);
        }
    }

    private AgentRequest buildContinuationRequest(UserQuestionRecord record, String visitorId) {
        UserQuestionResumeContext resumeContext = UserQuestionResumeContext.fromJson(record.getResumeContextJson());
        Integer agentType = resumeContext.getAgentType();
        return AgentRequest.builder()
                .requestId(record.getResumeRequestId())
                .sessionId(record.getSessionId())
                .visitorId(visitorId)
                .query("")
                .agentType(agentType)
                .model(resumeContext.getModel())
                .thinking(resumeContext.getThinking())
                .thinkingEffort(resumeContext.getThinkingEffort())
                .isStream(true)
                .resumeQuestionId(record.getQuestionId())
                .resumeContextJson(record.getResumeContextJson())
                .build();
    }

    /**
     * 用真实答案替换 waiting 占位（或补齐缺失的）tool observation，保证 function-call 成对。
     */
    public static List<Message> appendAnswerObservation(List<Message> working,
                                                        UserQuestionRecord record) {
        List<Message> messages = working == null ? new ArrayList<>() : new ArrayList<>(working);
        if (record == null) {
            return messages;
        }
        String toolCallId = AskUserQuestionObservationSupport.resolveAskUserToolCallId(
                messages, record.getToolCallId());
        if (StringUtils.isBlank(toolCallId)) {
            return messages;
        }
        String observation = AskUserQuestionObservationSupport.buildAnswerObservation(
                record.getQuestions(), record.getAnswers(), record.getQuestionId());
        messages.removeIf(message -> message != null
                && message.getRole() == org.wwz.ai.domain.agent.runtime.enums.RoleType.TOOL
                && toolCallId.equals(message.getToolCallId()));
        messages.add(Message.toolMessage(observation, toolCallId, null));
        return messages;
    }
}
