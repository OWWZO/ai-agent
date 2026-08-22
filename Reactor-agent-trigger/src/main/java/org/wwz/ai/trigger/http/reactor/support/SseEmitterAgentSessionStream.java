package org.wwz.ai.trigger.http.reactor.support;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.application.agent.stream.AgentSessionStream;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于 SSE 的会话输出适配器。
 * 触发层负责把 HTTP 协议细节封装为应用层可消费的流端口。
 */
public class SseEmitterAgentSessionStream implements AgentSessionStream {

    private final SseEmitter emitter;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean aborted = new AtomicBoolean(false);
    private final AtomicBoolean localTermination = new AtomicBoolean(false);
    private final AtomicBoolean abortNotified = new AtomicBoolean(false);
    private final List<Runnable> abortHandlers = new CopyOnWriteArrayList<>();
    private final Object sendLock = new Object();

    public SseEmitterAgentSessionStream(SseEmitter emitter) {
        this.emitter = emitter;
        this.emitter.onCompletion(this::handleCompletion);
        this.emitter.onTimeout(this::handleTimeout);
        this.emitter.onError(this::handleError);
    }

    @Override
    public void send(Object payload) throws Exception {
        synchronized (sendLock) {
            if (closed.get()) {
                return;
            }
            try {
                // 心跳与业务帧共用此锁，避免并发 emitter.send 打坏 SSE。
                emitter.send(payload);
            } catch (Exception ex) {
                if (SseClientDisconnectDetector.isClientDisconnected(ex)) {
                    markAborted();
                    return;
                }
                throw ex;
            }
        }
    }

    @Override
    public void complete() {
        synchronized (sendLock) {
            localTermination.set(true);
            if (closed.compareAndSet(false, true)) {
                emitter.complete();
            }
        }
    }

    @Override
    public void completeWithError(Throwable throwable) {
        synchronized (sendLock) {
            localTermination.set(true);
            if (closed.compareAndSet(false, true)) {
                emitter.completeWithError(throwable);
            }
        }
    }

    @Override
    public void onAbort(Runnable abortHandler) {
        if (abortHandler == null) {
            return;
        }
        if (aborted.get()) {
            abortHandler.run();
            return;
        }
        abortHandlers.add(abortHandler);
        if (aborted.get() && abortHandlers.remove(abortHandler)) {
            abortHandler.run();
        }
    }

    @Override
    public boolean isAborted() {
        return aborted.get();
    }

    private void handleCompletion() {
        // localTermination 表示服务端主动结束；否则同一个 completion 回调应被视为客户端断开。
        if (localTermination.get()) {
            closed.set(true);
            return;
        }
        markAborted();
    }

    private void handleTimeout() {
        if (localTermination.get()) {
            closed.set(true);
            return;
        }
        markAborted();
    }

    private void handleError(Throwable throwable) {
        if (localTermination.get()) {
            closed.set(true);
            return;
        }
        if (SseClientDisconnectDetector.isClientDisconnected(throwable)) {
            markAborted();
            return;
        }
        markAborted();
    }

    /**
     * 对客户端断开做一次性广播，避免重复通知观察者。
     */
    private void markAborted() {
        // 多个 SSE 回调可能并发到达，abortNotified 保证每个观察者只被通知一次。
        aborted.set(true);
        closed.set(true);
        if (!abortNotified.compareAndSet(false, true)) {
            return;
        }
        for (Runnable abortHandler : abortHandlers) {
            abortHandler.run();
        }
    }
}
