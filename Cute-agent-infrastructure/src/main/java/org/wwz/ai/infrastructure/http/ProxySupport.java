package org.wwz.ai.infrastructure.http;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;

/**
 * 出站代理解析工具：解析 http/https/socks5 代理串为 {@link Proxy}，
 * 为 JDK HttpClient（LLM 聊天）与 OkHttp（图片生成）两种出站栈提供一致的代理接入。
 */
public final class ProxySupport {

    private ProxySupport() {
    }

    /**
     * 解析形如 {@code http://127.0.0.1:7890} / {@code socks5://127.0.0.1:1080} 的代理串。
     *
     * @param rawProxy 代理串；为空或空白表示直连
     * @return 解析出的代理；未配置时返回 {@code null}
     * @throws IllegalArgumentException 代理串非法：scheme 不支持、缺 host/port、携带凭证
     */
    public static Proxy parse(String rawProxy) {
        String trimmed = StringUtils.trimToNull(rawProxy);
        if (trimmed == null) {
            return null;
        }
        try {
            URI uri = URI.create(trimmed);
            String scheme = StringUtils.defaultString(uri.getScheme()).toLowerCase();
            Proxy.Type type = switch (scheme) {
                case "http", "https" -> Proxy.Type.HTTP;
                case "socks", "socks5", "socks5h" -> Proxy.Type.SOCKS;
                default -> throw new IllegalArgumentException("unsupported proxy scheme: " + scheme);
            };
            if (StringUtils.isBlank(uri.getHost()) || uri.getPort() <= 0 || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("proxy must include host and port without credentials");
            }
            return new Proxy(type, new InetSocketAddress(uri.getHost(), uri.getPort()));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid proxy configuration: " + trimmed, e);
        }
    }

    /**
     * 把单个代理包装成对所有目标生效的 {@link ProxySelector}，供 {@link java.net.http.HttpClient} 使用。
     * https 目标经代理自动走 CONNECT 隧道，http 目标直接转发给代理。
     */
    public static ProxySelector proxySelector(Proxy proxy) {
        return new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                return List.of(proxy);
            }

            @Override
            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                // 连接失败由 HttpClient 自行抛错，无需额外处理
            }
        };
    }
}
