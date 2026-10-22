package org.wwz.ai.application.agent.stream;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.command.AgentExecutionCommand;
import org.wwz.ai.domain.agent.runtime.stream.AgentStreamAccumulator;
import org.wwz.ai.domain.agent.runtime.stream.AgentStreamEvent;
import org.wwz.ai.domain.agent.runtime.stream.AgentStreamResult;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;
import org.wwz.ai.domain.agent.runtime.enums.ResponseTypeEnum;
import org.wwz.ai.domain.agent.runtime.handler.AgentStreamEventHandler;

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
 * 将执行内核的 {@link AgentStreamEvent} 投影为 Case {@link AgentSessionStreamFrame}。
 * 应用层直接调度时使用，替代旧的 HTTP loopback 再解析路径。
 * <p>投影只赋序、缓冲并发布到 {@link AgentSessionEventBus}。HTTP 观察连接由 Hub 订阅。
 * HITL resume 等仍可挂本地 downstream。断流窗口内的帧按 {@code lastEventSeq} 回放。</p>
 */
@Slf4j
public class AgentStreamProjection implements AgentSessionStream {

    /** 断流窗口内保留的最近投影帧数（含 tool/结果，不含心跳）。 */
    static final int REPLAY_BUFFER_SIZE = 512;

    private final List<ObserverSink> observers = new CopyOnWriteArrayList<>();
    private final AgentSessionEventBus eventBus;
    private final SessionEventClock eventClock;
    private SessionProjectionRegistry projectionRegistry;
    private final AgentExecutionCommand request;
    private final Map<AgentType, AgentStreamEventHandler> handlerMap;
    private final List<AgentStreamEvent> agentRespList = new ArrayList<>();
    private final AgentStreamAccumulator eventResult = new AgentStreamAccumulator();
    private final List<Runnable> abortHandlers = new CopyOnWriteArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicLong eventSequence = new AtomicLong();
    private final long startTime = System.currentTimeMillis();
    private final Deque<AgentSessionStreamFrame> replayBuffer = new ArrayDeque<>();
    private final Object bufferLock = new Object();
    private final AgentStreamFrameMapper frameMapper = new AgentStreamFrameMapper();
    /**
     * 主/子 Agent 并行工具会同时 printer.send。Accumulator / agentRespList 不是线程安全的，
     * 投影必须单写，否则 taskId、orderMapping、resultMap 会错配到前端。
     */
    private final Object projectionLock = new Object();

    public AgentStreamProjection(AgentSessionStream downstream,
                                          AgentExecutionCommand request,
                                         Map<AgentType, AgentStreamEventHandler> handlerMap) {
        this(downstream, request, handlerMap, null);
    }

    public AgentStreamProjection(AgentSessionStream downstream,
                                          AgentExecutionCommand request,
                                         Map<AgentType, AgentStreamEventHandler> handlerMap,
                                         AgentSessionEventBus eventBus) {
        this(downstream, request, handlerMap, eventBus, null);
    }

