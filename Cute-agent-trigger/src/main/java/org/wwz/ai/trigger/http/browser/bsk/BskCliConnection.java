package org.wwz.ai.trigger.http.browser.bsk;

/**
 * 一条 CLI 侧的 WS 连接。
 *
 * <p>CLI 侧与扩展侧是<b>两种角色</b>，必须分开记账：</p>
 * <ul>
 *   <li><b>扩展侧</b>：首帧必须是 {@code system.handshake}，之后由网关向它发
 *       {@code tool.*} 请求、收它的应答帧。它带的是配对凭证（子协议/查询串里的 token）。</li>
 *   <li><b>CLI 侧</b>：<b>不发握手</b>（它对应 Rust CLI 的 IPC 客户端角色），
 *       直接发 {@code tool.*} 请求帧、收应答帧。它带的是
 *       {@code X-Adapter-Secret} + {@code user_id}。</li>
 * </ul>
 *
 * <p>把两者混在一条连接上会让首帧判定失去意义：扩展侧要求首帧是握手，
 * 而 CLI 的第一帧恰恰是 {@code tool.*}。</p>
 *
 * @param connectionId WS 会话 id
 * @param userId       归属用户 id（CLI 侧的 {@code user_id} 查询参数）
 * @param browserId    目标浏览器实例 id（{@code browser_id} 查询参数，可空）
 * @param sink         帧出口
 */
public record BskCliConnection(String connectionId,
                               String userId,
                               String browserId,
                               BskSink sink) {
}
