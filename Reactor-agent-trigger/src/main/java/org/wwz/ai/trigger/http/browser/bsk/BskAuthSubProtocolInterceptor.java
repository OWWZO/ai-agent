package org.wwz.ai.trigger.http.browser.bsk;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * bsk 扩展的 WebSocket 认证拦截器。
 *
 * <p>凭证走 <b>WebSocket 子协议头</b>而非 URL 参数（避免 token 进访问日志），
 * 对齐扩展侧 {@code apps/extension/src/transport/remote-endpoint.ts:84}：</p>
 *
 * <pre>{@code new WebSocket(url, ["bsk-auth." + endpoint.token]) }</pre>
 *
 * <p>解析规则：</p>
 * <ul>
 *   <li>请求头 {@code Sec-WebSocket-Protocol} 里必须出现以 {@code bsk-auth.}
 *       为前缀的子协议，其后的部分就是配对 token。</li>
 *   <li>解析成功把 userId 写入 WS attributes（{@code userId}），并把 token 放到
 *       attributes 的 {@code bskToken} 供 handler 使用。</li>
 *   <li>为了与标准握手兼容，额外支持 {@code bsk-auth.<token>} 的
 *       base64url 变体（{@code bsk-auth64.} 前缀）——部分 WS 客户端会对
 *       子协议 token 做字符校验。</li>
 *   <li>找不到合法子协议或 token 无效 → 返回 false 拒绝握手
 *       （契约 T6.8 "未认证连接必须被拒"）。</li>
 * </ul>
 *
 * <p>注意：本拦截器只做认证；子协议的<b>回显</b>由 Spring 的
 * {@code AbstractHandshakeHandler.selectProtocol} 完成，前提是 handler 实现
 * {@code SubProtocolCapable} 返回 {@code bsk-auth.<token>}——这由
 * {@link BskRelayWebSocketHandler} 动态实现。</p>
 */
@Slf4j
public class BskAuthSubProtocolInterceptor implements HandshakeInterceptor {

    /** 扩展侧硬编码的前缀（{@code REMOTE_AUTH_PROTOCOL_PREFIX}）。 */
    public static final String AUTH_PROTOCOL_PREFIX = "bsk-auth.";

    /** base64url 变体前缀。 */
    public static final String AUTH_PROTOCOL_BASE64_PREFIX = "bsk-auth64.";

    /** attributes 里放 userId 的键，与 Reactor relay 保持一致。 */
    public static final String ATTR_USER_ID = "userId";

    /** attributes 里放原始 token 的键。 */
    public static final String ATTR_BSK_TOKEN = "bskToken";

    /** attributes 里放客户端所声明子协议的键（handler 回显用）。 */
    public static final String ATTR_BSK_SUBPROTOCOL = "bskSubProtocol";

    private final BskTokenResolver tokenResolver;

    public BskAuthSubProtocolInterceptor(BskTokenResolver tokenResolver) {
        this.tokenResolver = tokenResolver;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String header = request.getHeaders().getFirst("Sec-WebSocket-Protocol");
        if (StringUtils.isBlank(header)) {
            // 复用 Reactor 老扩展的鉴权方式：token 走 URL 查询参数。
            // 这条路径不涉及子协议协商，握手不会被「服务端未回显子协议」判失败。
            String queryToken = queryParam(request.getURI(), "token");
            String userId = StringUtils.isBlank(queryToken)
                    ? null
                    : tokenResolver.resolveUserId(queryToken);
            if (StringUtils.isBlank(userId)) {
                log.warn("bsk handshake rejected: no Sec-WebSocket-Protocol header and no valid ?token=");
                return false;
            }
            attributes.put(ATTR_USER_ID, userId);
            attributes.put(ATTR_BSK_TOKEN, queryToken);
            return true;
        }
        ParsedAuth auth = parse(header);
        if (auth == null) {
            log.warn("bsk handshake rejected: no {} subprotocol present", AUTH_PROTOCOL_PREFIX);
            return false;
        }
        String userId = tokenResolver.resolveUserId(auth.token());
        if (StringUtils.isBlank(userId)) {
            log.warn("bsk handshake rejected: token invalid (protocol={}, tokenLen={})",
                    auth.subProtocol(), auth.token().length());
            return false;
        }
        attributes.put(ATTR_USER_ID, userId);
        attributes.put(ATTR_BSK_TOKEN, auth.token());
        attributes.put(ATTR_BSK_SUBPROTOCOL, auth.subProtocol());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
    }

    /**
     * 从 {@code Sec-WebSocket-Protocol} 头解析认证子协议。
     *
     * <p>头可能是逗号分隔的多值；扩展只发一个 {@code bsk-auth.<token>}。</p>
     *
     * @return 解析结果；没有合法认证子协议时返回 null
     */
    static ParsedAuth parse(String header) {
        if (StringUtils.isBlank(header)) {
            return null;
        }
        for (String part : header.split(",")) {
            String candidate = part.trim();
            if (candidate.isEmpty()) {
                continue;
            }
            if (candidate.startsWith(AUTH_PROTOCOL_BASE64_PREFIX)) {
                String encoded = candidate.substring(AUTH_PROTOCOL_BASE64_PREFIX.length());
                String token = base64UrlDecode(encoded);
                if (StringUtils.isNotBlank(token)) {
                    return new ParsedAuth(candidate, token);
                }
                continue;
            }
            if (candidate.startsWith(AUTH_PROTOCOL_PREFIX)) {
                String token = candidate.substring(AUTH_PROTOCOL_PREFIX.length());
                if (StringUtils.isNotBlank(token)) {
                    return new ParsedAuth(candidate, token);
                }
            }
        }
        return null;
    }

    /** 宽松 base64url：容忍缺 padding，失败返回 null。 */
    private static String base64UrlDecode(String encoded) {
        try {
            String normalized = encoded.replace('-', '+').replace('_', '/');
            int padding = (4 - normalized.length() % 4) % 4;
            return new String(Base64.getDecoder().decode(normalized + "=".repeat(padding)),
                    StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 解析结果：客户端声明的完整子协议 + 还原出的 token。 */
    record ParsedAuth(String subProtocol, String token) {
    }

    /**
     * 从 URI 查询串里取参数（对齐 {@code BrowserRelayHandshakeInterceptor} 的做法）。
     *
     * <p>query 参数里的 token 不做 base64url 解码——它就是原样的配对 token。</p>
     */
    private static String queryParam(java.net.URI uri, String name) {
        if (uri == null || StringUtils.isBlank(uri.getQuery())) {
            return null;
        }
        for (String part : uri.getQuery().split("&")) {
            int eq = part.indexOf('=');
            String key = eq < 0 ? part : part.substring(0, eq);
            if (name.equals(key)) {
                return eq < 0 ? "" : java.net.URLDecoder.decode(
                        part.substring(eq + 1), java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    /** 供 handler 查询：从已解析 attributes 取回子协议。 */
    public static String subProtocolOf(Map<String, Object> attributes) {
        Object value = attributes == null ? null : attributes.get(ATTR_BSK_SUBPROTOCOL);
        return value instanceof String s ? s : null;
    }

    /** 工具：把头值按 Spring 的解析习惯切成列表（测试用）。 */
    static List<String> splitProtocols(String header) {
        return List.of(header.split(","));
    }
}
