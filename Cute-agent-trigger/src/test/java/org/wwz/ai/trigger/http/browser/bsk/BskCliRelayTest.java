package org.wwz.ai.trigger.http.browser.bsk;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 锁定 CLI 侧网关的路由与转发（021）。
 *
 * <p>CLI 发 {@code tool.*} → 网关投给目标扩展 → 扩展的应答原样送回 CLI。
 * 目标解析顺序：{@code browser_id}（实例级）→ {@code user_id}（用户级）。</p>
 */
public class BskCliRelayTest {

    private final RecordingSink extensionSink = new RecordingSink();
    private final BskFrameRelay frameRelay = new BskFrameRelay(
            new BskSessionQueue(), new BskHandshakeResponder(), 0);
    private final BskCliRelay cliRelay = new BskCliRelay(frameRelay, 2);

    private static JSONObject handshakeFrame(String id, String instanceId) {
        JSONObject browser = new JSONObject();
        browser.put("name", "chrome");
        browser.put("version", "131.0");
        JSONObject params = new JSONObject();
        params.put("client", "browser-skill-extension");
        params.put("version", "0.3.1");
        params.put("protocol_version", "1.3");
        params.put("instance_id", instanceId);
        params.put("browser", browser);
        params.put("label", "Test Chrome");
        JSONObject frame = new JSONObject();
        frame.put("id", id);
        frame.put("method", "system.handshake");
        frame.put("params", params);
        return frame;
    }

    private static String toolRequest(String id, String sessionId) {
        JSONObject params = new JSONObject();
        params.put("session_id", sessionId);
        params.put("url", "https://example.test");
        JSONObject frame = new JSONObject();
        frame.put("id", id);
        frame.put("method", "tool.navigate");
        frame.put("params", params);
        return frame.toJSONString();
    }

    private BskCliConnection registerCli(String connectionId, String userId, String browserId) {
        BskCliConnection cli = new BskCliConnection(connectionId, userId, browserId, new RecordingSink());
        cliRelay.register(cli);
        return cli;
    }

    @Test
    public void requestIsForwardedToTheUsersExtensionAndAnswerIsReturned() throws Exception {
        frameRelay.registerSession("ext-1", null, "user-1", extensionSink);
        Assert.assertTrue(frameRelay.handleHandshake("ext-1", handshakeFrame("hs-1", "inst-1")));
        BskCliConnection cli = registerCli("cli-1", "user-1", null);

        // 模拟扩展：收到请求帧就回一个应答帧。
        Thread responder = new Thread(() -> {
            String request = awaitRequestFrame(extensionSink, 5000);
            if (request == null) {
                return;
            }
            JSONObject response = new JSONObject();
            response.put("id", JSONObject.parseObject(request).getString("id"));
            JSONObject result = new JSONObject();
            result.put("url", "https://example.test");
            response.put("result", result);
            frameRelay.routeResponse(response.getString("id"), response);
        });
        responder.start();

        JSONObject answer = cliRelay.forward(cli, toolRequest("rpc-1", "s1"));

        Assert.assertEquals("rpc-1", answer.getString("id"));
        Assert.assertEquals("https://example.test", answer.getJSONObject("result").getString("url"));
        // 转发的是原文：扩展侧收到的帧里带着 CLI 发的 session_id。
        JSONObject forwarded = JSONObject.parseObject(
                extensionSink.frames.get(extensionSink.frames.size() - 1));
        Assert.assertEquals("tool.navigate", forwarded.getString("method"));
        Assert.assertEquals("s1", forwarded.getJSONObject("params").getString("session_id"));
        responder.join(5000);
    }

