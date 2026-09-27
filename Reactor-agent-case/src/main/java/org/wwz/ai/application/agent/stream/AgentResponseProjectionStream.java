package org.wwz.ai.application.agent.stream;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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

/**
 * 将执行内核的 {@link AgentResponse} 投影为浏览器侧 {@link GptProcessResult}。
 * 应用层直接调度时使用，替代旧的 HTTP loopback 再解析路径。
 * <p>投影只赋序、缓冲并发布到 {@link AgentSessionEventBus}。HTTP 观察连接由 Hub 订阅。
 * HITL resume 等仍可挂本地 downstream。断流窗口内的帧按 {@code lastEventSeq} 回放。</p>
 */
@Slf4j
public class AgentResponseProjectionStream implements AgentSessionStream {

    /** 断流窗口内保留的最近投影帧数（含 tool/结果，不含心跳）。 */
    static final int REPLAY_BUFFER_SIZE = 512;

    private final List<ObserverSink> observers = new CopyOnWriteArrayList<>();
    private final AgentSessionEventBus eventBus;
    private final SessionEventClock eventClock;
    private SessionProjectionRegistry projectionRegistry;
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
    /**
     * 主/子 Agent 并行工具会同时 printer.send。EventResult / agentRespList 不是线程安全的，
     * 投影必须单写，否则 taskId、orderMapping、resultMap 会错配到前端。
     */
    private final Object projectionLock = new Object();

    public AgentResponseProjectionStream(AgentSessionStream downstream,
                                         AgentRequest request,
                                         Map<AgentType, AgentResponseHandler> handlerMap) {
        this(downstream, request, handlerMap, null);
    }

    public AgentResponseProjectionStream(AgentSessionStream downstream,
                                         AgentRequest request,
                                         Map<AgentType, AgentResponseHandler> handlerMap,
                                         AgentSessionEventBus eventBus) {
        this(downstream, request, handlerMap, eventBus, null);
    }

    public AgentResponseProjectionStream(AgentSessionStream downstream,
                                         AgentRequest request,
                                         Map<AgentType, AgentResponseHandler> handlerMap,
                                         AgentSessionEventBus eventBus,
                                         SessionEventClock eventClock) {
        this.request = request;
        this.handlerMap = handlerMap == null ? Map.of() : handlerMap;
        this.eventBus = eventBus;
        this.eventClock = eventClock;
        if (downstream != null) {
            addDownstream(downstream, 0L);
        }
    }

    public AgentResponseProjectionStream bindRegistry(SessionProjectionRegistry registry) {
        this.projectionRegistry = registry;
        if (registry != null && request != null && StringUtils.isNotBlank(request.getSessionId())) {
            registry.register(request.getSessionId(), this);
        }
        return this;
    }

    public boolean isClosed() {
        return closed.get();
    }

    /**
     * 刷新/重连后续绑浏览器观察流。已 finish 关闭的投影不再接受续绑。
     * 续绑后补发断流窗口内缓冲帧。旧观察者若仍活着会继续收帧，不会被替换。
     */
    public void rebindDownstream(AgentSessionStream next) {
        rebindDownstream(next, 0L);
    }

    public void rebindDownstream(AgentSessionStream next, long lastEventSeq) {
        addDownstream(next, lastEventSeq);
    }

    public void addDownstream(AgentSessionStream next, long lastEventSeq) {
        if (next == null) {
            return;
        }
        synchronized (projectionLock) {
            pruneAbortedObservers();
            ObserverSink sink = new ObserverSink(next);
            observers.add(sink);
            wireObserverAbort(sink);
            log.info("{} attach projection observer lastEventSeq={} observers={}",
                    request == null ? "-" : request.getRequestId(), lastEventSeq, observers.size());
            boolean wasClosed = closed.get();
            replayBufferedFrames(next, lastEventSeq, wasClosed);
            if (wasClosed && !next.isAborted()) {
                next.complete();
            }
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

        synchronized (projectionLock) {
            if (closed.get()) {
                return;
            }
            AgentType agentType = AgentType.fromCode(request.getAgentType());
            AgentResponseHandler handler = handlerMap.get(agentType);
            if (handler == null) {
                log.error("{} no AgentResponseHandler found for agentType: {}",
                        request.getRequestId(), agentType);
                GptProcessResult failed = buildDefaultResult(request, "unsupported agentType: " + agentType);
                assignSeq(failed);
                offerReplayBuffer(failed);
                forwardIfLive(failed);
                return;
            }

            // 断流期间仍推进投影状态，避免 rebind 后状态机落后。
            GptProcessResult result = handler.handle(request, agentResponse, agentRespList, eventResult);
            assignSeq(result);
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
    }

    private static boolean isStreamSettle(AgentResponse agentResponse) {
        return agentResponse != null && "stream_settle".equals(agentResponse.getMessageType());
    }

    @Override
    public void complete() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        persistWatermark(lastBufferedSeq());
        detachRegistry();
        for (ObserverSink sink : observers) {
            if (sink.stream != null && !sink.stream.isAborted()) {
                sink.stream.complete();
            }
        }
    }

    @Override
    public void completeWithError(Throwable throwable) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        GptProcessResult failed = buildDefaultResult(
                request, throwable == null ? "执行失败" : throwable.getMessage());
        assignSeq(failed);
        offerReplayBuffer(failed);
        if (eventBus != null && request != null && StringUtils.isNotBlank(request.getSessionId())) {
            eventBus.publish(request.getSessionId(), failed);
        }
        detachRegistry();
        for (ObserverSink sink : observers) {
            if (sink.stream != null && !sink.stream.isAborted()) {
                sink.stream.completeWithError(throwable);
            }
        }
    }

