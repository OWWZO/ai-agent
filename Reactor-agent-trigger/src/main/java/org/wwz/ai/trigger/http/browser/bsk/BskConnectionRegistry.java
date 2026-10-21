package org.wwz.ai.trigger.http.browser.bsk;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.adapter.port.BrowserBskConnectionPort;

/**
 * bsk 扩展连接的在线状态端口实现（021）。
 *
 * <p>bsk 扩展连的是 bsk 专用 WS 网关，连接的「归属用户」来自配对凭证
 * （握手拦截器把 userId 写进 session attributes）。本类把
 * {@link BskFrameRelay} 里已有的连接表暴露成 domain 端口，
 * 供 case 层查询「这个用户的浏览器是否已连接」——前端「连接浏览器」按钮就靠这个口径。</p>
 *
 * <p><b>为什么不自己维护一张表</b>：连接表只有 {@link BskFrameRelay} 一份。
 * CLI 侧的路由（按 userId 把 tool.* 投给正确的浏览器）和「是否在线」
 * 读的是同一批连接，两张表迟早会漂移。</p>
 *
 * <p><b>在线判定</b>：连接已登记 + 已完成 bsk 握手。握手未完成的连接不算在线——
 * bsk 协议规定首帧必须是 {@code system.handshake}，握手完成才代表扩展真的可用。</p>
 *
 * <p><b>为什么不把 bsk 连接塞进老 relay 的连接表</b>：那张表里的通道是给
 * <b>老协议帧</b>用的，塞进去会让老 relay 的命令路由把 legacy 帧发给 bsk 扩展
 * （bsk 扩展不认 legacy 帧）。两条链路各自记账，只在「在线状态」这一层汇合。</p>
 */
@Component
@ConditionalOnProperty(prefix = "reactor.browser-bsk", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BskConnectionRegistry implements BrowserBskConnectionPort {

    private final BskFrameRelay relay;

    public BskConnectionRegistry(BskFrameRelay relay) {
        this.relay = relay;
    }

    @Override
    public boolean isOnline(String userId) {
        return relay.isOnlineByUser(userId);
    }

    @Override
    public void disconnect(String userId) {
        relay.disconnectUser(userId);
    }
}