    /** 等扩展侧收到带 method 的请求帧（握手应答帧不算）。 */
    private static String awaitRequestFrame(RecordingSink sink, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            synchronized (sink.frames) {
                for (String frame : sink.frames) {
                    if (frame.contains("\"method\"")) {
                        return frame;
                    }
                }
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    @Test
    public void requestIsRoutedByBrowserInstanceId() {
        RecordingSink first = new RecordingSink();
        frameRelay.registerSession("ext-1", null, "user-1", first);
        frameRelay.handleHandshake("ext-1", handshakeFrame("hs-1", "inst-1"));
        frameRelay.registerSession("ext-2", null, "user-1", extensionSink);
        frameRelay.handleHandshake("ext-2", handshakeFrame("hs-2", "inst-2"));

        BskCliConnection cli = registerCli("cli-1", "user-1", "inst-2");
        Assert.assertEquals("ext-2", frameRelay.connectionIdForInstance("inst-2"));

        // 没有扩展在线响应，这里只验证路由：转发目标是 inst-2 那条连接。
        cliRelay.forward(cli, toolRequest("rpc-1", "s1"));
        Assert.assertNotNull(awaitRequestFrame(extensionSink, 2000));
        // inst-1 那条连接只收到过握手应答，没收到转发过去的请求帧。
        Assert.assertNull(awaitRequestFrame(first, 200));
    }

    @Test
    public void requestWithoutOnlineExtensionReturnsExtensionDisconnected() {
        BskCliConnection cli = registerCli("cli-1", "user-1", null);

        JSONObject answer = cliRelay.forward(cli, toolRequest("rpc-1", "s1"));

        Assert.assertEquals("rpc-1", answer.getString("id"));
        JSONObject error = answer.getJSONObject("error");
        Assert.assertNotNull(error);
        Assert.assertEquals("protocol_error", error.getString("code"));
        Assert.assertEquals("extension_disconnected",
                error.getJSONObject("data").getString("reason"));
    }

    @Test
    public void sessionKeyComesFromSessionIdAndFallsBackToConnection() {
        BskCliConnection cli = registerCli("cli-1", "user-1", null);
        Assert.assertEquals("s1", cliRelay.sessionKeyOf(cli, toolRequest("rpc-1", "s1")));

        JSONObject noSession = new JSONObject();
        noSession.put("id", "rpc-2");
        noSession.put("method", "tool.session_start");
        Assert.assertEquals("cli:cli-1", cliRelay.sessionKeyOf(cli, noSession.toJSONString()));
    }

    @Test
    public void cliDisconnectEndsItsInflightRequests() throws Exception {
        frameRelay.registerSession("ext-1", null, "user-1", extensionSink);
        frameRelay.handleHandshake("ext-1", handshakeFrame("hs-1", "inst-1"));
        BskCliConnection cli = registerCli("cli-1", "user-1", null);

        Thread forwarder = new Thread(() -> {
            cliRelay.forward(cli, toolRequest("rpc-1", "s1"));
        });
        forwarder.start();
        // 等转发真正发出（扩展侧收到请求帧）再断 CLI。
        Assert.assertNotNull(awaitRequestFrame(extensionSink, 5000));

        cliRelay.unregister("cli-1");
        forwarder.join(5000);

        // 在飞请求被结束，而不是挂到超时。
        Assert.assertFalse(frameRelay.isPending("rpc-1"));
    }

    @Test
    public void browserListIsAnsweredByTheGatewayNotForwardedToTheExtension() {
        frameRelay.registerSession("ext-1", null, "user-1", extensionSink);
        frameRelay.handleHandshake("ext-1", handshakeFrame("hs-1", "inst-1"));
        BskCliConnection cli = registerCli("cli-1", "user-1", null);

        JSONObject request = new JSONObject();
        request.put("id", "rpc-list-1");
        request.put("method", "browser.list");
        JSONObject answer = cliRelay.forward(cli, request.toJSONString());

        // browser.list 是 daemon 层方法：扩展不实现，转过去只会得到 unknown_method。
        Assert.assertEquals("rpc-list-1", answer.getString("id"));
        Assert.assertNull(answer.get("error"));
        JSONObject browsers = answer.getJSONObject("result").getJSONArray("browsers").getJSONObject(0);
        Assert.assertEquals("inst-1", browsers.getString("instance_id"));
        Assert.assertEquals("chrome", browsers.getString("browser_name"));
        Assert.assertEquals("131.0", browsers.getString("browser_version"));
        Assert.assertEquals("0.3.1", browsers.getString("extension_version"));
        Assert.assertEquals("Test Chrome", browsers.getString("label"));
        // 扩展根本没收到这条请求。
        Assert.assertNull(awaitRequestFrame(extensionSink, 200));
    }

    @Test
    public void sessionListIsAnsweredByTheGatewayNotForwardedToTheExtension() {
        frameRelay.registerSession("ext-1", null, "user-1", extensionSink);
        frameRelay.handleHandshake("ext-1", handshakeFrame("hs-1", "inst-1"));
        BskCliConnection cli = registerCli("cli-1", "user-1", null);

        JSONObject request = new JSONObject();
        request.put("id", "rpc-sessions-1");
        request.put("method", "session.list");
        JSONObject answer = cliRelay.forward(cli, request.toJSONString());

        // session.list 也是 daemon 层方法：Rust 由 SessionRegistry 回答，
        // 扩展 dispatcher 里没有这个 case，转过去只会得到 unknown_method。
        Assert.assertEquals("rpc-sessions-1", answer.getString("id"));
        Assert.assertNull(answer.get("error"));
        // 形状对齐 Rust 的 SessionListResult：结果对象里是 sessions 数组。
        // 没有登记过会话时是空数组，而不是按在线浏览器编造的占位条目。
        Assert.assertEquals(0,
                answer.getJSONObject("result").getJSONArray("sessions").size());
        Assert.assertNull(awaitRequestFrame(extensionSink, 200));
    }

    @Test
    public void sessionListReportsSessionsTheExtensionConfirmed() throws Exception {
        frameRelay.registerSession("ext-1", null, "user-1", extensionSink);
        frameRelay.handleHandshake("ext-1", handshakeFrame("hs-1", "inst-1"));
        BskCliConnection cli = registerCli("cli-1", "user-1", null);
        answerExtensionWith(extensionSink, "tool.session_start", "{\"agent_window_id\":42}");

        JSONObject start = new JSONObject();
        start.put("id", "rpc-start-1");
        start.put("method", "tool.session_start");
        start.put("params", new JSONObject(java.util.Map.of("session_id", "s1")));
        cliRelay.forward(cli, start.toJSONString());

        JSONObject list = new JSONObject();
        list.put("id", "rpc-sessions-2");
        list.put("method", "session.list");
        JSONObject answer = cliRelay.forward(cli, list.toJSONString());

        JSONObject entry = answer.getJSONObject("result").getJSONArray("sessions").getJSONObject(0);
        Assert.assertEquals("s1", entry.getString("session_id"));
        Assert.assertEquals("inst-1", entry.getString("browser_instance_id"));
        Assert.assertEquals(42, entry.getLongValue("agent_window_id"));
        // session_count 是真实值，不是硬编码 0。
        JSONObject browser = cliRelay.forward(cli, browserListRequest("rpc-browsers-1"))
                .getJSONObject("result").getJSONArray("browsers").getJSONObject(0);
        Assert.assertEquals(1, browser.getIntValue("session_count"));
    }

    @Test
    public void sessionListDropsSessionsAfterStop() throws Exception {
        frameRelay.registerSession("ext-1", null, "user-1", extensionSink);
        frameRelay.handleHandshake("ext-1", handshakeFrame("hs-1", "inst-1"));
        BskCliConnection cli = registerCli("cli-1", "user-1", null);
        answerExtensionWith(extensionSink, "tool.session_start", "{\"agent_window_id\":42}");

        JSONObject start = new JSONObject();
        start.put("id", "rpc-start-2");
        start.put("method", "tool.session_start");
        start.put("params", new JSONObject(java.util.Map.of("session_id", "s1")));
        cliRelay.forward(cli, start.toJSONString());

        answerExtensionWith(extensionSink, "tool.session_stop", "{}");
        JSONObject stop = new JSONObject();
        stop.put("id", "rpc-stop-1");
        stop.put("method", "tool.session_stop");
        stop.put("params", new JSONObject(java.util.Map.of("session_id", "s1")));
        cliRelay.forward(cli, stop.toJSONString());

        JSONObject list = new JSONObject();
        list.put("id", "rpc-sessions-3");
        list.put("method", "session.list");
        Assert.assertEquals(0,
                cliRelay.forward(cli, list.toJSONString())
                        .getJSONObject("result").getJSONArray("sessions").size());
    }

    @Test
    public void extensionDisconnectDropsItsSessions() throws Exception {
        frameRelay.registerSession("ext-1", null, "user-1", extensionSink);
        frameRelay.handleHandshake("ext-1", handshakeFrame("hs-1", "inst-1"));
        BskCliConnection cli = registerCli("cli-1", "user-1", null);
        answerExtensionWith(extensionSink, "tool.session_start", "{\"agent_window_id\":42}");

        JSONObject start = new JSONObject();
        start.put("id", "rpc-start-3");
        start.put("method", "tool.session_start");
        start.put("params", new JSONObject(java.util.Map.of("session_id", "s1")));
        cliRelay.forward(cli, start.toJSONString());

        // 扩展断连时它内存里的会话全部消失，网关必须同步清掉。
        frameRelay.handleDisconnect("ext-1");

        JSONObject list = new JSONObject();
        list.put("id", "rpc-sessions-4");
        list.put("method", "session.list");
        Assert.assertEquals(0,
                cliRelay.forward(cli, list.toJSONString())
                        .getJSONObject("result").getJSONArray("sessions").size());
    }

    /**
     * 让 mock 扩展连续应答 {@code count} 条指定方法的请求帧。
     *
     * <p>按帧 id 去重，避免同一个 responder 重复应答同一条请求。</p>
     */
    private Thread answerExtensionFor(RecordingSink sink, String method, String resultJson, int count) {
        int startIndex = sink.frameCount();
        Thread responder = new Thread(() -> {
            java.util.Set<String> answered = new java.util.HashSet<>();
            long deadline = System.currentTimeMillis() + 5000;
            while (answered.size() < count && System.currentTimeMillis() < deadline) {
                synchronized (sink.frames) {
                    for (int i = startIndex; i < sink.frames.size(); i++) {
                        String frame = sink.frames.get(i);
                        if (!frame.contains("\"method\":\"" + method + "\"")) continue;
                        String id = JSONObject.parseObject(frame).getString("id");
                        if (!answered.add(id)) continue;
                        JSONObject response = new JSONObject();
                        response.put("id", id);
                        response.put("result", JSONObject.parseObject(resultJson));
                        frameRelay.routeResponse(id, response);
                    }
                }
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        });
        responder.start();
        return responder;
    }

    /**
     * 让 mock 扩展对指定方法回一个固定的成功结果。
     *
     * <p><b>不 join</b>：responder 必须在 {@code forward()} <b>之前</b>启动（它要等
     * 请求帧出现），而 {@code forward()} 是阻塞的。join 会把「等请求」和「发请求」
     * 串成死锁。</p>
     *
     * <p>只认<b>本次调用之后</b>新增的帧：{@code RecordingSink.frames} 是累积的，
     * 从 0 开始扫描会匹配到上一轮的旧帧，用旧 id 应答，当前请求就永远等不到。</p>
     */
    private Thread answerExtensionWith(RecordingSink sink, String method, String resultJson) {
        int startIndex = sink.frameCount();
        Thread responder = new Thread(() -> {
            String request = awaitMethodFrameFrom(sink, startIndex, method, 5000);
            if (request == null) {
                return;
            }
            JSONObject response = new JSONObject();
            response.put("id", JSONObject.parseObject(request).getString("id"));
            response.put("result", JSONObject.parseObject(resultJson));
            frameRelay.routeResponse(response.getString("id"), response);
        });
        responder.start();
        return responder;
    }

    /** 等扩展侧收到指定 method 的请求帧（从 {@code startIndex} 之后开始找）。 */
    private static String awaitMethodFrameFrom(RecordingSink sink, int startIndex,
                                               String method, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            synchronized (sink.frames) {
                for (int i = startIndex; i < sink.frames.size(); i++) {
                    if (sink.frames.get(i).contains("\"method\":\"" + method + "\"")) {
                        return sink.frames.get(i);
                    }
                }
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    /** 等扩展侧收到指定 method 的请求帧。 */
    private static String awaitMethodFrame(RecordingSink sink, String method, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            synchronized (sink.frames) {
                for (String frame : sink.frames) {
                    if (frame.contains("\"method\":\"" + method + "\"")) {
                        return frame;
                    }
                }
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private static String browserListRequest(String id) {
        JSONObject request = new JSONObject();
        request.put("id", id);
        request.put("method", "browser.list");
        return request.toJSONString();
    }

    @Test
    public void waitMsIsSleptLocallyAndReturnsTheWaitAccuracy() {
        BskCliConnection cli = registerCli("cli-1", "user-1", null);

        JSONObject request = new JSONObject();
        request.put("id", "rpc-wait-1");
        request.put("method", "tool.wait_ms");
        request.put("params", new JSONObject(java.util.Map.of("duration_ms", 120)));
        long started = System.currentTimeMillis();
        JSONObject answer = cliRelay.forward(cli, request.toJSONString());

        // tool.wait_ms 是 daemon 自己 sleep 的，不经扩展，也不需要 session。
        Assert.assertEquals("rpc-wait-1", answer.getString("id"));
        Assert.assertNull(answer.get("error"));
        Assert.assertEquals(120, answer.getJSONObject("result").getLongValue("waited_ms"));
        Assert.assertTrue(System.currentTimeMillis() - started >= 100);
    }

    @Test
    public void waitMsRejectsDurationsBeyondTheDaemonCap() {
        BskCliConnection cli = registerCli("cli-1", "user-1", null);

        JSONObject request = new JSONObject();
        request.put("id", "rpc-wait-2");
        request.put("method", "tool.wait_ms");
        request.put("params", new JSONObject(java.util.Map.of("duration_ms", 5L * 60 * 1000 + 1)));
        JSONObject answer = cliRelay.forward(cli, request.toJSONString());

        // 对齐 Rust daemon 的 MAX_WAIT_MS（5 分钟）：超限要在客户端侧就拒绝。
        Assert.assertEquals("invalid_params", answer.getJSONObject("error").getString("code"));
    }

    @Test
    public void waitMsRejectsAMissingDuration() {
        BskCliConnection cli = registerCli("cli-1", "user-1", null);

        JSONObject request = new JSONObject();
        request.put("id", "rpc-wait-3");
        request.put("method", "tool.wait_ms");
        JSONObject answer = cliRelay.forward(cli, request.toJSONString());

        Assert.assertEquals("invalid_params", answer.getJSONObject("error").getString("code"));
    }

    @Test
    public void cancelForwardsToTheExtensionConnectionCarryingTheRequest() throws Exception {
        frameRelay.registerSession("ext-1", null, "user-1", extensionSink);
        frameRelay.handleHandshake("ext-1", handshakeFrame("hs-1", "inst-1"));
        BskCliConnection cli = registerCli("cli-1", "user-1", null);

        // 发一条会挂住的请求（扩展不应答），再用 cancel 打它。
        Thread forwarder = new Thread(() -> cliRelay.forward(cli, toolRequest("rpc-long", "s1")));
        forwarder.start();
        Assert.assertNotNull(awaitMethodFrame(extensionSink, "tool.navigate", 5000));

        JSONObject cancel = new JSONObject();
        cancel.put("id", "rpc-cancel-1");
        cancel.put("method", "cancel");
        cancel.put("params", new JSONObject(java.util.Map.of("rpc_id", "rpc-long")));
        JSONObject answer = cliRelay.forward(cli, cancel.toJSONString());

        Assert.assertEquals("rpc-cancel-1", answer.getString("id"));
        Assert.assertTrue(answer.getJSONObject("result").getBooleanValue("cancelled"));
        // 取消帧真的投到了扩展那条连接上，rpc_id 指向被取消的请求。
        String cancelFrame = awaitMethodFrame(extensionSink, "cancel", 2000);
        Assert.assertNotNull(cancelFrame);
        Assert.assertEquals("rpc-long",
                JSONObject.parseObject(cancelFrame).getJSONObject("params").getString("rpc_id"));

        // 收尾：结束挂住的请求，避免测试线程泄漏。
        frameRelay.failPending("rpc-long", "cancelled", "test cleanup", null);
        forwarder.join(5000);
    }

    @Test
    public void cancelForAnUnknownRpcReportsFalse() {
        BskCliConnection cli = registerCli("cli-1", "user-1", null);

        JSONObject cancel = new JSONObject();
        cancel.put("id", "rpc-cancel-2");
        cancel.put("method", "cancel");
        cancel.put("params", new JSONObject(java.util.Map.of("rpc_id", "never-sent")));
        JSONObject answer = cliRelay.forward(cli, cancel.toJSONString());

        // 没命中在飞表就如实回 false，不编造成功。
        Assert.assertFalse(answer.getJSONObject("result").getBooleanValue("cancelled"));
    }

    @Test
    public void cancelWithoutRpcIdIsRejected() {
        BskCliConnection cli = registerCli("cli-1", "user-1", null);

        JSONObject cancel = new JSONObject();
        cancel.put("id", "rpc-cancel-3");
        cancel.put("method", "cancel");
        JSONObject answer = cliRelay.forward(cli, cancel.toJSONString());

        Assert.assertEquals("invalid_params", answer.getJSONObject("error").getString("code"));
    }

    @Test
    public void sessionStopAllStopsEveryTrackedSession() throws Exception {
        frameRelay.registerSession("ext-1", null, "user-1", extensionSink);
        frameRelay.handleHandshake("ext-1", handshakeFrame("hs-1", "inst-1"));
        BskCliConnection cli = registerCli("cli-1", "user-1", null);

        // 先建两个会话。
        for (String sessionId : List.of("s1", "s2")) {
            answerExtensionWith(extensionSink, "tool.session_start", "{\"agent_window_id\":1}");
            JSONObject start = new JSONObject();
            start.put("id", "start-" + sessionId);
            start.put("method", "tool.session_start");
            start.put("params", new JSONObject(java.util.Map.of("session_id", sessionId)));
            cliRelay.forward(cli, start.toJSONString());
        }
        Assert.assertEquals(2, cliRelay.forward(cli, sessionListRequest("list-1"))
                .getJSONObject("result").getJSONArray("sessions").size());

        answerExtensionFor(extensionSink, "tool.session_stop", "{}", 2);
        JSONObject stopAll = new JSONObject();
        stopAll.put("id", "rpc-stop-all-1");
        stopAll.put("method", "session.stop_all");
        JSONObject answer = cliRelay.forward(cli, stopAll.toJSONString());

        // 形状对齐 Rust 的 CliSessionStopResult：{stopped:[...], failed:[...]}。
        Assert.assertEquals("rpc-stop-all-1", answer.getString("id"));
        Assert.assertNull(answer.get("error"));
        Assert.assertEquals(2, answer.getJSONObject("result").getJSONArray("stopped").size());
        Assert.assertEquals(0, answer.getJSONObject("result").getJSONArray("failed").size());
        // 会话表被清空，session_count 归零。
        Assert.assertEquals(0, cliRelay.forward(cli, sessionListRequest("list-2"))
                .getJSONObject("result").getJSONArray("sessions").size());
    }

    // ── 跨用户隔离 ───────────────────────────────────────────

    /**
     * 一台网关服务所有用户，所以 {@code session.list} 不能把别人的会话报出来。
     *
     * <p>Rust daemon 是单机单用户的，它的 {@code SessionRegistry} 天然只有自己
     * 的会话；我们替它回答这个方法，就必须自己补上这层过滤，否则 A 用户的
     * session id / Agent Window id / 交互策略会泄露给 B 用户。</p>
     */
    @Test
    public void sessionListOnlyReportsTheCallingUsersSessions() throws Exception {
        startSessionOn("ext-1", "inst-1", "user-1", extensionSink, "s1");
        startSessionOn("ext-2", "inst-2", "user-2", new RecordingSink(), "s2");

        BskCliConnection userOneCli = registerCli("cli-1", "user-1", null);
        JSONArray mine = cliRelay.forward(userOneCli, sessionListRequest("list-mine"))
                .getJSONObject("result").getJSONArray("sessions");
        Assert.assertEquals(1, mine.size());
        Assert.assertEquals("s1", mine.getJSONObject(0).getString("session_id"));

        BskCliConnection userTwoCli = registerCli("cli-2", "user-2", null);
        JSONArray theirs = cliRelay.forward(userTwoCli, sessionListRequest("list-theirs"))
                .getJSONObject("result").getJSONArray("sessions");
        Assert.assertEquals(1, theirs.size());
        Assert.assertEquals("s2", theirs.getJSONObject(0).getString("session_id"));
    }

    /**
     * {@code session.stop_all} 的「全部」只能是调用者自己的全部。
     *
     * <p>这一条是真正的破坏面：不加过滤时，任意已鉴权的 CLI 都停得掉
     * 其他用户浏览器上正在跑的会话。</p>
     */
    @Test
    public void sessionStopAllLeavesOtherUsersSessionsRunning() throws Exception {
        RecordingSink userTwoSink = new RecordingSink();
        startSessionOn("ext-1", "inst-1", "user-1", extensionSink, "s1");
        startSessionOn("ext-2", "inst-2", "user-2", userTwoSink, "s2");

        BskCliConnection userOneCli = registerCli("cli-1", "user-1", null);
        answerExtensionFor(extensionSink, "tool.session_stop", "{}", 1);
        JSONObject stopAll = new JSONObject();
        stopAll.put("id", "rpc-stop-all-2");
        stopAll.put("method", "session.stop_all");
        JSONObject answer = cliRelay.forward(userOneCli, stopAll.toJSONString());

        Assert.assertEquals(1, answer.getJSONObject("result").getJSONArray("stopped").size());
        Assert.assertEquals("s1", answer.getJSONObject("result")
                .getJSONArray("stopped").getJSONObject(0).getString("session_id"));
        // user-2 的会话既没被停，也没被转发到它那条连接上。
        Assert.assertEquals(0, cliRelay.forward(userOneCli, sessionListRequest("list-after"))
                .getJSONObject("result").getJSONArray("sessions").size());
        BskCliConnection userTwoCli = registerCli("cli-2", "user-2", null);
        JSONArray theirs = cliRelay.forward(userTwoCli, sessionListRequest("list-survived"))
                .getJSONObject("result").getJSONArray("sessions");
        Assert.assertEquals(1, theirs.size());
        Assert.assertEquals("s2", theirs.getJSONObject(0).getString("session_id"));
    }

    /** 在指定用户的扩展连接上建一个会话（走完整的 start 应答路径）。 */
    private void startSessionOn(String connectionId, String instanceId, String userId,
                                RecordingSink sink, String sessionId) throws Exception {
        frameRelay.registerSession(connectionId, null, userId, sink);
        frameRelay.handleHandshake(connectionId, handshakeFrame("hs-" + connectionId, instanceId));
        answerExtensionWith(sink, "tool.session_start", "{\"agent_window_id\":1}");
        BskCliConnection cli = registerCli("cli-for-" + sessionId, userId, null);
        JSONObject start = new JSONObject();
        start.put("id", "start-" + sessionId);
        start.put("method", "tool.session_start");
        start.put("params", new JSONObject(java.util.Map.of("session_id", sessionId)));
        cliRelay.forward(cli, start.toJSONString());
    }

    private static String sessionListRequest(String id) {
        JSONObject request = new JSONObject();
        request.put("id", id);
        request.put("method", "session.list");
        return request.toJSONString();
    }

    /** 记录写出的帧。 */
    private static final class RecordingSink implements BskSink {
        final List<String> frames = new ArrayList<>();

        /** 当前帧数，用来划定「只看这之后的新帧」。 */
        int frameCount() {
            synchronized (frames) {
                return frames.size();
            }
        }

        @Override
        public void send(String json) {
            synchronized (frames) {
                frames.add(json);
            }
        }

        @Override
        public void close() {
        }
    }
}
