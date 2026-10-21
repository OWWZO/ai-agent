package org.wwz.ai.domain.agent.adapter.port;

/**
 * bsk 浏览器连接的在线状态端口（021）。
 *
 * <p>bsk 扩展连的是 <b>bsk 专用 WS 网关</b>（{@code /api/agent/browser/bsk-relay}），
 * 与 Reactor 老 relay 是两条互不相干的链路。老 relay 的连接表里没有 bsk 的连接，
 * 所以「浏览器是否已连接」不能只看老 relay——否则前端按钮在 bsk 扩展已连上的情况下
 * 仍然显示未连接。</p>
 *
 * <p>本端口只暴露<b>在线状态</b>与<b>断开</b>两件事，不暴露 bsk 帧语义：
 * bsk 帧路由是 trigger 层网关的职责，domain 不感知。</p>
 *
 * <p>实现方在 trigger 层（{@code trigger/http/browser/bsk/BskConnectionRegistry}）——
 * bsk WS 网关本身就在 trigger（协议适配层），与 {@link BrowserRelaySocketPort}
 * 的实现在 infrastructure 的原因不同。</p>
 */
public interface BrowserBskConnectionPort {

    /**
     * 该用户当前是否有<b>已握手且仍在线</b>的 bsk 扩展连接。
     *
     * @param userId 配对时的用户 id
     * @return 无连接返回 false
     */
    boolean isOnline(String userId);

    /**
     * 断开该用户的所有 bsk 扩展连接（前端「断开」按钮语义）。
     *
     * @param userId 配对时的用户 id
     */
    void disconnect(String userId);
}
