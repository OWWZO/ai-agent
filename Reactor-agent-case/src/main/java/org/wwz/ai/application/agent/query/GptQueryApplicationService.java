package org.wwz.ai.application.agent.query;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.wwz.ai.application.agent.dispatch.IAgentDispatchService;
import org.wwz.ai.application.agent.stream.AgentResponseProjectionStream;
import org.wwz.ai.application.agent.stream.AgentSessionStream;
import org.wwz.ai.application.agent.visitor.ConversationSessionOwnershipApplicationService;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.reactor.model.req.GptQueryReq;
import org.wwz.ai.domain.agent.runtime.GptQueryAgentRequestFactory;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;
import org.wwz.ai.domain.agent.runtime.executor.AgentExecutorSupport;
import org.wwz.ai.domain.agent.runtime.handler.AgentResponseHandler;
import org.wwz.ai.types.agent.config.AgentExecutorNames;
import org.wwz.ai.types.agent.exception.AgentExecutorBusyException;
import org.wwz.ai.types.agent.visitor.VisitorRequestContext;

import javax.annotation.Resource;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * GPT 查询应用服务。
 * 主聊天路径在进程内直接调度执行策略，并把 {@code AgentResponse} 投影为浏览器侧结果；
 * 不再经 HTTP loopback 到 {@code /AutoAgent}。
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
    private Map<AgentType, AgentResponseHandler> handlerMap;

    @Resource
    @Qualifier(AgentExecutorNames.DISPATCH_EXECUTOR)
    private Executor dispatchExecutor;

    @Override
    public void queryAgentStreamIncr(GptQueryReq params, AgentSessionStream stream) {
        gptQueryAgentRequestFactory.normalize(params);
        AgentRequest agentRequest = gptQueryAgentRequestFactory.build(params);
        log.info("{} start handle Agent request: {}", params.getRequestId(), JSON.toJSONString(agentRequest));

        try {
            String visitorId = resolveVisitorId(agentRequest);
            agentRequest.setVisitorId(visitorId);
            conversationSessionOwnershipApplicationService.ensureSessionAccessible(
                    visitorId,
                    agentRequest.getSessionId(),
                    agentRequest.getQuery()
            );
        } catch (Exception e) {
            log.warn("{} reject gpt query before dispatch", agentRequest.getRequestId(), e);
            stream.completeWithError(e);
            return;
        }

        AgentResponseProjectionStream projectingStream =
                new AgentResponseProjectionStream(stream, agentRequest, handlerMap);
        try {
            AgentExecutorSupport.execute(dispatchExecutor, "dispatch", agentRequest.getRequestId(),
                    () -> dispatchOnExecutor(params, agentRequest, projectingStream, stream));
        } catch (AgentExecutorBusyException e) {
            log.warn("{} dispatch rejected", agentRequest.getRequestId(), e);
            stream.completeWithError(e);
        }
    }

    private void dispatchOnExecutor(GptQueryReq params,
                                    AgentRequest agentRequest,
                                    AgentResponseProjectionStream projectingStream,
                                    AgentSessionStream stream) {
        try {
            agentDispatchService.dispatch(agentRequest, projectingStream);
            projectingStream.complete();
        } catch (Exception e) {
            if (projectingStream.isAborted() || stream.isAborted()) {
                log.info("{} dispatch stopped after downstream abort", agentRequest.getRequestId());
                projectingStream.complete();
                return;
            }
            log.error("{} direct dispatch error", agentRequest.getRequestId(), e);
            projectingStream.completeWithError(e);
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
}
