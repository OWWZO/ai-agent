package org.wwz.ai.application.agent.query;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.wwz.ai.application.agent.askuser.AskUserQuestionApplicationService;
import org.wwz.ai.application.agent.planmode.PlanApprovalApplicationService;
import org.wwz.ai.application.agent.dispatch.IAgentDispatchService;
import org.wwz.ai.application.agent.run.AgentRunLaunchGate;
import org.wwz.ai.application.agent.stream.AgentResponseProjectionStream;
import org.wwz.ai.application.agent.stream.AgentSessionEventBus;
import org.wwz.ai.application.agent.stream.SessionEventClock;
import org.wwz.ai.application.agent.stream.SessionProjectionRegistry;
import org.wwz.ai.application.agent.visitor.ConversationSessionOwnershipApplicationService;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.reactor.model.req.GptQueryReq;
import org.wwz.ai.domain.agent.runtime.GptQueryAgentRequestFactory;
import org.wwz.ai.domain.agent.runtime.cancel.ActiveAgentRunRegistry;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;
import org.wwz.ai.domain.agent.runtime.executor.AgentExecutorSupport;
import org.wwz.ai.domain.agent.runtime.handler.AgentResponseHandler;
import org.wwz.ai.domain.agent.runtime.tasklist.SessionBackgroundTaskHub;
import org.wwz.ai.types.agent.config.AgentExecutorNames;
import org.wwz.ai.types.agent.exception.AgentExecutorBusyException;
import org.wwz.ai.types.agent.visitor.VisitorRequestContext;

import javax.annotation.Resource;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * GPT 查询应用服务。
 * POST 只提交 run；SSE 由 session Hub 旁观。
 */
@Slf4j
@Service
public class GptQueryApplicationService implements IGptQueryApplicationService {

    @Resource
    private GptQueryAgentRequestFactory gptQueryAgentRequestFactory;

    @Resource
    private IAgentDispatchService agentDispatchService;

    @Resource
    private ConversationSessionOwnershipApplicationService conversationSessionOwnershipApplicationService;

    @Resource
    private AskUserQuestionApplicationService askUserQuestionApplicationService;

    @Resource
    private PlanApprovalApplicationService planApprovalApplicationService;

    @Resource
    private Map<AgentType, AgentResponseHandler> handlerMap;

    @Resource
    @Qualifier(AgentExecutorNames.DISPATCH_EXECUTOR)
    private Executor dispatchExecutor;

    @Resource
    private ActiveAgentRunRegistry activeAgentRunRegistry;

    @Resource
    private AgentSessionEventBus agentSessionEventBus;

    @Resource
    private AgentRunLaunchGate agentRunLaunchGate;

    @Resource
    private SessionEventClock sessionEventClock;

    @Resource
    private SessionProjectionRegistry sessionProjectionRegistry;

    @Override
    public AgentQuerySubmitResult submitAgentQuery(GptQueryReq params) {
        gptQueryAgentRequestFactory.normalize(params);
        AgentRequest agentRequest = gptQueryAgentRequestFactory.build(params);
        log.info("{} start handle Agent request: {}", params.getRequestId(), JSON.toJSONString(agentRequest));

        String visitorId = resolveVisitorId(agentRequest);
        agentRequest.setVisitorId(visitorId);
        conversationSessionOwnershipApplicationService.ensureSessionAccessible(
                visitorId,
                agentRequest.getSessionId(),
                agentRequest.getQuery()
        );
        if (StringUtils.isBlank(agentRequest.getResumeQuestionId())
                && StringUtils.isBlank(agentRequest.getResumeApprovalId())
                && askUserQuestionApplicationService != null
                && askUserQuestionApplicationService.hasOpenQuestion(agentRequest.getSessionId())) {
            throw new IllegalStateException("当前会话有待回答的问题，请先回答或取消后再发送新消息");
        }
        if (StringUtils.isBlank(agentRequest.getResumeQuestionId())
                && StringUtils.isBlank(agentRequest.getResumeApprovalId())
                && planApprovalApplicationService != null
                && planApprovalApplicationService.hasOpenApproval(agentRequest.getSessionId())) {
            throw new IllegalStateException("当前会话有待批准的计划，请先批准/拒绝或取消后再发送新消息");
        }

        activeAgentRunRegistry.begin(
                agentRequest.getRequestId(),
                agentRequest.getSessionId(),
                visitorId);

        AgentResponseProjectionStream projectingStream =
                new AgentResponseProjectionStream(null, agentRequest, handlerMap, agentSessionEventBus, sessionEventClock)
                        .bindRegistry(sessionProjectionRegistry);
        agentRunLaunchGate.defer(agentRequest.getRequestId(), agentRequest.getSessionId(), () -> {
            try {
                AgentExecutorSupport.execute(dispatchExecutor, "dispatch", agentRequest.getRequestId(),
                        () -> dispatchOnExecutor(params, agentRequest, projectingStream));
            } catch (AgentExecutorBusyException e) {
                log.warn("{} deferred dispatch busy", agentRequest.getRequestId(), e);
                activeAgentRunRegistry.end(agentRequest.getRequestId());
                projectingStream.completeWithError(e);
            }
        });

        return AgentQuerySubmitResult.builder()
                .accepted(true)
                .sessionId(agentRequest.getSessionId())
                .requestId(agentRequest.getRequestId())
                .build();
    }

