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
import org.wwz.ai.application.agent.stream.AgentStreamProjection;
import org.wwz.ai.application.agent.stream.AgentSessionEventBus;
import org.wwz.ai.application.agent.stream.SessionEventClock;
import org.wwz.ai.application.agent.stream.SessionProjectionRegistry;
import org.wwz.ai.application.agent.authorization.ConversationSessionAuthorizationService;
import org.wwz.ai.application.agent.query.mapper.AgentExecutionCommandMapper;
import org.wwz.ai.domain.agent.runtime.command.AgentExecutionCommand;
import org.wwz.ai.domain.agent.runtime.cancel.ActiveAgentRunRegistry;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;
import org.wwz.ai.domain.agent.runtime.executor.AgentExecutorSupport;
import org.wwz.ai.domain.agent.runtime.handler.AgentStreamEventHandler;
import org.wwz.ai.domain.agent.runtime.llm.LlmModelCatalog;
import org.wwz.ai.domain.agent.runtime.tasklist.SessionBackgroundTaskHub;
import org.wwz.ai.types.agent.config.AgentExecutorNames;
import org.wwz.ai.types.agent.exception.AgentExecutorBusyException;
import org.wwz.ai.types.agent.user.UserRequestContext;

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
    private AgentExecutionCommandMapper agentExecutionCommandMapper;

    @Resource
    private LlmModelCatalog llmModelCatalog;

    @Resource
    private IAgentDispatchService agentDispatchService;

    @Resource
    private ConversationSessionAuthorizationService conversationSessionAuthorizationService;

    @Resource
    private AskUserQuestionApplicationService askUserQuestionApplicationService;

    @Resource
    private PlanApprovalApplicationService planApprovalApplicationService;

    @Resource
    private Map<AgentType, AgentStreamEventHandler> handlerMap;

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
    public AgentQuerySubmitResult submitAgentQuery(GptQueryCommand params) {
        AgentExecutionCommand agentRequest = agentExecutionCommandMapper.toExecutionCommand(
                params, UserRequestContext.currentUserId());
        validateRequestedModel(agentRequest);
        log.info("{} start handle Agent request: {}", params.getRequestId(), JSON.toJSONString(agentRequest));

        String userId = resolveUserId(agentRequest);
        agentRequest.setUserId(userId);
        conversationSessionAuthorizationService.ensureSessionAccessible(
                userId,
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
                userId);

        AgentStreamProjection projectingStream =
                new AgentStreamProjection(null, agentRequest, handlerMap, agentSessionEventBus, sessionEventClock)
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

    /**
     * 显式模型必须来自登录用户可见目录；空值/default 继续交给运行时选择默认模型。
     * <p>该校验必须发生在 begin 前，避免非法模型生成无法执行的 run。</p>
     */
    private void validateRequestedModel(AgentExecutionCommand agentRequest) {
        String model = agentRequest == null ? null : StringUtils.trimToNull(agentRequest.getModel());
        if (model == null || LlmModelCatalog.DEFAULT_MODEL.equalsIgnoreCase(model)) {
            return;
        }
        if (llmModelCatalog == null || !llmModelCatalog.isUserSelectableModel(model)) {
            throw new IllegalArgumentException("所选模型不可用，请从模型目录中选择已启用模型");
        }
    }

    private void dispatchOnExecutor(GptQueryCommand params,
                                    AgentExecutionCommand agentRequest,
                                    AgentStreamProjection projectingStream) {
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

    private String resolveUserId(AgentExecutionCommand request) {
        String contextUserId = UserRequestContext.currentUserId();
        String userId = StringUtils.defaultIfBlank(contextUserId, request == null ? null : request.getUserId());
        if (StringUtils.isBlank(userId)) {
            throw new IllegalArgumentException("userId不能为空");
        }
        return userId;
    }

    public static void completeProjectionUnlessBackgroundRunning(AgentExecutionCommand agentRequest,
                                                                 AgentStreamProjection projectingStream) {
        completeProjectionUnlessBackgroundRunning(agentRequest, projectingStream, null);
    }

    public static void completeProjectionUnlessBackgroundRunning(AgentExecutionCommand agentRequest,
                                                                 AgentStreamProjection projectingStream,
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

    public static boolean shouldDeferProjectionComplete(AgentExecutionCommand agentRequest) {
        if (agentRequest == null) {
            return false;
        }
        return SessionBackgroundTaskHub.hasRunning(
                SessionBackgroundTaskHub.keyFor(agentRequest.getSessionId(), agentRequest.getRequestId()));
    }

    /**
     * 父循环结束后立即释放占用槽，即使后台子 Agent 还在跑。
     */
    public static void endRunUnlessBackground(AgentExecutionCommand agentRequest, ActiveAgentRunRegistry runRegistry) {
        endOccupancy(agentRequest, runRegistry);
    }

    public static void endOccupancy(AgentExecutionCommand agentRequest, ActiveAgentRunRegistry runRegistry) {
        if (runRegistry == null || agentRequest == null || StringUtils.isBlank(agentRequest.getRequestId())) {
            return;
        }
        runRegistry.end(agentRequest.getRequestId());
    }
}
