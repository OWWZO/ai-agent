package org.wwz.ai.trigger.http.browser.bsk;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

/**
 * 把 {@link WebSocketSession} 适配成 {@link BskSink}。
 *
 * <p>扩展侧与 CLI 侧共用一个适配器：两条链路的帧出口语义完全一样
 * （单连接内有序写、关闭幂等），没有理由写两份。</p>
 *
 * <p>写操作用 {@code synchronized(session)} 串行化：Spring 的
 * {@code WebSocketSession} 不允许并发发送，而应答帧可能来自
 * 「扩展侧 WS 线程」与「CLI 侧队列线程」两个方向。</p>
 */
@Slf4j
public record BskWebSocketSink(WebSocketSession session) implements BskSink {

    @Override
    public void send(String json) throws Exception {
        synchronized (session) {
            session.sendMessage(new TextMessage(json));
        }
    }

    @Override
    public void close() {
        closeQuietly(session, CloseStatus.NORMAL);
    }

    /** 关闭会话并吞掉异常（关闭失败不影响主流程语义）。 */
    static void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException e) {
            log.debug("bsk session close failed: {}", e.getMessage());
        }
    }
}
