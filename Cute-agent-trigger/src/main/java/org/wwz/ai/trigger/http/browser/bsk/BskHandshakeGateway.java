package org.wwz.ai.trigger.http.browser.bsk;

import java.util.Optional;

/**
 * bsk 协议握手兼容性判定。
 *
 * <p>严格对齐 {@code BrowserSkill/crates/bsk-protocol/src/system.rs} 的
 * {@code evaluate_handshake_compat()}，判定规则只有四条：</p>
 *
 * <pre>
 *  protocol 无法解析 或 major 不同        → Reject
 *  peer_protocol &lt; our_min_compatible    → Reject
 *  our_protocol  &lt; peer_min_compatible   → Reject（peer 未提供 floor 时跳过）
 *  同 major 但版本号不同                   → Skew（允许，但记录告警）
 *  其他                                    → Ok
 * </pre>
 *
 * <p>注意：应用 semver（{@code version} 字段）<b>不参与</b>本判定，
 * CLI 与扩展走独立版本线。</p>
 */
public final class BskHandshakeGateway {

    /** 当前协议版本，对齐 {@code daemon/state.rs} 的 PROTOCOL_VERSION。 */
    public static final String PROTOCOL_VERSION = "1.3";

    /** 本端接受的最低协议版本，对齐 MIN_COMPATIBLE_PROTOCOL。 */
    public static final String MIN_COMPATIBLE_PROTOCOL = "1.0";

    /** 握手响应里的服务名，扩展侧按此识别对端。 */
    public static final String SERVER_NAME = "browser-skill-daemon";

    /** protocol 的 major 字段上限，对齐 Rust 侧 MAX_PROTOCOL_MAJOR（2^53-1）。 */
    private static final long MAX_PROTOCOL_MAJOR = (1L << 53) - 1;

    private BskHandshakeGateway() {
    }

    /**
     * 握手判定结果。
     *
     * <p>{@code Skew} 与 {@code Ok} 都允许连接，区别仅在于是否需要在 UI 上
     * 提示版本漂移。</p>
     */
    public sealed interface Compat permits Ok, Skew, Reject {
    }

    /** 协议字符串完全一致。 */
    public record Ok() implements Compat {
    }

    /** 同 major 且双方 floor 都满足，但版本字符串不同（minor 漂移）。 */
    public record Skew() implements Compat {
    }

    /** 必须拒绝连接。 */
    public record Reject(String reason) implements Compat {
    }

    /**
     * 判定对端握手是否兼容。
     *
     * @param peerProtocolVersion   对端协议版本，如 "1.3"
     * @param peerMinCompatible     对端接受的最低协议版本；旧版扩展可能不提供
     * @param ourProtocolVersion    本端协议版本
     * @param ourMinCompatible      本端接受的最低协议版本
     */
    public static Compat evaluate(String peerProtocolVersion,
                                  String peerMinCompatible,
                                  String ourProtocolVersion,
                                  String ourMinCompatible) {
        Long peerMajor = parseMajor(peerProtocolVersion);
        Long ourMajor = parseMajor(ourProtocolVersion);
        if (peerMajor == null || ourMajor == null || !peerMajor.equals(ourMajor)) {
            return new Reject("protocol major mismatch (peer=" + peerProtocolVersion
                    + ", local=" + ourProtocolVersion + ")");
        }

        Integer belowFloor = compareProtocol(peerProtocolVersion, ourMinCompatible);
        if (belowFloor == null) {
            return new Reject("peer protocol version unparseable: " + peerProtocolVersion);
        }
        if (belowFloor < 0) {
            return new Reject("peer protocol " + peerProtocolVersion
                    + " is below local min_compatible_protocol " + ourMinCompatible);
        }

        if (peerMinCompatible != null) {
            Integer localBelowPeerFloor = compareProtocol(ourProtocolVersion, peerMinCompatible);
            if (localBelowPeerFloor == null) {
                // 区分是本端还是对端不可解析，错误信息才有排查价值。
                if (parseProtocolParts(ourProtocolVersion) == null) {
                    return new Reject("local protocol version unparseable: " + ourProtocolVersion);
                }
                return new Reject("peer min_compatible_protocol unparseable: " + peerMinCompatible);
            }
            if (localBelowPeerFloor < 0) {
                return new Reject("local protocol " + ourProtocolVersion
                        + " is below peer min_compatible_protocol " + peerMinCompatible);
            }
        }

        // 注意：不能直接做字符串相等。Rust 侧用的是 compare_protocol()，
        // 因此 "1.3.0" 与 "1.3" 解析为同一 (major, minor)，应判为 Ok 而非 Skew。
        Integer ordering = compareProtocol(peerProtocolVersion, ourProtocolVersion);
        if (ordering == null) {
            return new Reject("peer protocol version unparseable: " + peerProtocolVersion);
        }
        return ordering == 0 ? new Ok() : new Skew();
    }

