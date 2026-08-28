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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 将执行内核的 {@link AgentResponse} 投影为浏览器侧 {@link GptProcessResult}。
 * 应用层直接调度时使用，替代旧的 HTTP loopback 再解析路径。
 * <p>下游观察流可在客户端断开后通过 {@link #rebindDownstream(AgentSessionStream)} 续绑，
 * 投影状态（EventResult / 已投影响应列表）保持不变，仅替换浏览器 SSE 承载。
 * 断流期间投影结果写入环形缓冲，续绑后补发，降低丢帧。</p>
 */
@Slf4j
public class AgentResponseProjectionStream implements AgentSessionStream {

    /** 断流窗口内保留的最近投影帧数（含 tool/结果，不含心跳）。 */
    static final int REPLAY_BUFFER_SIZE = 128;

    private final AtomicReference<AgentSessionStream> downstreamRef = new AtomicReference<>();
    private final AgentRequest request;
    private final Map<AgentType, AgentResponseHandler> handlerMap;
    private final List<AgentResponse> agentRespList = new ArrayList<>();
    private final EventResult eventResult = new EventResult();
    private final List<Runnable> abortHandlers = new CopyOnWriteArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicLong eventSequence = new AtomicLong();
    private final long startTime = System.currentTimeMillis();
    private final Deque<GptProcessResult> replayBuffer = new ArrayDeque<>();
    private final Object bufferLock = new Object();

    public AgentResponseProjectionStream(AgentSessionStream downstream,
                                         AgentRequest request,
                                         Map<AgentType, AgentResponseHandler> handlerMap) {
        this.request = request;
        this.handlerMap = handlerMap == null ? Map.of() : handlerMap;
        if (downstream != null) {
            this.downstreamRef.set(downstream);
            wireDownstreamAbort(downstream);
        }
    }

    /**
     * 刷新/重连后续绑浏览器观察流。已 finish 关闭的投影不再接受续绑。
     * 续绑后补发断流窗口内缓冲帧。
     */
    public void rebindDownstream(AgentSessionStream next) {
        rebindDownstream(next, 0L);
    }

    public void rebindDownstream(AgentSessionStream next, long lastEventSeq) {
        if (next == null) {
            return;
        }
        downstreamRef.set(next);
        wireDownstreamAbort(next);
        log.info("{} rebind projection downstream", request == null ? "-" : request.getRequestId());
        boolean wasClosed = closed.get();
        replayBufferedFrames(next, lastEventSeq, wasClosed);
        if (wasClosed && !next.isAborted()) {
            next.complete();
        }
    }

    @Override
    public void send(Object payload) throws Exception {
        if (closed.get()) {
            return;
        }
        if (!(payload instanceof AgentResponse agentResponse)) {
            forwardIfLive(payload);
            return;
        }

        AgentType agentType = AgentType.fromCode(request.getAgentType());
        AgentResponseHandler handler = handlerMap.get(agentType);
        if (handler == null) {
            log.error("{} no AgentResponseHandler found for agentType: {}",
                    request.getRequestId(), agentType);
            GptProcessResult failed = buildDefaultResult(request, "unsupported agentType: " + agentType);
            offerReplayBuffer(failed);
            forwardIfLive(failed);
            return;
        }

        // 断流期间仍推进投影状态，避免 rebind 后状态机落后。
        GptProcessResult result = handler.handle(request, agentResponse, agentRespList, eventResult);
        result.setEventSeq(eventSequence.incrementAndGet());
        offerReplayBuffer(result);
        forwardIfLive(result);
        // 根 result 的 finished 只表示业务终态（前端收口 loading），不在此关传输层。
        // 关流留给：1) GptQuery / HITL resume 在 finishRun、markAnswered 之后的显式 complete；
        // 2) 后台空闲时的 stream_settle。避免 SSE 在 ledger/approval 落库前被掐断。
        if (result.isFinished() && isStreamSettle(agentResponse)) {
            log.info("{} task total cost time:{}ms",
                    request.getRequestId(), System.currentTimeMillis() - startTime);
            complete();
        }
    }

    private static boolean isStreamSettle(AgentResponse agentResponse) {
        return agentResponse != null && "stream_settle".equals(agentResponse.getMessageType());
    }

    @Override
    public void complete() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        AgentSessionStream downstream = currentDownstream();
        if (downstream != null) {
            downstream.complete();
        }
    }

    @Override
    public void completeWithError(Throwable throwable) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        AgentSessionStream downstream = currentDownstream();
        if (downstream != null) {
            downstream.completeWithError(throwable);
        }
    }

    @Override
    public void onAbort(Runnable abortHandler) {
        if (abortHandler == null) {
            return;
        }
        abortHandlers.add(abortHandler);
        AgentSessionStream downstream = currentDownstream();
        if (downstream != null && downstream.isAborted() && !closed.get()) {
            abortHandler.run();
        }
    }

    @Override
    public boolean isAborted() {
        // 对外仍表示「当前浏览器观察流是否断开」；closed 才是投影生命周期结束。
        // 续绑后下游恢复，isAborted 变 false。
        if (closed.get()) {
            return true;
        }
        AgentSessionStream downstream = currentDownstream();
        return downstream == null || downstream.isAborted();
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

    /**
     * 续流时若 run 已不在进程内且 ledger 终态，向前端推终态空包。
     */
    public static GptProcessResult buildFollowIdle(String requestId) {
        GptProcessResult result = new GptProcessResult();
        result.setFinished(true);
        result.setStatus("success");
        result.setResponseType(ResponseTypeEnum.text.name());
        result.setResponse("");
        result.setResponseAll("");
        result.setUseTimes(0);
        result.setUseTokens(0);
        result.setReqId(requestId);
        result.setPackageType("follow_idle");
        result.setEncrypted(false);
        return result;
    }

    /**
     * registry 暂无但 ledger 仍 RUNNING：提示前端继续退避重连，不要当任务结束。
     */
    public static GptProcessResult buildFollowPending(String requestId) {
        return buildFollowPending(requestId, null);
    }

    public static GptProcessResult buildFollowPending(String requestId, Long retryMs) {
        GptProcessResult result = new GptProcessResult();
        result.setFinished(false);
        result.setStatus("success");
        result.setResponseType(ResponseTypeEnum.text.name());
        result.setResponse("");
        result.setResponseAll("");
        result.setUseTimes(0);
        result.setUseTokens(0);
        result.setReqId(requestId);
        result.setPackageType("follow_pending");
        result.setEncrypted(false);
        result.setRetryMs(retryMs);
        return result;
    }

    private void forwardIfLive(Object payload) throws Exception {
        AgentSessionStream liveDownstream = currentDownstream();
        if (liveDownstream == null || liveDownstream.isAborted()) {
            return;
        }
        liveDownstream.send(payload);
    }

    private void offerReplayBuffer(GptProcessResult result) {
        if (result == null || "heartbeat".equals(result.getPackageType())) {
            return;
        }
        synchronized (bufferLock) {
            if (replayBuffer.size() >= REPLAY_BUFFER_SIZE) {
                replayBuffer.removeFirst();
            }
            replayBuffer.addLast(result);
        }
    }

    private void replayBufferedFrames(AgentSessionStream next, long lastEventSeq, boolean allowClosed) {
        List<GptProcessResult> snapshot;
        synchronized (bufferLock) {
            snapshot = new ArrayList<>(replayBuffer);
        }
        if (snapshot.isEmpty()) {
            return;
        }
        log.info("{} replay {} buffered frames after rebind",
                request == null ? "-" : request.getRequestId(), snapshot.size());
        for (GptProcessResult frame : snapshot) {
            if (frame.getEventSeq() > 0 && frame.getEventSeq() <= lastEventSeq) {
                continue;
            }
            if (next.isAborted() || (!allowClosed && closed.get())) {
                return;
            }
            try {
                next.send(frame);
            } catch (Exception e) {
                log.warn("{} replay frame failed after rebind",
                        request == null ? "-" : request.getRequestId(), e);
                return;
            }
        }
    }

    private AgentSessionStream currentDownstream() {
        return downstreamRef.get();
    }

    private void wireDownstreamAbort(AgentSessionStream downstream) {
        if (downstream == null) {
            return;
        }
        downstream.onAbort(() -> {
            if (downstreamRef.get() != downstream) {
                return;
            }
            for (Runnable handler : abortHandlers) {
                try {
                    handler.run();
                } catch (Exception e) {
                    log.warn("{} projection abort handler failed",
                            request == null ? "-" : request.getRequestId(), e);
                }
            }
        });
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
