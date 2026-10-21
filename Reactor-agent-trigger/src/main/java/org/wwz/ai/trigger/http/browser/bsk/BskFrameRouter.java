package org.wwz.ai.trigger.http.browser.bsk;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;

/**
 * 入站文本帧分类器（契约 §6.2 的"识别 bsk 帧格式"）。
 *
 * <p>只按<b>顶层 JSON 键</b>分类，绝不解析 {@code method} 的业务语义——
 * 契约 §6.3 要求 Java 对 bsk 保持透明转发，握手是唯一的协议级例外。</p>
 *
 * <p>判定顺序严格对齐 {@code BrowserSkill/crates/bsk-protocol/src/frame.rs}
 * 的 {@code FrameVisitor::visit_map}：</p>
 *
 * <pre>
 *  event 存在        → Event
 *  method 存在       → Request
 *  result / error    → Response（且二者必须恰好存在一个）
 * </pre>
 *
 * <p>响应帧的歧义处理同样照抄 Rust：{@code result} 与 {@code error}
 * 同现或缺席时返回 {@link BskFrameKind#UNKNOWN}，而不是"猜一个"。
 * {@code {"id":"1","result":null}} 是合法的 Ok(null)，
 * 因为 Rust 侧用 {@code de_present_field} 只关心键是否出现。</p>
 */
public final class BskFrameRouter {

    /** 扩展必须作为首帧发送的方法名。 */
    public static final String METHOD_SYSTEM_HANDSHAKE = "system.handshake";

    private BskFrameRouter() {
    }

    /**
     * 分类一条入站文本帧。
     *
     * <p>返回 {@link BskFrameKind#REACTOR_LEGACY} 表示这条帧不是 bsk 帧，
     * 调用方应把它交给现有 Reactor 中继（{@code BrowserRelayHub}）。
     * 这样两种协议在同一条连接上互不干扰，而不需要 Java 认识 Reactor 帧格式。</p>
     *
     * @param payload 原始文本，允许为 null / 空白 / 非法 JSON
     * @return 分类结果，永不为 null
     */
    public static BskFrameKind classify(String payload) {
        if (StringUtils.isBlank(payload)) {
            return BskFrameKind.UNKNOWN;
        }
        final JSONObject frame;
        try {
            frame = JSON.parseObject(payload);
        } catch (RuntimeException ignored) {
            // 非法 JSON 没有可依赖的顶层键，不能冒充旧协议。
            return BskFrameKind.UNKNOWN;
        }
        if (frame == null) {
            return BskFrameKind.UNKNOWN;
        }

        // 顺序对齐 Rust FrameVisitor：event → method → result/error。
        if (frame.containsKey("event")) {
            return frame.get("event") instanceof String event && StringUtils.isNotBlank(event)
                    ? BskFrameKind.BSK_EVENT
                    : BskFrameKind.UNKNOWN;
        }

        if (frame.containsKey("method")) {
            if (!(frame.get("method") instanceof String method)
                    || StringUtils.isBlank(method)
                    // 请求帧必须带字符串 id（Rust: RpcId = String）。
                    || !(frame.get("id") instanceof String id)
                    || StringUtils.isBlank(id)) {
                return BskFrameKind.UNKNOWN;
            }
            // BskFrameKind.BSK_HANDSHAKE names the request-shaped bsk path;
            // methodOf(...) lets the connection state machine distinguish the
            // mandatory system.handshake from later tool/session requests.
            return BskFrameKind.BSK_HANDSHAKE;
        }

        boolean hasResult = frame.containsKey("result");
        boolean hasError = frame.containsKey("error");
        if (hasResult || hasError) {
            // 响应帧必须带字符串 id；缺 id 是畸形 bsk，不是 Reactor 帧。
            if (!(frame.get("id") instanceof String id) || StringUtils.isBlank(id)) {
                return BskFrameKind.UNKNOWN;
            }
            // Rust: (Some,Some) → AmbiguousResponse。
            return hasResult == hasError ? BskFrameKind.UNKNOWN : BskFrameKind.BSK_RESPONSE;
        }
        // 没有任何 bsk 协议键。Reactor 旧帧的判别键是 `type`（hello / ping /
        // tab.changed）与 `ok`（命令结果 {id, ok, data}）。
        if (frame.containsKey("type") || frame.containsKey("ok")) {
            return BskFrameKind.REACTOR_LEGACY;
        }
        // 只剩 id、既无 result/error 也无 Reactor 判别键：
        // Rust 侧是 InvalidFrame("expected result or error")，按畸形 bsk 处理。
        return BskFrameKind.UNKNOWN;
    }

    /** 请求帧的方法名；非请求帧返回 null。 */
    public static String methodOf(String payload) {
        JSONObject frame = parse(payload);
        Object method = frame == null ? null : frame.get("method");
        return method instanceof String value ? value : null;
    }

    /** 帧 id（请求与响应共用）；缺失返回 null。 */
    public static String idOf(String payload) {
        JSONObject frame = parse(payload);
        return frame == null ? null : frame.getString("id");
    }

    /** 请求/响应/事件帧的负载对象；解析失败返回 null。 */
    public static JSONObject parse(String payload) {
        if (StringUtils.isBlank(payload)) {
            return null;
        }
        try {
            return JSON.parseObject(payload);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /** 是否是一条可以安全透传的 bsk 帧（请求 / 响应 / 事件）。 */
    public static boolean isBsk(String payload) {
        BskFrameKind kind = classify(payload);
        return kind == BskFrameKind.BSK_HANDSHAKE
                || kind == BskFrameKind.BSK_RESPONSE
                || kind == BskFrameKind.BSK_EVENT;
    }
}
