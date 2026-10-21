package org.wwz.ai.trigger.http.browser.bsk;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 锁定 {@link BskRelayWebSocketHandler} 的帧路由：bsk 帧闭环，
 * 绝不进入 {@code BrowserRelayFrameMapper}（会把 bsk 扁平响应帧误读成
 * Reactor 命令结果）。旧帧委托给 legacy delegate。
 */
public class BskRelayWebSocketHandlerTest {

    private final FakeSession session = new FakeSession("sess-1");
    private final BskFrameRelay relay = new BskFrameRelay(
            new BskSessionQueue(), new BskHandshakeResponder(), 0);
    private final List<String> legacyFrames = new ArrayList<>();
    private final BskRelayWebSocketHandler handler =
            new BskRelayWebSocketHandler(relay, (wsSession, payload) -> legacyFrames.add(payload));

    @Test
    public void handshakeFrameIsAnsweredAndConnectionOnline() {
        handler.afterConnectionEstablished(session);
        handler.handleTextMessage(session, new TextMessage(handshakeJson("hs-1")));

        Assert.assertEquals(1, session.sent.size());
        Assert.assertTrue(session.sent.get(0).contains("browser-skill-daemon"));
        Assert.assertTrue(relay.isOnline("sess-1"));
        Assert.assertTrue(legacyFrames.isEmpty());
    }

    @Test
    public void nonHandshakeFirstFrameClosesConnection() {
        handler.afterConnectionEstablished(session);
        handler.handleTextMessage(session, new TextMessage(
                "{\"id\":\"1\",\"method\":\"tool.navigate\",\"params\":{}}"));

        Assert.assertTrue(session.closed);
        Assert.assertEquals(CloseStatus.POLICY_VIOLATION, session.closeStatus);
        Assert.assertFalse(relay.isOnline("sess-1"));
        Assert.assertTrue(legacyFrames.isEmpty());
    }

    @Test
    public void bskResponseIsRoutedNotMappedAsReactorResult() {
        handler.afterConnectionEstablished(session);
        handler.handleTextMessage(session, new TextMessage(handshakeJson("hs-1")));
        relay.dispatch("sess-1", "rpc-1", request("rpc-1"));

        handler.handleTextMessage(session, new TextMessage("{\"id\":\"rpc-1\",\"result\":{\"ok\":true}}"));

        Assert.assertFalse(relay.isPending("rpc-1"));
        Assert.assertTrue("bsk 响应绝不能落到 Reactor 旧路径", legacyFrames.isEmpty());
        Assert.assertFalse(session.closed);
    }

