package org.wwz.ai.trigger.http.reactor.support;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.application.agent.stream.AgentSessionStream;

/**
 * 基于 SSE 的会话输出适配器。
 * 触发层负责把 HTTP 协议细节封装为应用层可消费的流端口。
 */
public class SseEmitterAgentSessionStream implements AgentSessionStream {

    private final SseEmitter emitter;

    public SseEmitterAgentSessionStream(SseEmitter emitter) {
        this.emitter = emitter;
    }

    @Override
    public void send(Object payload) throws Exception {
        emitter.send(payload);
    }

    @Override
    public void complete() {
        emitter.complete();
    }

    @Override
    public void completeWithError(Throwable throwable) {
        emitter.completeWithError(throwable);
    }
}
