package org.wwz.ai.trigger.http.browser.bsk;

import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.util.List;

/**
 * bsk 网关的握手处理器：把客户端请求的认证子协议<b>原样回显</b>到 101 响应。
 *
 * <p><b>为什么必须回显</b>：bsk 扩展用 {@code Sec-WebSocket-Protocol: bsk-auth.<token>}
 * 携带凭证，而 Chromium 的 {@code ValidateSubProtocol()} 规定——客户端发了非空的
 * 子协议头而服务端一个都没选，握手会被判失败并当场 RST 连接。表现是
 * 「连接建立 → 几毫秒后 1006 异常断开 → 服务端一个帧都没收到」。</p>
 *
 * <p>Rust daemon 同样回显（{@code crates/bsk-cli/src/daemon/remote/server.rs} 的
 * {@code upgrade_response.headers_mut().insert("sec-websocket-protocol", ...)}），
 * 其测试也锁定了该行为。本类是 Java 侧的对齐实现。</p>
 *
 * <p><b>为什么不能靠 {@code SubProtocolCapable}</b>：凭证里的 token 是<b>每条连接
 * 动态不同</b>的，而 {@code getSubProtocols()} 是 handler 级静态列表，无法表达
 * 「回显本次连接请求的那个值」。所以只能在这里覆写选协议逻辑。</p>
 *
 * <p>安全性：回显逻辑只处理 {@code bsk-auth.} / {@code bsk-auth64.} 前缀，且
 * 本处理器在 {@link BskAuthSubProtocolInterceptor} 之后才运行——拦截器已把无效
 * token 的连接挡在握手之前，这里回显的值必然是已通过校验的那一个。</p>
 */
public class BskSubProtocolHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected String selectProtocol(List<String> requestedProtocols, WebSocketHandler webSocketHandler) {
        return selectAuthSubprotocol(requestedProtocols);
    }

    /**
     * 从客户端请求的子协议列表里挑出认证子协议（逐字回显）。
     *
     * <p>包级可见，便于测试直接锁定这段选择逻辑。</p>
     */
    static String selectAuthSubprotocol(List<String> requestedProtocols) {
        if (requestedProtocols != null) {
            for (String requested : requestedProtocols) {
                if (requested == null) {
                    continue;
                }
                // 与拦截器的解析口径一致：先 bsk-auth64.，再 bsk-auth.。
                if (requested.startsWith(BskAuthSubProtocolInterceptor.AUTH_PROTOCOL_BASE64_PREFIX)
                        || requested.startsWith(BskAuthSubProtocolInterceptor.AUTH_PROTOCOL_PREFIX)) {
                    // 原样回显客户端请求的值（Tomcat 的协商逻辑要求它出现在
                    // 客户端请求列表里，逐字一致才不会被判为「未请求的协议」）。
                    return requested;
                }
            }
        }
        return null;
    }
}
