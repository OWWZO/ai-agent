package org.wwz.ai.trigger.http.browser.bsk;

import com.alibaba.fastjson.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 锁定 {@link BskFrameRelay} 的状态机：首帧强制握手、响应路由、
 * 断连补发 {@code protocol_error} + {@code reason=extension_disconnected}。
 */
public class BskFrameRelayTest {

    private final RecordingSink sink = new RecordingSink();
    private final BskFrameRelay relay = new BskFrameRelay(
            new BskSessionQueue(), new BskHandshakeResponder(), 0);

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

    private static JSONObject toolRequest(String id) {
        return toolRequest(id, "tool.navigate");
    }

    private static JSONObject toolRequest(String id, String method) {
        JSONObject frame = new JSONObject();
        frame.put("id", id);
        frame.put("method", method);
        JSONObject params = new JSONObject();
        params.put("session_id", "s1");
        params.put("url", "https://example.test");
        frame.put("params", params);
        return frame;
    }

    // ── 握手 ─────────────────────────────────────────────────

    @Test
    public void handshakeIsAnsweredAndConnectionBecomesOnline() {
        relay.registerSession("conn-1", null, null, sink);
        Assert.assertFalse(relay.isOnline("conn-1"));
        Assert.assertTrue(relay.handleHandshake("conn-1", handshakeFrame("hs-1")));
        Assert.assertTrue(relay.isOnline("conn-1"));

        Assert.assertEquals(1, sink.frames.size());
        JSONObject sent = sink.lastFrame();
        Assert.assertEquals("hs-1", sent.getString("id"));
        Assert.assertEquals("browser-skill-daemon",
                sent.getJSONObject("result").getString("server"));
        // instance_id 已注册路由。
        Assert.assertEquals("conn-1", relay.connectionIdForInstance("inst-1"));
    }

    @Test
    public void secondHandshakeIsRejectedAndConnectionClosed() {
        relay.registerSession("conn-1", null, null, sink);
        Assert.assertTrue(relay.handleHandshake("conn-1", handshakeFrame("hs-1")));
        Assert.assertFalse(relay.handleHandshake("conn-1", handshakeFrame("hs-2")));
        // 最后一帧是 protocol_error，连接被关闭。
        JSONObject last = sink.lastFrame();
        Assert.assertEquals("hs-2", last.getString("id"));
        Assert.assertEquals("protocol_error", last.getJSONObject("error").getString("code"));
        Assert.assertTrue(sink.closed);
        Assert.assertFalse(relay.isOnline("conn-1"));
    }

    @Test
    public void handshakeRejectStillAnswersThenCloses() {
        relay.registerSession("conn-1", null, null, sink);
        JSONObject frame = handshakeFrame("hs-1");
        frame.getJSONObject("params").put("protocol_version", "2.0");
        Assert.assertFalse(relay.handleHandshake("conn-1", frame));
        Assert.assertEquals("version_too_old",
                sink.lastFrame().getJSONObject("error").getString("code"));
        Assert.assertTrue(sink.closed);
    }

    @Test
    public void malformedHandshakeClosesWithoutReply() {
        relay.registerSession("conn-1", null, null, sink);
        // 缺 params → daemon 的 invalid handshake 路径。
        JSONObject frame = new JSONObject();
        frame.put("id", "hs-1");
        frame.put("method", "system.handshake");
        Assert.assertFalse(relay.handleHandshake("conn-1", frame));
        Assert.assertTrue("无回帧", sink.frames.isEmpty());
        Assert.assertTrue(sink.closed);
    }

    @Test
    public void unknownConnectionHandshakeFails() {
        Assert.assertFalse(relay.handleHandshake("ghost", handshakeFrame("hs-1")));
    }

    // ── 请求与响应路由 ────────────────────────────────────────