    @Test
    public void reactorLegacyFrameIsDelegated() {
        handler.afterConnectionEstablished(session);
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"hello\"}"));
        Assert.assertEquals(List.of("{\"type\":\"hello\"}"), legacyFrames);
    }

    @Test
    public void eventFrameIsAcceptedWithoutClosing() {
        handler.afterConnectionEstablished(session);
        handler.handleTextMessage(session, new TextMessage(handshakeJson("hs-1")));
        handler.handleTextMessage(session, new TextMessage("{\"event\":\"system.heartbeat\",\"payload\":{}}"));
        Assert.assertFalse(session.closed);
        Assert.assertTrue(legacyFrames.isEmpty());
    }

    @Test
    public void ambiguousFrameClosesWithBadData() {
        handler.afterConnectionEstablished(session);
        handler.handleTextMessage(session, new TextMessage(
                "{\"id\":\"1\",\"result\":{},\"error\":{\"code\":\"timeout\",\"message\":\"m\"}}"));
        Assert.assertEquals(CloseStatus.BAD_DATA, session.closeStatus);
    }

    @Test
    public void closingConnectionFailsInFlight() {
        handler.afterConnectionEstablished(session);
        handler.handleTextMessage(session, new TextMessage(handshakeJson("hs-1")));
        java.util.concurrent.CompletableFuture<com.alibaba.fastjson.JSONObject> pending =
                relay.dispatch("sess-1", "rpc-1", request("rpc-1"));

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        Assert.assertNotNull(pending);
        Assert.assertEquals("protocol_error", pending.join().getString("code"));
        Assert.assertFalse(relay.isOnline("sess-1"));
    }

    @Test
    public void noLegacyDelegateIgnoresLegacyFrames() {
        BskRelayWebSocketHandler solo = new BskRelayWebSocketHandler(relay);
        solo.afterConnectionEstablished(session);
        solo.handleTextMessage(session, new TextMessage("{\"type\":\"ping\"}"));
        Assert.assertFalse(session.closed);
    }

    @Test
    public void handlerLevelSubProtocolListStaysEmpty() {
        // 认证子协议里的 token 每条连接都不同，handler 级静态列表无法表达
        // 「回显本次请求的那个值」——真正的回显在 BskSubProtocolHandshakeHandler。
        // 这里若返回非空且与请求值不匹配，Spring 会拒绝该协议，反而拿不到回显。
        Assert.assertTrue(handler.getSubProtocols().isEmpty());
    }

    // ── 辅助 ─────────────────────────────────────────────────

    private static String handshakeJson(String id) {
        return "{\"id\":\"" + id + "\",\"method\":\"system.handshake\",\"params\":{"
                + "\"client\":\"browser-skill-extension\",\"version\":\"0.3.1\","
                + "\"protocol_version\":\"1.3\",\"instance_id\":\"inst-1\","
                + "\"browser\":{\"name\":\"chrome\",\"version\":\"131.0\"},"
                + "\"label\":\"Test\",\"min_compatible_protocol\":\"1.0\"}}";
    }

    private static com.alibaba.fastjson.JSONObject request(String id) {
        com.alibaba.fastjson.JSONObject frame = new com.alibaba.fastjson.JSONObject();
        frame.put("id", id);
        frame.put("method", "tool.navigate");
        return frame;
    }

    /** 最小 WebSocketSession 替身（只实现 handler 用到的部分）。 */
    private static final class FakeSession implements WebSocketSession {
        private final String id;
        private final Map<String, Object> attributes = new HashMap<>();
        private final List<String> sent = new ArrayList<>();
        private volatile boolean closed;
        private volatile CloseStatus closeStatus;

        private FakeSession(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return attributes;
        }

        @Override
        public void sendMessage(org.springframework.web.socket.WebSocketMessage<?> message) {
            sent.add(String.valueOf(message.getPayload()));
        }

        @Override
        public void close() {
            this.closed = true;
        }

        @Override
        public void close(CloseStatus status) {
            this.closed = true;
            this.closeStatus = status;
        }

        @Override
        public boolean isOpen() {
            return !closed;
        }

        // ── 以下方法 handler 不调用，返回默认值 ──
        @Override
        public java.net.URI getUri() {
            return java.net.URI.create("ws://localhost/api/agent/browser/bsk-relay");
        }

        @Override
        public org.springframework.http.HttpHeaders getHandshakeHeaders() {
            return new org.springframework.http.HttpHeaders();
        }

        @Override
        public java.security.Principal getPrincipal() {
            return null;
        }

        @Override
        public java.net.InetSocketAddress getLocalAddress() {
            return null;
        }

        @Override
        public java.net.InetSocketAddress getRemoteAddress() {
            return null;
        }

        @Override
        public String getAcceptedProtocol() {
            return null;
        }

        @Override
        public void setTextMessageSizeLimit(int messageSizeLimit) {
        }

        @Override
        public int getTextMessageSizeLimit() {
            return 0;
        }

        @Override
        public void setBinaryMessageSizeLimit(int messageSizeLimit) {
        }

        @Override
        public int getBinaryMessageSizeLimit() {
            return 0;
        }

        @Override
        public List<org.springframework.web.socket.WebSocketExtension> getExtensions() {
            return List.of();
        }
    }
}