    @Override
    public void onAbort(Runnable abortHandler) {
        if (abortHandler == null) {
            return;
        }
        abortHandlers.add(abortHandler);
        if (isAborted() && !closed.get()) {
            abortHandler.run();
        }
    }

    @Override
    public boolean isAborted() {
        if (closed.get()) {
            return true;
        }
        // 主路径只发布到 session bus，没有本地 sink 也不算 aborted。
        if (eventBus != null) {
            return false;
        }
        return liveObservers().isEmpty();
    }

    public List<GptProcessResult> replayAfter(long lastEventSeq) {
        List<GptProcessResult> snapshot;
        synchronized (bufferLock) {
            snapshot = new ArrayList<>(replayBuffer);
        }
        List<GptProcessResult> frames = new ArrayList<>();
        for (GptProcessResult frame : snapshot) {
            if (frame.getEventSeq() <= lastEventSeq) {
                continue;
            }
            frames.add(frame);
        }
        return frames;
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

    private void forwardIfLive(Object payload) {
        if (eventBus != null && request != null && StringUtils.isNotBlank(request.getSessionId())) {
            eventBus.publish(request.getSessionId(), payload);
        }
        for (ObserverSink sink : liveObservers()) {
            try {
                sink.stream.send(payload);
            } catch (Exception e) {
                log.warn("{} forward observer failed",
                        request == null ? "-" : request.getRequestId(), e);
            }
        }
    }

    private void assignSeq(GptProcessResult result) {
        if (result == null) {
            return;
        }
        result.setEventSeq(nextSeq());
        if (isDurable(result)) {
            persistWatermark(result.getEventSeq());
        }
    }

    private long nextSeq() {
        if (eventClock != null && request != null && StringUtils.isNotBlank(request.getSessionId())) {
            return eventClock.next(request.getSessionId());
        }
        return eventSequence.incrementAndGet();
    }

    private void persistWatermark(long eventSeq) {
        if (eventClock == null || request == null || StringUtils.isBlank(request.getSessionId()) || eventSeq <= 0) {
            return;
        }
        eventClock.persist(request.getSessionId(), eventSeq);
    }

    private long lastBufferedSeq() {
        synchronized (bufferLock) {
            GptProcessResult last = replayBuffer.peekLast();
            return last == null ? 0L : last.getEventSeq();
        }
    }

    private static boolean isDurable(GptProcessResult result) {
        if (result.isFinished()) {
            return true;
        }
        String packageType = result.getPackageType();
        return "result".equals(packageType)
                || "tool".equals(packageType)
                || "stream_settle".equals(packageType)
                || "follow_idle".equals(packageType);
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

    private List<ObserverSink> liveObservers() {
        List<ObserverSink> live = new ArrayList<>();
        for (ObserverSink sink : observers) {
            if (sink.stream != null && !sink.stream.isAborted()) {
                live.add(sink);
            }
        }
        return live;
    }

    private void detachRegistry() {
        if (projectionRegistry == null || request == null) {
            completeSessionObservers();
            return;
        }
        projectionRegistry.unregister(request.getSessionId(), this, this::completeSessionObservers);
    }

    private void completeSessionObservers() {
        if (eventBus != null && request != null && StringUtils.isNotBlank(request.getSessionId())) {
            eventBus.completeSession(request.getSessionId());
        }
    }

    private void pruneAbortedObservers() {
        observers.removeIf(sink -> sink.stream == null || sink.stream.isAborted());
    }

    private void wireObserverAbort(ObserverSink sink) {
        if (sink == null || sink.stream == null) {
            return;
        }
        sink.stream.onAbort(() -> {
            observers.remove(sink);
            if (closed.get() || !liveObservers().isEmpty()) {
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
        result.setResultMap(new HashMap<>());
        result.setStatus("failed");
        result.setFinished(true);
        result.setErrorMsg(errMsg);
        return result;
    }

    private static final class ObserverSink {
        private final AgentSessionStream stream;

        private ObserverSink(AgentSessionStream stream) {
            this.stream = stream;
        }
    }
}
