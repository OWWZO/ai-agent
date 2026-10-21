package org.wwz.ai.trigger.http.browser.bsk;

import org.junit.Assert;
import org.junit.Test;

/**
 * 锁定 {@link BskFrameRouter} 的分类规则与 Rust
 * {@code FrameVisitor::visit_map} 一致。
 */
public class BskFrameRouterTest {

    // ── 请求帧 ───────────────────────────────────────────────

    @Test
    public void handshakeRequestIsClassified() {
        Assert.assertEquals(BskFrameKind.BSK_HANDSHAKE, BskFrameRouter.classify(
                "{\"id\":\"hs-1\",\"method\":\"system.handshake\",\"params\":{\"protocol_version\":\"1.3\"}}"));
    }

    @Test
    public void toolRequestIsClassifiedAsRequestShape() {
        // 请求帧统一归 BSK_HANDSHAKE（请求形状）；握手与否由 methodOf 区分。
        Assert.assertEquals(BskFrameKind.BSK_HANDSHAKE, BskFrameRouter.classify(
                "{\"id\":\"1\",\"method\":\"tool.navigate\",\"params\":{\"session_id\":\"s\"}}"));
        Assert.assertEquals("tool.navigate", BskFrameRouter.methodOf(
                "{\"id\":\"1\",\"method\":\"tool.navigate\"}"));
        Assert.assertEquals("system.handshake", BskFrameRouter.methodOf(
                "{\"id\":\"hs\",\"method\":\"system.handshake\"}"));
    }

    @Test
    public void requestWithoutIdIsUnknown() {
        // Rust: RequestFrame 缺 id → missing_field("id") 报错。
        Assert.assertEquals(BskFrameKind.UNKNOWN, BskFrameRouter.classify(
                "{\"method\":\"tool.navigate\"}"));
    }

    @Test
    public void requestWithNonStringIdIsUnknown() {
        // RpcId 是 String；数字 id 不在 Rust 接受集合内。
        Assert.assertEquals(BskFrameKind.UNKNOWN, BskFrameRouter.classify(
                "{\"id\":1,\"method\":\"tool.navigate\"}"));
    }

    // ── 响应帧 ───────────────────────────────────────────────

    @Test
    public void flatResultResponseIsClassified() {
        Assert.assertEquals(BskFrameKind.BSK_RESPONSE, BskFrameRouter.classify(
                "{\"id\":\"1\",\"result\":{\"tab_id\":3}}"));
    }

    @Test
    public void explicitNullResultIsValidOk() {
        // frame.rs: {"id":"rpc-1","result":null} 解码为 Ok(null)。
        Assert.assertEquals(BskFrameKind.BSK_RESPONSE, BskFrameRouter.classify(
                "{\"id\":\"1\",\"result\":null}"));
    }

    @Test
    public void flatErrorResponseIsClassified() {
        Assert.assertEquals(BskFrameKind.BSK_RESPONSE, BskFrameRouter.classify(
                "{\"id\":\"1\",\"error\":{\"code\":\"timeout\",\"message\":\"late\"}}"));
    }

    @Test
    public void bothResultAndErrorIsRejectedLikeRust() {
        // Rust: (Some, Some) → AmbiguousResponse。
        Assert.assertEquals(BskFrameKind.UNKNOWN, BskFrameRouter.classify(
                "{\"id\":\"1\",\"result\":{},\"error\":{\"code\":\"timeout\",\"message\":\"m\"}}"));
    }

    @Test
    public void neitherResultNorErrorIsRejectedLikeRust() {
        // Rust: (None, None) → InvalidFrame("expected result or error")。
        Assert.assertEquals(BskFrameKind.UNKNOWN, BskFrameRouter.classify(
                "{\"id\":\"1\"}"));
    }

    @Test
    public void responseWithoutIdIsUnknown() {
        Assert.assertEquals(BskFrameKind.UNKNOWN, BskFrameRouter.classify(
                "{\"result\":{}}"));
    }

    // ── 事件帧 ───────────────────────────────────────────────

    @Test
    public void heartbeatEventIsClassified() {
        Assert.assertEquals(BskFrameKind.BSK_EVENT, BskFrameRouter.classify(
                "{\"event\":\"system.heartbeat\",\"payload\":{}}"));
    }

