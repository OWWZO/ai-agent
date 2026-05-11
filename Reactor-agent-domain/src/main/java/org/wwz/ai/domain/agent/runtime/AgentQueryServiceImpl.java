package org.wwz.ai.domain.agent.runtime;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.adapter.port.AgentMessageStream;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamListener;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamRequest;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamSession;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.model.dto.AutoBotsResult;
import org.wwz.ai.domain.agent.reactor.model.multi.EventResult;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.reactor.model.req.GptQueryReq;
import org.wwz.ai.domain.agent.reactor.model.response.AgentResponse;
import org.wwz.ai.domain.agent.reactor.model.response.GptProcessResult;
import org.wwz.ai.domain.agent.reactor.util.ChateiUtils;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;
import org.wwz.ai.domain.agent.runtime.enums.ResponseTypeEnum;
import org.wwz.ai.domain.agent.runtime.handler.AgentResponseHandler;
import org.wwz.ai.types.agent.visitor.VisitorRequestContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * GPT 查询与多智能体请求的稳定运行时实现。
 * 负责把 legacy 查询请求翻译为统一运行时请求，并通过远端流式端口驱动主链路。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentQueryServiceImpl implements AgentQueryService {

    private final ReactorConfig reactorConfig;
    private final Map<AgentType, AgentResponseHandler> handlerMap;
    private final RemoteStreamPort remoteStreamPort;

    @Override
    public void queryAgentStreamIncr(GptQueryReq req, AgentMessageStream stream) {
        req.setUser("reactor");
        req.setDeepThink(req.getDeepThink() == null ? 0 : req.getDeepThink());
        req.setTraceId(ChateiUtils.getRequestId(req));

        AgentRequest agentRequest = buildAgentRequest(req);
        log.info("{} start handle Agent request: {}", req.getRequestId(), JSON.toJSONString(agentRequest));
        try {
            handleMultiAgentRequest(agentRequest, stream);
        } catch (Exception e) {
            log.error("{}, error in requestMultiAgent, deepThink: {}, errorMsg: {}",
                    req.getRequestId(), req.getDeepThink(), e.getMessage(), e);
            throw e;
        } finally {
            log.info("{}, agent.query.web.singleRequest end, requestId: {}",
                    req.getRequestId(), JSON.toJSONString(req));
        }
    }

    /**
     * 统一处理远端多智能体流式响应，把协议事件投影为领域结果。
     */
    void handleMultiAgentRequest(AgentRequest request, AgentMessageStream stream) {
        long startTime = System.currentTimeMillis();
        RemoteStreamRequest remoteRequest = buildRemoteRequest(request);
        log.info("{} agentRequest:{}", request.getRequestId(), JSON.toJSONString(request));
        try {
            List<AgentResponse> agentRespList = new ArrayList<>();
            EventResult eventResult = new EventResult();
            AtomicBoolean downstreamClosed = new AtomicBoolean(false);
            AtomicReference<RemoteStreamSession> remoteSessionRef = new AtomicReference<>();
            stream.onAbort(() -> cancelRemoteStream(request.getRequestId(), remoteSessionRef));
            remoteSessionRef.set(remoteStreamPort.openStream(remoteRequest, new RemoteStreamListener() {
                @Override
                public void onOpen() {
                    log.info("{} multi agent stream opened", request.getRequestId());
                }

                @Override
                public void onLine(String line) throws Exception {
                    if (stream.isAborted()) {
                        cancelRemoteStream(request.getRequestId(), remoteSessionRef);
                        return;
                    }
                    if (!line.startsWith("data:")) {
                        return;
                    }
                    String data = line.substring(5);
                    if ("[DONE]".equals(data)) {
                        log.info("{} data equals with [DONE] {}:", request.getRequestId(), data);
                        return;
                    }
                    if (data.startsWith("heartbeat")) {
                        stream.send(buildHeartbeatData(request.getRequestId()));
                        log.info("{} heartbeat-data: {}", request.getRequestId(), data);
                        return;
                    }
                    log.info("{} recv from autocontroller: {}", request.getRequestId(), data);
                    AgentResponse agentResponse = JSON.parseObject(data, AgentResponse.class);
                    AgentType agentType = AgentType.fromCode(request.getAgentType());
                    AgentResponseHandler handler = handlerMap.get(agentType);
                    if (handler == null) {
                        log.error("{} no AgentResponseHandler found for agentType: {}",
                                request.getRequestId(), agentType);
                        stream.send(buildDefaultAutobotsResult(request, "unsupported agentType: " + agentType));
                        return;
                    }

                    GptProcessResult result = handler.handle(request, agentResponse, agentRespList, eventResult);
                    stream.send(result);
                    if (result.isFinished()) {
                        log.info("{} task total cost time:{}ms",
                                request.getRequestId(), System.currentTimeMillis() - startTime);
                        closeDownstream(stream, downstreamClosed);
                    }
                }

                @Override
                public void onFailure(Throwable throwable, Integer statusCode, String responseBody) {
                    remoteSessionRef.set(null);
                    if (stream.isAborted()) {
                        log.info("{} downstream SSE aborted, stop relaying upstream stream quietly, code={}",
                                request.getRequestId(), statusCode);
                        closeDownstream(stream, downstreamClosed);
                        return;
                    }
                    log.error("{} multi agent request failed, code={}, body={}",
                            request.getRequestId(), statusCode, responseBody, throwable);
                    failDownstream(stream, downstreamClosed, throwable);
                }

                @Override
                public void onClosed() {
                    remoteSessionRef.set(null);
                    closeDownstream(stream, downstreamClosed);
                }
            }));
            if (stream.isAborted()) {
                cancelRemoteStream(request.getRequestId(), remoteSessionRef);
            }
        } catch (Exception e) {
            log.error("{} open multi agent stream failed", request.getRequestId(), e);
            stream.completeWithError(e);
        }
    }

    private void closeDownstream(AgentMessageStream stream,
                                 AtomicBoolean downstreamClosed) {
        if (!downstreamClosed.compareAndSet(false, true)) {
            return;
        }
        stream.complete();
    }

    private void failDownstream(AgentMessageStream stream,
                                AtomicBoolean downstreamClosed,
                                Throwable throwable) {
        if (!downstreamClosed.compareAndSet(false, true)) {
            return;
        }
        stream.completeWithError(throwable);
    }

    /**
     * 下游浏览器已断开时，主动取消本地到上游 AutoAgent 的转发连接，避免继续空跑。
     */
    private void cancelRemoteStream(String requestId,
                                    AtomicReference<RemoteStreamSession> remoteSessionRef) {
        RemoteStreamSession remoteStreamSession = remoteSessionRef.getAndSet(null);
        if (remoteStreamSession == null) {
            return;
        }
        try {
            remoteStreamSession.cancel();
            log.info("{} cancel upstream stream because downstream SSE already aborted", requestId);
        } catch (Exception e) {
            log.warn("{} cancel upstream stream failed", requestId, e);
        }
    }

    private RemoteStreamRequest buildRemoteRequest(AgentRequest request) {
        return RemoteStreamRequest.builder()
                .method("POST")
                .url("http://127.0.0.1:8100/AutoAgent")
                .headers(Map.of("Content-Type", "application/json"))
                .body(JSONObject.toJSONString(request))
                .connectTimeoutSeconds(60L)
                .readTimeoutSeconds((long) reactorConfig.getSseClientReadTimeout())
                .writeTimeoutSeconds(1800L)
                .callTimeoutSeconds((long) reactorConfig.getSseClientConnectTimeout())
                .build();
    }

    private AgentRequest buildAgentRequest(GptQueryReq req) {
        AgentRequest request = new AgentRequest();
        request.setRequestId(req.getTraceId());
        request.setSessionId(req.getSessionId());
        request.setVisitorId(VisitorRequestContext.currentVisitorId());
        request.setErp(req.getUser());
        request.setQuery(req.getQuery());
        request.setSessionFiles(req.getSessionFiles());

        if ("chat".equalsIgnoreCase(req.getOutputStyle())) {
            request.setAgentType(AgentType.WORKFLOW.getValue());
            request.setSopPrompt("");
        } else {
            Integer agentType = (req.getDeepThink() == null || req.getDeepThink() == 0)
                    ? AgentType.REACT.getValue()
                    : AgentType.PLAN_SOLVE.getValue();
            request.setAgentType(agentType);
            request.setSopPrompt(agentType.equals(AgentType.PLAN_SOLVE.getValue())
                    ? reactorConfig.getReactorSopPrompt()
                    : "");
            request.setBasePrompt(agentType.equals(AgentType.REACT.getValue())
                    ? reactorConfig.getReactorBasePrompt()
                    : "");
        }

        request.setIsStream(true);
        request.setOutputStyle(req.getOutputStyle());
        return request;
    }

    private GptProcessResult buildDefaultAutobotsResult(AgentRequest request, String errMsg) {
        GptProcessResult result = new GptProcessResult();
        boolean routerRequest = AgentType.ROUTER.getValue().equals(request.getAgentType());
        if (routerRequest) {
            result.setStatus("success");
            result.setFinished(true);
            result.setResponse(errMsg);
            result.setTraceId(request.getRequestId());
        } else {
            result.setResultMap(new HashMap<>());
            result.setStatus("failed");
            result.setFinished(true);
            result.setErrorMsg(errMsg);
        }
        return result;
    }

    private GptProcessResult buildHeartbeatData(String requestId) {
        GptProcessResult result = new GptProcessResult();
        result.setFinished(false);
        result.setStatus("success");
        result.setResponseType(ResponseTypeEnum.text.name());
        result.setResponse("");
        result.setResponseAll("");
        result.setUseTimes(0);
        result.setUseTokens(0);
        result.setReqId(requestId);
        result.setPackageType("heartbeat");
        result.setEncrypted(false);
        return result;
    }
}