    @Test
    public void dispatchSendsFrameAndRouteResponseResolvesIt() {
        relay.registerSession("conn-1", null, null, sink);
        relay.handleHandshake("conn-1", handshakeFrame("hs-1"));

        CompletableFuture<JSONObject> pending = relay.dispatch("conn-1", "rpc-1", toolRequest("rpc-1"));
        Assert.assertNotNull(pending);
        Assert.assertTrue(relay.isPending("rpc-1"));
        Assert.assertEquals(1, sink.frames.size() - 1); // 握手帧之外又发了 1 帧
        Assert.assertEquals(toolRequest("rpc-1").toJSONString(), sink.frames.get(1));

        JSONObject response = new JSONObject();
        response.put("id", "rpc-1");
        response.put("result", new JSONObject());
        Assert.assertTrue(relay.routeResponse("rpc-1", response));
        JSONObject done = pending.join();
        Assert.assertEquals("rpc-1", done.getString("id"));
        Assert.assertFalse(BskFrameRelay.isErrorFrame(done));
        Assert.assertFalse(relay.isPending("rpc-1"));
    }

    @Test
    public void extensionErrorResponseIsNormalizedToErrorBody() {
        relay.registerSession("conn-1", null, null, sink);
        relay.handleHandshake("conn-1", handshakeFrame("hs-1"));
        CompletableFuture<JSONObject> pending = relay.dispatch("conn-1", "rpc-e", toolRequest("rpc-e"));

        JSONObject error = new JSONObject();
        error.put("code", "timeout");
        error.put("message", "tool dispatch timed out");
        JSONObject response = new JSONObject();
        response.put("id", "rpc-e");
        response.put("error", error);
        relay.routeResponse("rpc-e", response);

        JSONObject done = pending.join();
        Assert.assertTrue(BskFrameRelay.isErrorFrame(done));
        Assert.assertEquals("timeout", done.getString("code"));
    }

    @Test
    public void dispatchFailsForUnregisteredOrUnhandshakedConnection() {
        Assert.assertNull(relay.dispatch("ghost", "rpc-1", toolRequest("rpc-1")));
        relay.registerSession("conn-1", null, null, sink);
        // 未握手不允许发请求（daemon 同样只在握手后转发）。
        Assert.assertNull(relay.dispatch("conn-1", "rpc-1", toolRequest("rpc-1")));
    }

    @Test
    public void duplicateRpcIdFailsImmediately() {
        relay.registerSession("conn-1", null, null, sink);
        relay.handleHandshake("conn-1", handshakeFrame("hs-1"));
        Assert.assertNotNull(relay.dispatch("conn-1", "rpc-1", toolRequest("rpc-1")));
        CompletableFuture<JSONObject> duplicate = relay.dispatch("conn-1", "rpc-1", toolRequest("rpc-1"));
        Assert.assertNotNull(duplicate);
        JSONObject error = duplicate.join();
        Assert.assertTrue(BskFrameRelay.isErrorFrame(error));
        Assert.assertEquals("protocol_error", error.getString("code"));
        // 原请求仍可正常完成。
        Assert.assertTrue(relay.routeResponse("rpc-1", new JSONObject()));
    }

    @Test
    public void responseForUnknownIdIsIgnored() {
        Assert.assertFalse(relay.routeResponse("missing", new JSONObject()));
        Assert.assertFalse(relay.routeResponse(null, new JSONObject()));
    }

    @Test
    public void sendFailureIsConvertedToProtocolError() {
        sink.failNextSends = true;
        relay.registerSession("conn-1", null, null, sink);
        // 握手发送失败 → 连接移除。
        Assert.assertFalse(relay.handleHandshake("conn-1", handshakeFrame("hs-1")));
        Assert.assertFalse(relay.isOnline("conn-1"));
    }

    // ── 断连 ─────────────────────────────────────────────────

    @Test
    public void disconnectCompletesAllInFlightWithExtensionDisconnectedReason() {
        relay.registerSession("conn-1", null, null, sink);
        relay.handleHandshake("conn-1", handshakeFrame("hs-1"));
        CompletableFuture<JSONObject> first = relay.dispatch("conn-1", "rpc-1", toolRequest("rpc-1"));
        CompletableFuture<JSONObject> second = relay.dispatch("conn-1", "rpc-2", toolRequest("rpc-2"));
        Assert.assertEquals(2, relay.pendingCount());

        relay.handleDisconnect("conn-1");

        // 立即完成，不挂起。
        JSONObject err1 = first.join();
        JSONObject err2 = second.join();
        for (JSONObject error : List.of(err1, err2)) {
            Assert.assertTrue(BskFrameRelay.isErrorFrame(error));
            Assert.assertEquals("protocol_error", error.getString("code"));
            Assert.assertEquals(BskFrameRelay.REASON_EXTENSION_DISCONNECTED,
                    error.getJSONObject("data").getString("reason"));
        }
        Assert.assertEquals(0, relay.pendingCount());
        Assert.assertNull(relay.connectionIdForInstance("inst-1"));
        Assert.assertTrue(sink.closed);
    }