    @Test
    public void eventWithDefaultPayloadIsClassified() {
        // Rust: payload 缺省 → 空对象。
        Assert.assertEquals(BskFrameKind.BSK_EVENT, BskFrameRouter.classify(
                "{\"event\":\"browser.connected\"}"));
    }

    @Test
    public void eventWithNonStringValueIsUnknown() {
        // EventKind 是枚举字符串；数字 event 不是合法帧。
        Assert.assertEquals(BskFrameKind.UNKNOWN, BskFrameRouter.classify(
                "{\"event\":42}"));
    }

    // ── Reactor 旧帧 ─────────────────────────────────────────

    @Test
    public void reactorHelloIsLegacy() {
        Assert.assertEquals(BskFrameKind.REACTOR_LEGACY, BskFrameRouter.classify(
                "{\"type\":\"hello\"}"));
    }

    @Test
    public void reactorPingIsLegacy() {
        Assert.assertEquals(BskFrameKind.REACTOR_LEGACY, BskFrameRouter.classify(
                "{\"type\":\"ping\"}"));
    }

    @Test
    public void reactorCommandResultIsLegacy() {
        // Reactor 命令结果：{id, ok, data} —— 没有 result/error 键，落到旧协议。
        Assert.assertEquals(BskFrameKind.REACTOR_LEGACY, BskFrameRouter.classify(
                "{\"id\":\"rpc-1\",\"ok\":true,\"data\":\"x\"}"));
    }

    @Test
    public void bareIdFrameIsUnknown() {
        // 只有 id、既无 result/error 也无 Reactor 判别键（type/ok）：
        // Rust 侧是 InvalidFrame("expected result or error")，按畸形 bsk 拒绝。
        Assert.assertEquals(BskFrameKind.UNKNOWN, BskFrameRouter.classify(
                "{\"id\":\"rpc-1\"}"));
    }

    // ── 边界 ─────────────────────────────────────────────────

    @Test
    public void invalidJsonIsUnknown() {
        Assert.assertEquals(BskFrameKind.UNKNOWN, BskFrameRouter.classify("not json"));
        Assert.assertEquals(BskFrameKind.UNKNOWN, BskFrameRouter.classify(""));
        Assert.assertEquals(BskFrameKind.UNKNOWN, BskFrameRouter.classify(null));
        Assert.assertEquals(BskFrameKind.UNKNOWN, BskFrameRouter.classify("   "));
    }

    @Test
    public void jsonArrayIsUnknown() {
        Assert.assertEquals(BskFrameKind.UNKNOWN, BskFrameRouter.classify("[1,2]"));
    }

    @Test
    public void methodWithBlankStringIsUnknown() {
        Assert.assertEquals(BskFrameKind.UNKNOWN, BskFrameRouter.classify(
                "{\"id\":\"1\",\"method\":\"\"}"));
    }

    @Test
    public void isBskMatchesTheThreeBskKinds() {
        Assert.assertTrue(BskFrameRouter.isBsk("{\"id\":\"1\",\"method\":\"system.handshake\"}"));
        Assert.assertTrue(BskFrameRouter.isBsk("{\"id\":\"1\",\"result\":null}"));
        Assert.assertTrue(BskFrameRouter.isBsk("{\"event\":\"system.heartbeat\"}"));
        Assert.assertFalse(BskFrameRouter.isBsk("{\"type\":\"hello\"}"));
        Assert.assertFalse(BskFrameRouter.isBsk("garbage"));
    }

    @Test
    public void duplicateTopLevelKeysUseFastjsonLastWin() {
        // Rust 会拒绝重复字段；fastjson 取后值。帧仍可分类，语义由调用方兜底。
        Assert.assertEquals(BskFrameKind.BSK_RESPONSE, BskFrameRouter.classify(
                "{\"id\":\"1\",\"error\":{\"code\":\"timeout\",\"message\":\"m\"},\"error\":{\"code\":\"timeout\",\"message\":\"m2\"}}"));
    }

    @Test
    public void unknownKeysAreIgnoredLikeRust() {
        // Rust: IgnoredAny —— 未知顶层键不影响分类。
        Assert.assertEquals(BskFrameKind.BSK_RESPONSE, BskFrameRouter.classify(
                "{\"id\":\"1\",\"result\":{},\"extra\":\"ignored\"}"));
        Assert.assertEquals(BskFrameKind.BSK_HANDSHAKE, BskFrameRouter.classify(
                "{\"id\":\"1\",\"method\":\"tool.click\",\"audit_enabled\":true}"));
    }
}