    /** 用本端常量做判定的便捷重载。 */
    public static Compat evaluate(String peerProtocolVersion, String peerMinCompatible) {
        return evaluate(peerProtocolVersion, peerMinCompatible, PROTOCOL_VERSION, MIN_COMPATIBLE_PROTOCOL);
    }

    /** 连接是否被允许（Ok 或 Skew）。 */
    public static boolean isAccepted(Compat compat) {
        return compat instanceof Ok || compat instanceof Skew;
    }

    /** 拒绝原因；被接受时返回 empty。 */
    public static Optional<String> rejectReason(Compat compat) {
        return compat instanceof Reject reject ? Optional.of(reject.reason()) : Optional.empty();
    }

    /**
     * 按 (major, minor) 字典序比较两个协议版本串。
     *
     * @return 负数表示 a &lt; b，0 表示相等，正数表示 a &gt; b；任一侧不可解析返回 null
     */
    public static Integer compareProtocol(String a, String b) {
        long[] pa = parseProtocolParts(a);
        long[] pb = parseProtocolParts(b);
        if (pa == null || pb == null) {
            return null;
        }
        if (pa[0] != pb[0]) {
            return Long.compare(pa[0], pb[0]);
        }
        return Long.compare(pa[1], pb[1]);
    }

    /**
     * 解析 "1"、"1.0"、"1.0.0" 形式的前两段。
     *
     * <p>major 段必须是纯 ASCII 数字且不超过 {@link #MAX_PROTOCOL_MAJOR}，
     * 与扩展侧 {@code parseProtocolMajor} 的接受集合保持一致——否则会出现
     * "服务端接受但扩展拒绝"的不对称。</p>
     */
    private static long[] parseProtocolParts(String value) {
        if (value == null) {
            return null;
        }
        Long major = parseMajor(value);
        if (major == null) {
            return null;
        }
        String[] segments = value.split("\\.", -1);
        long minor = 0;
        if (segments.length > 1) {
            String seg = segments[1];
            // Rust 侧把 "1." 的 minor 段当作 0（system.rs 的 `Some("") => 0`），
            // 这里必须一致，否则会出现"服务端拒绝但扩展接受"的不对称。
            if (seg.isEmpty()) {
                minor = 0;
            } else {
                if (!isAllDigits(seg)) {
                    return null;
                }
                try {
                    minor = Long.parseLong(seg);
                } catch (NumberFormatException e) {
                    return null;
                }
                if (minor > MAX_PROTOCOL_MAJOR) {
                    return null;
                }
            }
        }
        return new long[]{major, minor};
    }

    /** 只取第一段作为 major，拒绝前导正负号、空白、科学计数法等。 */
    private static Long parseMajor(String value) {
        if (value == null) {
            return null;
        }
        int dot = value.indexOf('.');
        String head = dot < 0 ? value : value.substring(0, dot);
        if (head.isEmpty() || !isAllDigits(head)) {
            return null;
        }
        try {
            long n = Long.parseLong(head);
            return n > MAX_PROTOCOL_MAJOR ? null : n;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isAllDigits(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }
}