    @Test
    public void disconnectIsIdempotentAndToleratesUnknownConnection() {
        relay.handleDisconnect("ghost");
        relay.registerSession("conn-1", null, null, sink);
        relay.handleDisconnect("conn-1");
        relay.handleDisconnect("conn-1");
        Assert.assertTrue(sink.closed);
    }

    @Test
    public void dispatchAfterDisconnectReturnsNull() {
        relay.registerSession("conn-1", null, null, sink);
        relay.handleHandshake("conn-1", handshakeFrame("hs-1"));
        relay.handleDisconnect("conn-1");
        Assert.assertNull(relay.dispatch("conn-1", "rpc-9", toolRequest("rpc-9")));
    }

    @Test
    public void pendingCallsDoNotLeakAcrossConnections() {
        RecordingSink sinkB = new RecordingSink();
        relay.registerSession("conn-1", null, null, sink);
        relay.registerSession("conn-2", null, null, sinkB);
        relay.handleHandshake("conn-1", handshakeFrame("hs-1"));
        relay.handleHandshake("conn-2", handshakeFrame("hs-1"));

        relay.dispatch("conn-1", "rpc-a", toolRequest("rpc-a"));
        relay.dispatch("conn-2", "rpc-b", toolRequest("rpc-b"));

        // 只有 conn-1 断开：conn-2 的在飞请求不受影响。
        relay.handleDisconnect("conn-1");
        Assert.assertEquals(1, relay.pendingCount());
        Assert.assertTrue(relay.isPending("rpc-b"));

        JSONObject response = new JSONObject();
        response.put("id", "rpc-b");
        response.put("result", "ok");
        Assert.assertTrue(relay.routeResponse("rpc-b", response));
    }

    @Test
    public void timeoutCompletesPendingCall() throws Exception {
        RecordingSink quietSink = new RecordingSink();
        BskFrameRelay timingRelay = new BskFrameRelay(
                new BskSessionQueue(), new BskHandshakeResponder(), 1);
        timingRelay.registerSession("conn-t", null, null, quietSink);
        timingRelay.handleHandshake("conn-t", handshakeFrame("hs-1"));
        CompletableFuture<JSONObject> pending =
                timingRelay.dispatch("conn-t", "rpc-slow", toolRequest("rpc-slow", "tool.click"));
        JSONObject frame = pending.get(3, TimeUnit.SECONDS);
        Assert.assertTrue(BskFrameRelay.isErrorFrame(frame));
        Assert.assertEquals("timeout", frame.getString("code"));
        Assert.assertFalse(timingRelay.isPending("rpc-slow"));
    }

    // ── 按方法计算的帧超时 ───────────────────────────────────

    /**
     * borrow 的等待是<b>用户</b>决定的：确认窗口默认 60s，而默认帧超时也是 60s，
     * 不单独放宽的话用户还没来得及点确认，帧就先被判死了。
     */
    @Test
    public void tabBorrowBudgetOutlivesTheUserConfirmationWindow() {
        BskFrameRelay relay60 = new BskFrameRelay(
                new BskSessionQueue(), new BskHandshakeResponder(), 60);

        // 默认 60s 确认 + 15s 移动/回滚 = 75s，再加 25s 网关余量。
        Assert.assertEquals(100, relay60.timeoutSecondsFor(borrowRequest(null)));
        // 显式 --timeout 90s。
        Assert.assertEquals(130, relay60.timeoutSecondsFor(borrowRequest(90_000)));
        // 参数坏了也不拒请求：回退到默认确认窗口。
        Assert.assertEquals(100, relay60.timeoutSecondsFor(borrowRequest(0)));
    }