    private void dispatchOnExecutor(GptQueryReq params,
                                    AgentRequest agentRequest,
                                    AgentResponseProjectionStream projectingStream) {
        try {
            agentDispatchService.dispatch(agentRequest, projectingStream);
            completeProjectionUnlessBackgroundRunning(agentRequest, projectingStream, activeAgentRunRegistry);
        } catch (Exception e) {
            if (projectingStream.isAborted()) {
                log.info("{} dispatch error occurred after projection closed", agentRequest.getRequestId());
                if (!shouldDeferProjectionComplete(agentRequest)) {
                    projectingStream.complete();
                }
                endRunUnlessBackground(agentRequest, activeAgentRunRegistry);
                return;
            }
            log.error("{} direct dispatch error", agentRequest.getRequestId(), e);
            projectingStream.completeWithError(e);
            endRunUnlessBackground(agentRequest, activeAgentRunRegistry);
        } finally {
            log.info("{}, agent.query.web.singleRequest end, requestId: {}",
                    params.getRequestId(), JSON.toJSONString(params));
        }
    }

    private String resolveVisitorId(AgentRequest request) {
        String contextVisitorId = VisitorRequestContext.currentVisitorId();
        String visitorId = StringUtils.defaultIfBlank(contextVisitorId, request == null ? null : request.getVisitorId());
        if (StringUtils.isBlank(visitorId)) {
            throw new IllegalArgumentException("visitorId不能为空");
        }
        return visitorId;
    }

    public static void completeProjectionUnlessBackgroundRunning(AgentRequest agentRequest,
                                                                 AgentResponseProjectionStream projectingStream) {
        completeProjectionUnlessBackgroundRunning(agentRequest, projectingStream, null);
    }

    public static void completeProjectionUnlessBackgroundRunning(AgentRequest agentRequest,
                                                                 AgentResponseProjectionStream projectingStream,
                                                                 ActiveAgentRunRegistry runRegistry) {
        if (shouldDeferProjectionComplete(agentRequest)) {
            log.info("{} defer projection complete: background tasks still running sessionId={}",
                    agentRequest == null ? "-" : agentRequest.getRequestId(),
                    agentRequest == null ? null : agentRequest.getSessionId());
            endOccupancy(agentRequest, runRegistry);
            return;
        }
        if (projectingStream != null) {
            projectingStream.complete();
        }
        endOccupancy(agentRequest, runRegistry);
    }

    public static boolean shouldDeferProjectionComplete(AgentRequest agentRequest) {
        if (agentRequest == null) {
            return false;
        }
        return SessionBackgroundTaskHub.hasRunning(
                SessionBackgroundTaskHub.keyFor(agentRequest.getSessionId(), agentRequest.getRequestId()));
    }

    /**
     * 父循环结束后立即释放占用槽，即使后台子 Agent 还在跑。
     */
    public static void endRunUnlessBackground(AgentRequest agentRequest, ActiveAgentRunRegistry runRegistry) {
        endOccupancy(agentRequest, runRegistry);
    }

    public static void endOccupancy(AgentRequest agentRequest, ActiveAgentRunRegistry runRegistry) {
        if (runRegistry == null || agentRequest == null || StringUtils.isBlank(agentRequest.getRequestId())) {
            return;
        }
        runRegistry.end(agentRequest.getRequestId());
    }
}
