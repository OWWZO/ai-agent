package org.wwz.ai.trigger.http.browser.bsk;

/**
 * bsk 帧分类。
 *
 * <p>只按<b>顶层 JSON 键</b>判定，不解析 {@code method} 的业务语义——
 * 契约 §6.3 要求 Java 保持透明转发，只有握手是协议级例外。</p>
 *
 * <p>判定顺序对齐 {@code BrowserSkill/crates/bsk-protocol/src/frame.rs}
 * 的 {@code FrameVisitor::visit_map}：{@code event} → {@code method} →
 * {@code result}/{@code error}，最后落到 Reactor 旧帧。</p>
 */
public enum BskFrameKind {

    /** 扩展主动发来的请求帧（含必须首帧到达的 {@code system.handshake}）。 */
    BSK_HANDSHAKE,

    /** 扩展对 Java 侧请求的应答帧。 */
    BSK_RESPONSE,

    /** 扩展主动推送的事件帧。 */
    BSK_EVENT,

    /** 不是 bsk 帧，交给现有 Reactor 中继处理。 */
    REACTOR_LEGACY,

    /** 无法归类（非法 JSON / 非对象 / 响应帧 result 与 error 同现或缺席）。 */
    UNKNOWN
}
