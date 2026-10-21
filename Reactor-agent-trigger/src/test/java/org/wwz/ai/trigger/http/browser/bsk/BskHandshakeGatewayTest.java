package org.wwz.ai.trigger.http.browser.bsk;

import org.junit.Assert;
import org.junit.Test;

/**
 * 锁定 {@link BskHandshakeGateway} 的判定规则与 Rust 侧
 * {@code evaluate_handshake_compat()} 一致。
 *
 * <p>用例直接对照 {@code BrowserSkill/crates/bsk-protocol/src/system.rs}
 * 的四条分支与 {@code parse_major_tests} 的接受集合。</p>
 */
public class BskHandshakeGatewayTest {

    private static BskHandshakeGateway.Compat eval(String peer, String peerFloor) {
        return BskHandshakeGateway.evaluate(peer, peerFloor);
    }

    // ── 接受 ────────────────────────────────────────────────

    @Test
    public void exactMatchIsOk() {
        Assert.assertTrue(eval("1.3", "1.0") instanceof BskHandshakeGateway.Ok);
    }

    @Test
    public void sameMajorDifferentMinorIsSkew() {
        BskHandshakeGateway.Compat compat = eval("1.2", "1.0");
        Assert.assertTrue(compat instanceof BskHandshakeGateway.Skew);
        Assert.assertTrue(BskHandshakeGateway.isAccepted(compat));
    }

    @Test
    public void newerMinorIsSkewNotReject() {
        BskHandshakeGateway.Compat compat = eval("1.9", "1.0");
        Assert.assertTrue(compat instanceof BskHandshakeGateway.Skew);
    }

    @Test
    public void missingPeerFloorIsTolerated() {
        // 旧版扩展不带 min_compatible_protocol，该分支必须跳过。
        Assert.assertTrue(eval("1.3", null) instanceof BskHandshakeGateway.Ok);
        Assert.assertTrue(eval("1.2", null) instanceof BskHandshakeGateway.Skew);
    }

    // ── 拒绝：major ─────────────────────────────────────────

    @Test
    public void differentMajorIsRejected() {
        BskHandshakeGateway.Compat compat = eval("2.0", "1.0");
        Assert.assertTrue(compat instanceof BskHandshakeGateway.Reject);
        Assert.assertTrue(BskHandshakeGateway.rejectReason(compat).orElseThrow().contains("major mismatch"));
    }

    @Test
    public void unparseablePeerProtocolIsRejected() {
        for (String bad : new String[]{"", "abc", "+1.0", "-1.0", " 1.0", ".1", "1x", "1e3"}) {
            BskHandshakeGateway.Compat compat = eval(bad, null);
            Assert.assertTrue("should reject: " + bad, compat instanceof BskHandshakeGateway.Reject);
        }
    }

    // ── 拒绝：floor ─────────────────────────────────────────

    @Test
    public void peerBelowLocalFloorIsRejected() {
        BskHandshakeGateway.Compat compat = eval("0.9", "0.0");
        // major 0 != 1，先被 major 分支拦下；这条断言锁定的是"必须拒绝"。
        Assert.assertTrue(compat instanceof BskHandshakeGateway.Reject);
    }

    @Test
    public void peerBelowLocalFloorSameMajorIsRejected() {
        BskHandshakeGateway.Compat compat = BskHandshakeGateway.evaluate(
                "1.0", "1.0", "1.3", "1.1");
        Assert.assertTrue(compat instanceof BskHandshakeGateway.Reject);
        Assert.assertTrue(BskHandshakeGateway.rejectReason(compat).orElseThrow()
                .contains("below local min_compatible_protocol"));
    }

    @Test
    public void localBelowPeerFloorIsRejected() {
        BskHandshakeGateway.Compat compat = BskHandshakeGateway.evaluate(
                "1.3", "1.5", "1.3", "1.0");
        Assert.assertTrue(compat instanceof BskHandshakeGateway.Reject);
        Assert.assertTrue(BskHandshakeGateway.rejectReason(compat).orElseThrow()
                .contains("below peer min_compatible_protocol"));
    }

    @Test
    public void unparseablePeerFloorIsRejected() {
        BskHandshakeGateway.Compat compat = BskHandshakeGateway.evaluate(
                "1.3", "not-a-version", "1.3", "1.0");
        Assert.assertTrue(compat instanceof BskHandshakeGateway.Reject);
        Assert.assertTrue(BskHandshakeGateway.rejectReason(compat).orElseThrow()
                .contains("peer min_compatible_protocol unparseable"));
    }

    // ── compareProtocol ─────────────────────────────────────

    @Test
    public void compareProtocolUsesMajorThenMinor() {
        Assert.assertEquals(0, (int) BskHandshakeGateway.compareProtocol("1.0", "1"));
        Assert.assertTrue(BskHandshakeGateway.compareProtocol("1.0", "1.1") < 0);
        Assert.assertTrue(BskHandshakeGateway.compareProtocol("1.2", "1.1") > 0);
        Assert.assertTrue(BskHandshakeGateway.compareProtocol("2.0", "1.9") > 0);
        Assert.assertNull(BskHandshakeGateway.compareProtocol("x", "1.0"));
    }

    @Test
    public void leadingZerosAreAccepted() {
        // 与 Rust parse_major_tests::accepts_leading_zeros 对齐。
        // "01.3" 解析为 (1,3)，与 "1.3" 等价，因此判定为 Ok 而非 Skew。
        Assert.assertTrue(eval("01.3", "1.0") instanceof BskHandshakeGateway.Ok);
        Assert.assertEquals(0, (int) BskHandshakeGateway.compareProtocol("01.3", "1.3"));
    }

    @Test
    public void majorAboveMaxSafeIntegerIsRejected() {
        // 与 Rust 侧 MAX_PROTOCOL_MAJOR 上限对齐。
        Assert.assertTrue(eval("9007199254740992.0", null) instanceof BskHandshakeGateway.Reject);
    }

    @Test
    public void threeSegmentVersionParsesFirstTwo() {
        Assert.assertTrue(eval("1.3.0", "1.0") instanceof BskHandshakeGateway.Ok);
    }

    @Test
    public void trailingDotIsTreatedAsMinorZero() {
        // Rust 侧 system.rs 的 `Some("") => 0`：`"1."` 等价于 `"1.0"`。
        Assert.assertTrue(eval("1.", "1.0") instanceof BskHandshakeGateway.Skew);
        Assert.assertEquals(0, (int) BskHandshakeGateway.compareProtocol("1.", "1.0"));
    }

    @Test
    public void largeMajorIsNotTruncated() {
        // 曾经用 int 承载 major：4294967297 会被截断成 1，导致与 "1.0" 比较结果为 0。
        // 改成 long 后必须返回正值（大于），且两侧互换为负。
        Assert.assertTrue("4294967297 must compare greater than 1",
                BskHandshakeGateway.compareProtocol("4294967297.0", "1.0") > 0);
        Assert.assertTrue(BskHandshakeGateway.compareProtocol("1.0", "4294967297.0") < 0);
        // 同一超大 major 与自身相等。
        Assert.assertEquals(0, (int) BskHandshakeGateway.compareProtocol(
                "4294967297.0", "4294967297.0"));
    }
}
