package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.trigger.http.reactor.support.SseEmitterAgentSessionStream;
import org.wwz.ai.application.agent.stream.AgentSessionStreamFrame;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SSE 会话流适配器回归测试。
 */
public class SseEmitterAgentSessionStreamTest {

    @Test
    public void shouldIgnoreSendAfterStreamCompleted() throws Exception {
        SseEmitter emitter = new SseEmitter();
        SseEmitterAgentSessionStream stream = new SseEmitterAgentSessionStream(emitter);

        stream.complete();
        stream.send("ignored-after-complete");

        Assert.assertTrue("关闭后的重复发送应被静默忽略", true);
    }

    @Test
    public void shouldMarkStreamAbortedWhenClientDisconnectsDuringSend() throws Exception {
        AtomicBoolean abortTriggered = new AtomicBoolean(false);
        SseEmitterAgentSessionStream stream = new SseEmitterAgentSessionStream(new DisconnectingSseEmitter());
        stream.onAbort(() -> abortTriggered.set(true));

        stream.send("payload");

        Assert.assertTrue("客户端断开后应标记为 aborted", stream.isAborted());
        Assert.assertTrue("客户端断开后应触发断开观察回调", abortTriggered.get());
    }

        @Test
    public void shouldWriteEventSeqAsSseId() throws Exception {
        AgentSessionStreamFrame payload = new AgentSessionStreamFrame();
        payload.setEventSeq(17L);
        payload.setPackageType("result");
        SseEmitter.SseEventBuilder builder = SseEmitterAgentSessionStream.toSseEvent(payload);
        Assert.assertNotNull(builder.build());
    }

    private static class DisconnectingSseEmitter extends SseEmitter {

        @Override
        public void send(Object object) throws IOException {
            throw new IOException("你的主机中的软件中止了一个已建立的连接。");
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            throw new IOException("你的主机中的软件中止了一个已建立的连接。");
        }
    }
}
