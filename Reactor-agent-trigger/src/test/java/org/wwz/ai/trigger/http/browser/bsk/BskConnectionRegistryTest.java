package org.wwz.ai.trigger.http.browser.bsk;

import com.alibaba.fastjson.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 锁定 bsk 链路的「浏览器是否已连接」口径。
 *
 * <p>在线判定 = 连接已登记 + 已完成 bsk 握手。握手未完成的连接不算在线——
 * bsk 协议规定首帧必须是 {@code system.handshake}，握手完成才代表扩展真的可用。</p>
 *
 * <p>连接表只有 {@link BskFrameRelay} 一份，{@link BskConnectionRegistry} 只是
 * 把它暴露成 domain 端口，所以这里的用例同时锁住「relay 记账」与「端口语义」。</p>
 */
public class BskConnectionRegistryTest {

    private final RecordingSink sink = new RecordingSink();
    private final BskFrameRelay relay = new BskFrameRelay(
            new BskSessionQueue(), new BskHandshakeResponder(), 0);
    private final BskConnectionRegistry registry = new BskConnectionRegistry(relay);

    private static JSONObject handshakeFrame(String id) {
        JSONObject browser = new JSONObject();
        browser.put("name", "chrome");
        browser.put("version", "131.0");
        JSONObject params = new JSONObject();
        params.put("client", "browser-skill-extension");
        params.put("version", "0.3.1");
        params.put("protocol_version", "1.3");
        params.put("instance_id", "inst-1");
        params.put("browser", browser);
        params.put("label", "Test Chrome");
        JSONObject frame = new JSONObject();
        frame.put("id", id);
        frame.put("method", "system.handshake");
        frame.put("params", params);
        return frame;
    }

    @Test
    public void registeredButNotHandshakenIsNotOnline() {
        relay.registerSession("conn-1", null, "user-1", sink);
        Assert.assertFalse(registry.isOnline("user-1"));
    }

    @Test
    public void handshakeMakesConnectionOnline() {
        relay.registerSession("conn-1", null, "user-1", sink);
        Assert.assertTrue(relay.handleHandshake("conn-1", handshakeFrame("hs-1")));
        Assert.assertTrue(registry.isOnline("user-1"));
    }

    @Test
    public void closedConnectionIsNotOnline() {
        relay.registerSession("conn-1", null, "user-1", sink);
        relay.handleHandshake("conn-1", handshakeFrame("hs-1"));
        Assert.assertTrue(registry.isOnline("user-1"));

        relay.handleDisconnect("conn-1");
        Assert.assertFalse(registry.isOnline("user-1"));
    }

    @Test
    public void disconnectRemovesEveryConnectionOfTheUser() {
        RecordingSink other = new RecordingSink();
        relay.registerSession("conn-1", null, "user-1", sink);
        relay.handleHandshake("conn-1", handshakeFrame("hs-1"));
        relay.registerSession("conn-2", null, "user-1", other);
        relay.handleHandshake("conn-2", handshakeFrame("hs-2"));
        relay.registerSession("conn-3", null, "user-2", other);
        relay.handleHandshake("conn-3", handshakeFrame("hs-3"));
        Assert.assertTrue(registry.isOnline("user-1"));

        registry.disconnect("user-1");

        Assert.assertFalse(registry.isOnline("user-1"));
        Assert.assertTrue(sink.closed);
        Assert.assertTrue(other.closed);
    }

    @Test
    public void disconnectLeavesOtherUsersAlone() {
        RecordingSink other = new RecordingSink();
        relay.registerSession("conn-1", null, "user-1", sink);
        relay.handleHandshake("conn-1", handshakeFrame("hs-1"));
        relay.registerSession("conn-2", null, "user-2", other);
        relay.handleHandshake("conn-2", handshakeFrame("hs-2"));

        registry.disconnect("user-1");

        Assert.assertFalse(registry.isOnline("user-1"));
        Assert.assertTrue(registry.isOnline("user-2"));
        Assert.assertTrue(sink.closed);
        Assert.assertFalse(other.closed);
    }

    @Test
    public void blankUserIdIsNeverOnline() {
        relay.registerSession("conn-1", null, null, sink);
        relay.handleHandshake("conn-1", handshakeFrame("hs-1"));

        Assert.assertFalse(registry.isOnline(""));
        Assert.assertFalse(registry.isOnline(null));
    }

    /** 记录写出的帧，并记住是否被关闭。 */
    private static final class RecordingSink implements BskSink {
        final List<String> frames = new ArrayList<>();
        volatile boolean closed;

        @Override
        public void send(String json) {
            frames.add(json);
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
