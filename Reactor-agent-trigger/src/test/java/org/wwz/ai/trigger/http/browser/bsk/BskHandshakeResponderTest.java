package org.wwz.ai.trigger.http.browser.bsk;

import com.alibaba.fastjson.JSONObject;
import org.junit.Assert;
import org.junit.Test;

/**
 * 锁定握手响应形状与 Rust daemon 的 handshake 回包一致
 * （{@code crates/bsk-cli/src/daemon/ws.rs}）。
 */
public class BskHandshakeResponderTest {

    private final BskHandshakeResponder responder = new BskHandshakeResponder(
            "0.3.2", "1.3", "1.0");

    private static JSONObject handshake(String protocolVersion, String minCompatible) {
        JSONObject browser = new JSONObject();
        browser.put("name", "chrome");
        browser.put("version", "131.0");
        JSONObject params = new JSONObject();
        params.put("client", "browser-skill-extension");
        params.put("version", "0.3.1");
        params.put("protocol_version", protocolVersion);
        params.put("instance_id", "inst-1");
        params.put("browser", browser);
        params.put("label", "Test Chrome");
        if (minCompatible != null) {
            params.put("min_compatible_protocol", minCompatible);
        }
        JSONObject frame = new JSONObject();
        frame.put("id", "hs-1");
        frame.put("method", "system.handshake");
        frame.put("params", params);
        return frame;
    }

    // ── 接受 ─────────────────────────────────────────────────

    @Test
    public void successResponseCarriesDaemonShape() {
        JSONObject response = responder.respond(handshake("1.3", "1.0"));
        Assert.assertNotNull(response);
        Assert.assertEquals("hs-1", response.getString("id"));
        Assert.assertNull(response.get("error"));
        JSONObject result = response.getJSONObject("result");
        Assert.assertEquals("browser-skill-daemon", result.getString("server"));
        Assert.assertEquals("0.3.2", result.getString("version"));
        Assert.assertEquals("1.3", result.getString("protocol_version"));
        Assert.assertEquals("1.0", result.getString("min_compatible_protocol"));
    }

    @Test
    public void skewIsAcceptedButDetectable() {
        JSONObject frame = handshake("1.1", "1.0");
        JSONObject response = responder.respond(frame);
        Assert.assertNotNull(response);
        Assert.assertTrue(response.containsKey("result"));
        Assert.assertFalse(response.containsKey("error"));
        Assert.assertTrue(responder.isSkew(frame));
    }

    @Test
    public void missingPeerFloorIsTolerated() {
        JSONObject response = responder.respond(handshake("1.3", null));
        Assert.assertNotNull(response);
        Assert.assertTrue(response.containsKey("result"));
        Assert.assertFalse(responder.isSkew(handshake("1.3", null)));
    }

    // ── 拒绝 ─────────────────────────────────────────────────

    @Test
    public void majorMismatchIsRejectedAsVersionTooOld() {
        JSONObject response = responder.respond(handshake("2.0", "1.0"));
        Assert.assertNotNull(response);
        Assert.assertEquals("hs-1", response.getString("id"));
        Assert.assertNull(response.get("result"));
        JSONObject error = response.getJSONObject("error");
        Assert.assertEquals("version_too_old", error.getString("code"));
        Assert.assertTrue(error.getString("message").contains("major mismatch"));
        JSONObject data = error.getJSONObject("data");
        Assert.assertEquals("2.0", data.getString("peer_protocol_version"));
        Assert.assertEquals("1.3", data.getString("daemon_protocol_version"));
        Assert.assertEquals("0.3.2", data.getString("daemon_version"));
        Assert.assertEquals("1.0", data.getString("min_compatible_protocol"));
    }

    @Test
    public void unparseablePeerProtocolIsRejected() {
        JSONObject response = responder.respond(handshake("oops", null));
        Assert.assertNotNull(response);
        Assert.assertEquals("version_too_old", response.getJSONObject("error").getString("code"));
    }

    @Test
    public void localBelowPeerFloorIsRejected() {
        JSONObject response = responder.respond(handshake("1.3", "1.5"));
        Assert.assertNotNull(response);
        Assert.assertEquals("version_too_old", response.getJSONObject("error").getString("code"));
        Assert.assertTrue(response.getJSONObject("error").getString("message")
                .contains("min_compatible_protocol"));
    }

    // ── 非法入参 ─────────────────────────────────────────────

    @Test
    public void missingIdOrParamsYieldsNull() {
        // daemon: invalid handshake → 直接踢连接，不回帧。
        Assert.assertNull(responder.respond(null));
        Assert.assertNull(responder.respond(new JSONObject()));
        JSONObject noParams = new JSONObject();
        noParams.put("id", "hs-1");
        Assert.assertNull(responder.respond(noParams));
        JSONObject noId = new JSONObject();
        noId.put("method", "system.handshake");
        noId.put("params", new JSONObject());
        Assert.assertNull(responder.respond(noId));
    }

    @Test
    public void isSkewHandlesGarbage() {
        Assert.assertFalse(responder.isSkew(null));
        Assert.assertFalse(responder.isSkew(new JSONObject()));
    }

    @Test
    public void defaultConstructorUsesGatewayConstants() {
        BskHandshakeResponder defaultResponder = new BskHandshakeResponder();
        JSONObject response = defaultResponder.respond(handshake("1.3", "1.0"));
        JSONObject result = response.getJSONObject("result");
        Assert.assertEquals(BskHandshakeGateway.PROTOCOL_VERSION, result.getString("protocol_version"));
        Assert.assertEquals(BskHandshakeGateway.MIN_COMPATIBLE_PROTOCOL,
                result.getString("min_compatible_protocol"));
    }
}
