package org.wwz.ai.application.agent.askuser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.wwz.ai.application.agent.dispatch.IAgentDispatchService;
import org.wwz.ai.application.agent.stream.AgentResponseProjectionStream;
import org.wwz.ai.application.agent.stream.AgentSessionStream;
import org.wwz.ai.application.agent.visitor.ConversationSessionOwnershipApplicationService;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.runtime.askuser.AskUserQuestionObservationSupport;
import org.wwz.ai.domain.agent.runtime.askuser.IUserQuestionRepository;
import org.wwz.ai.domain.agent.runtime.askuser.UserQuestionRecord;
import org.wwz.ai.domain.agent.runtime.askuser.UserQuestionResumeContext;
import org.wwz.ai.domain.agent.runtime.askuser.UserQuestionStatuses;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;
import org.wwz.ai.domain.agent.runtime.executor.AgentExecutorSupport;
import org.wwz.ai.domain.agent.runtime.handler.AgentResponseHandler;
import org.wwz.ai.types.agent.config.AgentExecutorNames;
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

    @Resource
    private Map<AgentType, AgentResponseHandler> handlerMap;

    @Resource
    @Qualifier(AgentExecutorNames.DISPATCH_EXECUTOR)
    private Executor dispatchExecutor;

    /**
     * @return true 已 claim 并进入派发；false 已向 stream 结束（错误或无需续跑）
     */
    public boolean resume(String resumeRequestId, AgentSessionStream stream) {
        if (stream == null) {
            return false;
        }
        if (StringUtils.isBlank(resumeRequestId)) {
            stream.completeWithError(new IllegalArgumentException("resumeRequestId 不能为空"));
            return false;
        }
        String visitorId = VisitorRequestContext.currentVisitorId();
        if (StringUtils.isBlank(visitorId)) {
            stream.completeWithError(new IllegalArgumentException("visitorId不能为空"));
            return false;
        }

        UserQuestionRecord record = userQuestionRepository.findByResumeRequestId(resumeRequestId.trim()).orElse(null);
        if (record == null) {
            stream.completeWithError(new IllegalArgumentException("resume 记录不存在"));
            return false;
        }
        if (StringUtils.isNotBlank(record.getVisitorId()) && !record.getVisitorId().equals(visitorId)) {
            stream.completeWithError(new IllegalArgumentException("无权恢复该问题"));
            return false;
        }

        try {
            conversationSessionOwnershipApplicationService.ensureExistingSessionAccessible(
                    visitorId, record.getSessionId());
        } catch (Exception e) {
            stream.completeWithError(e);
            return false;
        }

        if (UserQuestionStatuses.ANSWERED.equals(record.getStatus())) {
            stream.complete();
            return false;
        }
        if (UserQuestionStatuses.RESUMING.equals(record.getStatus())) {
            stream.completeWithError(new IllegalStateException("续跑已在进行中"));
            return false;
        }
        if (!UserQuestionStatuses.RESUME_PENDING.equals(record.getStatus())) {
            stream.completeWithError(new IllegalStateException("问题状态不可 resume: " + record.getStatus()));
            return false;
        }

        boolean claimed = userQuestionRepository.casClaimResume(resumeRequestId.trim(), visitorId);
        if (!claimed) {
            stream.completeWithError(new IllegalStateException("claim 失败或续跑已被认领"));
            return false;
        }

        AgentRequest agentRequest = buildContinuationRequest(record, visitorId);
        AgentResponseProjectionStream projectingStream =
                new AgentResponseProjectionStream(stream, agentRequest, handlerMap);
        try {
            AgentExecutorSupport.execute(dispatchExecutor, "askUserResume", agentRequest.getRequestId(),
                    () -> dispatchContinuation(record, agentRequest, projectingStream));
            return true;
        } catch (AgentExecutorBusyException e) {
            userQuestionRepository.markStatus(record.getQuestionId(), UserQuestionStatuses.RESUME_PENDING);
            stream.completeWithError(e);
            return false;
        }
    }

    private void dispatchContinuation(UserQuestionRecord record,
                                      AgentRequest agentRequest,
                                      AgentResponseProjectionStream projectingStream) {
        try {
            agentDispatchService.dispatch(agentRequest, projectingStream);
            userQuestionRepository.markAnswered(record.getQuestionId());
            projectingStream.complete();
        } catch (Exception e) {
            log.error("{} ask-user resume failed questionId={}",
                    agentRequest.getRequestId(), record.getQuestionId(), e);
            userQuestionRepository.markStatus(record.getQuestionId(), UserQuestionStatuses.FAILED);
            if (projectingStream.isAborted()) {
                projectingStream.complete();
                return;
            }
            projectingStream.completeWithError(e);
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
                .aiAgentId(resumeContext.getAiAgentId())
                .agentType(agentType)
                .model(resumeContext.getModel())
                .thinking(resumeContext.getThinking())
                .thinkingEffort(resumeContext.getThinkingEffort())
                .outputStyle(resumeContext.getOutputStyle())
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
