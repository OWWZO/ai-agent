package org.wwz.ai.application.agent.stream;

import lombok.extern.slf4j.Slf4j;
import org.wwz.ai.domain.agent.reactor.model.multi.EventResult;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.reactor.model.response.AgentResponse;
import org.wwz.ai.domain.agent.reactor.model.response.GptProcessResult;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;
import org.wwz.ai.domain.agent.runtime.enums.ResponseTypeEnum;
import org.wwz.ai.domain.agent.runtime.handler.AgentResponseHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 将执行内核的 {@link AgentResponse} 投影为浏览器侧 {@link GptProcessResult}。
 * 应用层直接调度时使用，替代旧的 HTTP loopback 再解析路径。
 */
@Slf4j
public class AgentResponseProjectionStream implements AgentSessionStream {

    private final AgentSessionStream downstream;
    private final AgentRequest request;
    private final Map<AgentType, AgentResponseHandler> handlerMap;
    private final List<AgentResponse> agentRespList = new ArrayList<>();
    private final EventResult eventResult = new EventResult();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final long startTime = System.currentTimeMillis();

    public AgentResponseProjectionStream(AgentSessionStream downstream,
                                         AgentRequest request,
                                         Map<AgentType, AgentResponseHandler> handlerMap) {
        this.downstream = downstream;
        this.request = request;
        this.handlerMap = handlerMap == null ? Map.of() : handlerMap;
    }

    @Override
    public void send(Object payload) throws Exception {
        if (closed.get() || downstream.isAborted()) {
            return;
        }
        if (!(payload instanceof AgentResponse agentResponse)) {
            downstream.send(payload);
            return;
        }

        AgentType agentType = AgentType.fromCode(request.getAgentType());
        AgentResponseHandler handler = handlerMap.get(agentType);
        if (handler == null) {
            log.error("{} no AgentResponseHandler found for agentType: {}",
                    request.getRequestId(), agentType);
            downstream.send(buildDefaultResult(request, "unsupported agentType: " + agentType));
            return;
        }

        GptProcessResult result = handler.handle(request, agentResponse, agentRespList, eventResult);
        downstream.send(result);
        if (result.isFinished()) {
            log.info("{} task total cost time:{}ms",
                    request.getRequestId(), System.currentTimeMillis() - startTime);
            complete();
        }
    }

    @Override
    public void complete() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        downstream.complete();
    }

    @Override
    public void completeWithError(Throwable throwable) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        downstream.completeWithError(throwable);
    }

    @Override
    public void onAbort(Runnable abortHandler) {
        downstream.onAbort(abortHandler);
    }

    @Override
    public boolean isAborted() {
        return closed.get() || downstream.isAborted();
    }

    public static GptProcessResult buildHeartbeat(String requestId) {
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

    private static GptProcessResult buildDefaultResult(AgentRequest request, String errMsg) {
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
}