    /**
     * 网关必须比<b>客户端</b>更耐等，否则客户端先放弃、网关后到点，请求会挂成孤儿。
     *
     * <p>客户端 borrow 的预算是「确认 60s + 移动 15s + 取消 2s + 传输 5s」；
     * {@code request-help} 是「300s + 15s」。这条断言锁死这个方向，防止以后
     * 两边各改各的又把它改反。</p>
     */
    @Test
    public void gatewayAlwaysOutlivesTheClientBudget() {
        BskFrameRelay relay60 = new BskFrameRelay(
                new BskSessionQueue(), new BskHandshakeResponder(), 60);

        Assert.assertTrue(relay60.timeoutSecondsFor(borrowRequest(60_000)) > 82);
        Assert.assertTrue(relay60.timeoutSecondsFor(
                methodRequest("tool.request_help", "timeout_ms", 300_000)) > 315);
        Assert.assertTrue(relay60.timeoutSecondsFor(
                methodRequest("tool.screenshot_full_page", null, null)) > 125);
    }

    /** 扩展自己握着截止时间的方法，传输层要多留 2s，否则会覆盖掉它产出的结果。 */
    @Test
    public void selfDeadlineMethodsGetGraceOnTopOfTheirOwnBudget() {
        BskFrameRelay relay60 = new BskFrameRelay(
                new BskSessionQueue(), new BskHandshakeResponder(), 60);

        // request-help：300s 预算 + 2s grace + 25s 网关余量。
        Assert.assertEquals(327, relay60.timeoutSecondsFor(
                methodRequest("tool.request_help", "timeout_ms", 300_000)));
        // 整页截图没给 timeout_ms 时按 120s + 2s grace 算。
        Assert.assertEquals(147, relay60.timeoutSecondsFor(
                methodRequest("tool.screenshot_full_page", null, null)));
        // 其余方法不超配置值，就不加余量：session-timeout-seconds 是权威开关。
        Assert.assertEquals(60, relay60.timeoutSecondsFor(
                methodRequest("tool.click", null, null)));
    }

    /** 配置成 0 表示「网关不设兜底」，任何方法都不能凭空造出一个截止时间。 */
    @Test
    public void disabledGatewayTimeoutStaysDisabledForEveryMethod() {
        BskFrameRelay unlimited = new BskFrameRelay(
                new BskSessionQueue(), new BskHandshakeResponder(), 0);

        Assert.assertEquals(0, unlimited.timeoutSecondsFor(borrowRequest(90_000)));
        Assert.assertEquals(0, unlimited.timeoutSecondsFor(
                methodRequest("tool.request_help", "timeout_ms", 300_000)));
    }

    private static String borrowRequest(Integer confirmationTimeoutMs) {
        JSONObject params = new JSONObject();
        params.put("session_id", "s1");
        params.put("tab_id", 7);
        if (confirmationTimeoutMs != null) {
            params.put("confirmation_timeout_ms", confirmationTimeoutMs);
        }
        JSONObject frame = new JSONObject();
        frame.put("id", "rpc-borrow");
        frame.put("method", "tool.tab_borrow");
        frame.put("params", params);
        return frame.toJSONString();
    }

    private static String methodRequest(String method, String key, Integer value) {
        JSONObject params = new JSONObject();
        params.put("session_id", "s1");
        if (key != null && value != null) {
            params.put(key, value);
        }
        JSONObject frame = new JSONObject();
        frame.put("id", "rpc-1");
        frame.put("method", method);
        frame.put("params", params);
        return frame.toJSONString();
    }

    // ── 测试替身 ─────────────────────────────────────────────

    /** 记录所有发送帧的 sink。 */
    private static final class RecordingSink implements BskSink {
        final List<String> frames = new ArrayList<>();
        volatile boolean closed;
        volatile boolean failNextSends;

        JSONObject lastFrame() {
            return JSONObject.parseObject(frames.get(frames.size() - 1));
        }

        @Override
        public void send(String json) {
            if (failNextSends) {
                throw new IllegalStateException("socket down");
            }
            frames.add(json);
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
