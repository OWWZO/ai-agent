package org.wwz.ai.trigger.http.browser.bsk;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.web.socket.WebSocketHandler;

import java.util.List;

/**
 * 锁定子协议回显行为。
 *
 * <p>Chromium 的 {@code ValidateSubProtocol()} 规定：客户端发了非空的
 * {@code Sec-WebSocket-Protocol} 而服务端一个都没选，握手判失败并当场 RST。
 * Rust daemon 同样回显（{@code crates/bsk-cli/src/daemon/remote/server.rs}），
 * 其测试也锁定了该行为。本类锁定 Java 侧的同等语义。</p>
 */
public class BskSubProtocolHandshakeHandlerTest {

    @Test
    public void echoesRequestedAuthSubprotocol() {
        Assert.assertEquals("bsk-auth.tok-1",
                select("bsk-auth.tok-1"));
    }

    @Test
    public void echoesRequestedBase64Variant() {
        Assert.assertEquals("bsk-auth64.abcd", select("bsk-auth64.abcd"));
    }

    @Test
    public void picksTheAuthSubprotocolAmongSeveral() {
        // Spring 的 WebSocketHttpHeaders.getSecWebSocketProtocol() 会把单个
        // 逗号分隔的头拆成多个元素，故这里按拆开后的形态传入。
        Assert.assertEquals("bsk-auth.tok-2", select("chat", "superchat", "bsk-auth.tok-2"));
    }

    @Test
    public void ignoresNonAuthSubprotocols() {
        Assert.assertNull(select("chat", "superchat"));
    }

    @Test
    public void noRequestedSubprotocolYieldsNull() {
        Assert.assertNull(select());
    }

    private String select(String... requested) {
        return BskSubProtocolHandshakeHandler.selectAuthSubprotocol(List.of(requested));
    }
}