    public AgentStreamProjection(AgentSessionStream downstream,
                                          AgentExecutionCommand request,
                                         Map<AgentType, AgentStreamEventHandler> handlerMap,
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

    public AgentStreamProjection bindRegistry(SessionProjectionRegistry registry) {
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
        if (payload instanceof AgentStreamEvent event) {
            sendRuntimeEvent(event);
            return;
        }
        if (payload instanceof AgentSessionStreamFrame frame) {
            forwardIfLive(frame);
            return;
        }
    }

    @Override
    public void sendRuntimeEvent(AgentStreamEvent agentResponse) throws Exception {
        if (closed.get()) {
            return;
        }

        synchronized (projectionLock) {
            if (closed.get()) {
                return;
            }
            AgentType agentType = AgentType.fromCode(request.getAgentType());
            AgentStreamEventHandler handler = handlerMap.get(agentType);
            if (handler == null) {
                log.error("{} no AgentStreamEventHandler found for agentType: {}",
                        request.getRequestId(), agentType);
                AgentSessionStreamFrame failed = buildDefaultResult(request, "unsupported agentType: " + agentType);
                assignSeq(failed);
                offerReplayBuffer(failed);
                forwardIfLive(failed);
                return;
            }

            // 断流期间仍推进投影状态，避免 rebind 后状态机落后。
            AgentStreamResult result = handler.handle(request, agentResponse, agentRespList, eventResult);
            AgentSessionStreamFrame frame = frameMapper.toFrame(result);
            assignSeq(frame);
            offerReplayBuffer(frame);
            forwardIfLive(frame);
            // 根 result 的 finished 只表示业务终态（前端收口 loading），不在此关传输层。
            // 关流留给：1) GptQuery / HITL resume 在 finishRun、markAnswered 之后的显式 complete；
            // 2) 后台空闲时的 stream_settle。避免 SSE 在 ledger/approval 落库前被掐断。
            if (frame != null && frame.isFinished() && isStreamSettle(agentResponse)) {
                log.info("{} task total cost time:{}ms",
                        request.getRequestId(), System.currentTimeMillis() - startTime);
                complete();
            }
        }
    }

    private static boolean isStreamSettle(AgentStreamEvent agentResponse) {
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
        AgentSessionStreamFrame failed = buildDefaultResult(
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

    public List<AgentSessionStreamFrame> replayAfter(long lastEventSeq) {
        List<AgentSessionStreamFrame> snapshot;
        synchronized (bufferLock) {
            snapshot = new ArrayList<>(replayBuffer);
        }
        List<AgentSessionStreamFrame> frames = new ArrayList<>();
        for (AgentSessionStreamFrame frame : snapshot) {
            if (frame.getEventSeq() <= lastEventSeq) {
                continue;
            }
            frames.add(frame);
        }
        return frames;
    }

    public static AgentSessionStreamFrame buildHeartbeat(String requestId) {
        return AgentSessionStreamFrame.builder()
                .finished(false)
                .status("success")
                .responseType(ResponseTypeEnum.text.name())
                .response("")
                .responseAll("")
                .useTimes(0)
                .useTokens(0)
                .reqId(requestId)
                .packageType("heartbeat")
                .encrypted(false)
                .build();
    }

    /**
     * 续流时若 run 已不在进程内且 ledger 终态，向前端推终态空包。
     */
    public static AgentSessionStreamFrame buildFollowIdle(String requestId) {
        return AgentSessionStreamFrame.builder()
                .finished(true)
                .status("success")
                .responseType(ResponseTypeEnum.text.name())
                .response("")
                .responseAll("")
                .useTimes(0)
                .useTokens(0)
                .reqId(requestId)
                .packageType("follow_idle")
                .encrypted(false)
                .build();
    }

    /**
     * registry 暂无但 ledger 仍 RUNNING：提示前端继续退避重连，不要当任务结束。
     */
    public static AgentSessionStreamFrame buildFollowPending(String requestId) {
        return buildFollowPending(requestId, null);
    }

    public static AgentSessionStreamFrame buildFollowPending(String requestId, Long retryMs) {
        return AgentSessionStreamFrame.builder()
                .finished(false)
                .status("success")
                .responseType(ResponseTypeEnum.text.name())
                .response("")
                .responseAll("")
                .useTimes(0)
                .useTokens(0)
                .reqId(requestId)
                .packageType("follow_pending")
                .encrypted(false)
                .retryMs(retryMs)
                .build();
    }

    private void forwardIfLive(AgentSessionStreamFrame payload) {
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

    private void assignSeq(AgentSessionStreamFrame result) {
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
            AgentSessionStreamFrame last = replayBuffer.peekLast();
            return last == null ? 0L : last.getEventSeq();
        }
    }

    private static boolean isDurable(AgentSessionStreamFrame result) {
        if (result.isFinished()) {
            return true;
        }
        String packageType = result.getPackageType();
        return "result".equals(packageType)
                || "tool".equals(packageType)
                || "stream_settle".equals(packageType)
                || "follow_idle".equals(packageType);
    }

    private void offerReplayBuffer(AgentSessionStreamFrame result) {
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
        List<AgentSessionStreamFrame> snapshot;
        synchronized (bufferLock) {
            snapshot = new ArrayList<>(replayBuffer);
        }
        if (snapshot.isEmpty()) {
            return;
        }
        log.info("{} replay {} buffered frames after rebind",
                request == null ? "-" : request.getRequestId(), snapshot.size());
        for (AgentSessionStreamFrame frame : snapshot) {
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

    private static AgentSessionStreamFrame buildDefaultResult(AgentExecutionCommand request, String errMsg) {
        return AgentSessionStreamFrame.builder()
                .resultMap(new HashMap<>())
                .status("failed")
                .finished(true)
                .errorMsg(errMsg)
                .reqId(request == null ? null : request.getRequestId())
                .traceId(request == null ? null : request.getRequestId())
                .build();
    }

    private static final class ObserverSink {
        private final AgentSessionStream stream;

        private ObserverSink(AgentSessionStream stream) {
            this.stream = stream;
        }
    }
}
