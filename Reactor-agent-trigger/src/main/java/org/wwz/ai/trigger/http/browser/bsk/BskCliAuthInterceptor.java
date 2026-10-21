package org.wwz.ai.trigger.http.browser.bsk;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * bsk 网关 <b>CLI 侧</b>的 WebSocket 认证拦截器。
 *
 * <p><b>与扩展侧鉴权完全分开</b>：扩展侧用配对凭证（子协议/查询串里的 token，
 * 见 {@link BskAuthSubProtocolInterceptor}），CLI 侧用
 * <b>共享密钥 + user_id</b>。两套凭证混用会让「谁是谁」变得不可判。</p>
 *
 * <p>CLI 侧凭证（对齐老 relay 的 {@code POST /internal/browser/rpc}）：</p>
 * <ul>
 *   <li>请求头 {@code X-Adapter-Secret}：与
 *       {@code reactor.browser-relay.internal-rpc-secret} 一致。
 *       配置了密钥就必须带上；没配置密钥则只允许回环地址（CLI 由 Java 本机拉起）。</li>
 *   <li>查询参数 {@code user_id}：归属用户 id，必填。</li>
 *   <li>查询参数 {@code browser_id}：目标浏览器实例 id，可空；给了就精确路由。</li>
 * </ul>
 *
 * <p><b>user_id 本身不构成越权</b>：它只决定「请求投给哪个用户的浏览器」，
 * 而路由表里的扩展连接都是按配对凭证绑定到具体用户的——伪造别人的 user_id
 * 只能把请求投向那个用户的浏览器，而那个浏览器的连接必须由它自己的配对凭证
 * 建立。密钥则保证调用方是我们自己拉起的 CLI。</p>
 */
@Slf4j
public class BskCliAuthInterceptor implements HandshakeInterceptor {

    /** 共享密钥头名（与老 relay 的 internal RPC 一致）。 */
    public static final String SECRET_HEADER = "X-Adapter-Secret";

    /** attributes 里放 userId 的键。 */
    public static final String ATTR_USER_ID = "userId";

    /** attributes 里放 browserId 的键。 */
    public static final String ATTR_BROWSER_ID = "browserId";

    /** 期望的共享密钥；空白表示「只允许回环」。 */
    private final String expectedSecret;

    public BskCliAuthInterceptor(String expectedSecret) {
        this.expectedSecret = StringUtils.trimToNull(expectedSecret);
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        if (!authorized(request)) {
            log.warn("bsk cli handshake rejected: unauthorized (secretConfigured={} remote={})",
                    expectedSecret != null, request.getRemoteAddress());
            return false;
        }
        String userId = queryParam(request.getURI(), "user_id");
        if (StringUtils.isBlank(userId)) {
            log.warn("bsk cli handshake rejected: missing user_id");
            return false;
        }
        attributes.put(ATTR_USER_ID, userId);
        String browserId = queryParam(request.getURI(), "browser_id");
        if (StringUtils.isNotBlank(browserId)) {
            attributes.put(ATTR_BROWSER_ID, browserId);
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
    }

    /** 密钥校验：配置了密钥就必须对上；没配置则只允许回环地址。 */
    private boolean authorized(ServerHttpRequest request) {
        if (expectedSecret != null) {
            return expectedSecret.equals(request.getHeaders().getFirst(SECRET_HEADER));
        }
        return isLoopback(request);
    }

    private static boolean isLoopback(ServerHttpRequest request) {
        InetSocketAddress address = request.getRemoteAddress();
        if (address == null) {
            return false;
        }
        InetAddress resolved = address.getAddress();
        if (resolved != null) {
            return resolved.isLoopbackAddress();
        }
        return "::1".equals(address.getHostString())
                || "127.0.0.1".equals(address.getHostString());
    }

    private static String queryParam(URI uri, String name) {
        if (uri == null || StringUtils.isBlank(uri.getQuery())) {
            return null;
        }
        for (String part : uri.getQuery().split("&")) {
            int eq = part.indexOf('=');
            String key = eq < 0 ? part : part.substring(0, eq);
            if (name.equals(key)) {
                return eq < 0 ? "" : java.net.URLDecoder.decode(
                        part.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
