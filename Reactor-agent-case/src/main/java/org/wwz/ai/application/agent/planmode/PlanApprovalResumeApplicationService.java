package org.wwz.ai.application.agent.planmode;

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
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;
import org.wwz.ai.domain.agent.runtime.executor.AgentExecutorSupport;
import org.wwz.ai.domain.agent.runtime.handler.AgentResponseHandler;
import org.wwz.ai.domain.agent.runtime.planmode.IPlanApprovalRepository;
import org.wwz.ai.domain.agent.runtime.planmode.PlanApprovalObservationSupport;
import org.wwz.ai.domain.agent.runtime.planmode.PlanApprovalRecord;
import org.wwz.ai.domain.agent.runtime.planmode.PlanApprovalResumeContext;
import org.wwz.ai.domain.agent.runtime.planmode.PlanApprovalStatuses;
import org.wwz.ai.types.agent.config.AgentExecutorNames;
import org.wwz.ai.types.agent.exception.AgentExecutorBusyException;
import org.wwz.ai.types.agent.visitor.VisitorRequestContext;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * ExitPlanMode resume：claim 后派发 continuation Run B（空 query + tool observation）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanApprovalResumeApplicationService {

    private final IPlanApprovalRepository planApprovalRepository;
    private final IAgentDispatchService agentDispatchService;
    private final ConversationSessionOwnershipApplicationService conversationSessionOwnershipApplicationService;

    @Resource
    private Map<AgentType, AgentResponseHandler> handlerMap;

    @Resource
    @Qualifier(AgentExecutorNames.DISPATCH_EXECUTOR)
    private Executor dispatchExecutor;

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

        PlanApprovalRecord record = planApprovalRepository.findByResumeRequestId(resumeRequestId.trim()).orElse(null);
        if (record == null) {
            stream.completeWithError(new IllegalArgumentException("resume 记录不存在"));
            return false;
        }
        if (StringUtils.isNotBlank(record.getVisitorId()) && !record.getVisitorId().equals(visitorId)) {
            stream.completeWithError(new IllegalArgumentException("无权恢复该审批"));
            return false;
        }

        try {
            conversationSessionOwnershipApplicationService.ensureExistingSessionAccessible(
                    visitorId, record.getSessionId());
        } catch (Exception e) {
            stream.completeWithError(e);
            return false;
        }

        if (PlanApprovalStatuses.ANSWERED.equals(record.getStatus())) {
            stream.complete();
            return false;
        }
        if (PlanApprovalStatuses.RESUMING.equals(record.getStatus())) {
            stream.completeWithError(new IllegalStateException("续跑已在进行中"));
            return false;
        }
        if (!PlanApprovalStatuses.RESUME_PENDING.equals(record.getStatus())) {
            stream.completeWithError(new IllegalStateException("审批状态不可 resume: " + record.getStatus()));
            return false;
        }

        boolean claimed = planApprovalRepository.casClaimResume(resumeRequestId.trim(), visitorId);
        if (!claimed) {
            stream.completeWithError(new IllegalStateException("claim 失败或续跑已被认领"));
            return false;
        }

        AgentRequest agentRequest = buildContinuationRequest(record, visitorId);
        AgentResponseProjectionStream projectingStream =
                new AgentResponseProjectionStream(stream, agentRequest, handlerMap);
        try {
            AgentExecutorSupport.execute(dispatchExecutor, "planApprovalResume", agentRequest.getRequestId(),
                    () -> dispatchContinuation(record, agentRequest, projectingStream));
            return true;
        } catch (AgentExecutorBusyException e) {
            planApprovalRepository.markStatus(record.getApprovalId(), PlanApprovalStatuses.RESUME_PENDING);
            stream.completeWithError(e);
            return false;
        }
    }

    private void dispatchContinuation(PlanApprovalRecord record,
                                      AgentRequest agentRequest,
                                      AgentResponseProjectionStream projectingStream) {
        try {
            agentDispatchService.dispatch(agentRequest, projectingStream);
            planApprovalRepository.markAnswered(record.getApprovalId());
            projectingStream.complete();
        } catch (Exception e) {
            log.error("{} plan-approval resume failed approvalId={}",
                    agentRequest.getRequestId(), record.getApprovalId(), e);
            planApprovalRepository.markStatus(record.getApprovalId(), PlanApprovalStatuses.FAILED);
            if (projectingStream.isAborted()) {
                projectingStream.complete();
                return;
            }
            projectingStream.completeWithError(e);
        }
    }

    private AgentRequest buildContinuationRequest(PlanApprovalRecord record, String visitorId) {
        PlanApprovalResumeContext resumeContext = PlanApprovalResumeContext.fromJson(record.getResumeContextJson());
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
                .resumeApprovalId(record.getApprovalId())
                .resumeContextJson(record.getResumeContextJson())
                .build();
    }

    public static List<Message> appendDecisionObservation(List<Message> working,
                                                          PlanApprovalRecord record) {
        List<Message> messages = working == null ? new ArrayList<>() : new ArrayList<>(working);
        if (record == null) {
            return messages;
        }
        String toolCallId = PlanApprovalObservationSupport.resolveExitPlanToolCallId(
                messages, record.getToolCallId());
        if (StringUtils.isBlank(toolCallId)) {
            return messages;
        }
        String observation = PlanApprovalObservationSupport.buildDecisionObservation(record);
        messages.removeIf(message -> message != null
                && message.getRole() == org.wwz.ai.domain.agent.runtime.enums.RoleType.TOOL
                && toolCallId.equals(message.getToolCallId()));
        messages.add(Message.toolMessage(observation, toolCallId, null));
        return messages;
    }
}
