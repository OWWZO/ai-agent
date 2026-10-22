package org.wwz.ai.trigger.http.browser.bsk;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

/**
 * 锁定子协议认证解析：{@code bsk-auth.<token>}（对齐
 * {@code apps/extension/src/transport/remote-endpoint.ts:84}）。
 */
public class BskAuthSubProtocolInterceptorTest {

    // ── 头解析 ───────────────────────────────────────────────

    @Test
    public void parsesPlainAuthSubprotocol() {
        BskAuthSubProtocolInterceptor.ParsedAuth auth =
                BskAuthSubProtocolInterceptor.parse("bsk-auth.abc123def");
        Assert.assertNotNull(auth);
        Assert.assertEquals("bsk-auth.abc123def", auth.subProtocol());
        Assert.assertEquals("abc123def", auth.token());
    }

    @Test
    public void parsesAuthAmongMultipleSubprotocols() {
        BskAuthSubProtocolInterceptor.ParsedAuth auth =
                BskAuthSubProtocolInterceptor.parse("chat, superchat, bsk-auth.tok-1");
        Assert.assertNotNull(auth);
        Assert.assertEquals("bsk-auth.tok-1", auth.subProtocol());
        Assert.assertEquals("tok-1", auth.token());
    }

    @Test
    public void parsesBase64Variant() {
        // "secret-token!" → base64url
        String encoded = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("secret-token!".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        BskAuthSubProtocolInterceptor.ParsedAuth auth =
                BskAuthSubProtocolInterceptor.parse("bsk-auth64." + encoded);
        Assert.assertNotNull(auth);
        Assert.assertEquals("secret-token!", auth.token());
    }

    @Test
    public void missingPrefixReturnsNull() {
        Assert.assertNull(BskAuthSubProtocolInterceptor.parse("chat"));
        Assert.assertNull(BskAuthSubProtocolInterceptor.parse(""));
        Assert.assertNull(BskAuthSubProtocolInterceptor.parse("bsk-auth."));
        Assert.assertNull(BskAuthSubProtocolInterceptor.parse(null));
    }

    @Test
    public void invalidBase64IsSkipped() {
        Assert.assertNull(BskAuthSubProtocolInterceptor.parse("bsk-auth64.%%%"));
    }

    // ── 拦截器行为 ───────────────────────────────────────────

    @Test
    public void validTokenResolvesUserIdIntoAttributes() {
        BskAuthSubProtocolInterceptor interceptor = new BskAuthSubProtocolInterceptor(
                token -> "tok-1".equals(token) ? "user-7" : null);
        Map<String, Object> attributes = new HashMap<>();
        boolean accepted = interceptor.beforeHandshake(
                request("bsk-auth.tok-1"), null, null, attributes);
        Assert.assertTrue(accepted);
        Assert.assertEquals("user-7", attributes.get(BskAuthSubProtocolInterceptor.ATTR_USER_ID));
        Assert.assertEquals("tok-1", attributes.get(BskAuthSubProtocolInterceptor.ATTR_BSK_TOKEN));
    }

    @Test
    public void missingHeaderIsRejected() {
        BskAuthSubProtocolInterceptor interceptor = new BskAuthSubProtocolInterceptor(
                token -> "user-1");
        Assert.assertFalse(interceptor.beforeHandshake(
                request(null), null, null, new HashMap<>()));
    }

    /**
     * 021：token 走 URL 查询参数时也接受（与 Cute 老扩展同一套鉴权方式）。
     */
    @Test
    public void queryParamTokenIsAccepted() {
        BskAuthSubProtocolInterceptor interceptor = new BskAuthSubProtocolInterceptor(
                token -> "tok-1".equals(token) ? "user-7" : null);
        Map<String, Object> attributes = new HashMap<>();
        boolean accepted = interceptor.beforeHandshake(
                request(null, "ws://localhost/api/agent/browser/bsk-relay?token=tok-1"),
                null, null, attributes);
        Assert.assertTrue(accepted);
        Assert.assertEquals("user-7", attributes.get(BskAuthSubProtocolInterceptor.ATTR_USER_ID));
        Assert.assertEquals("tok-1", attributes.get(BskAuthSubProtocolInterceptor.ATTR_BSK_TOKEN));
    }

    @Test
    public void queryParamTokenIsRejectedWhenInvalid() {
        BskAuthSubProtocolInterceptor interceptor = new BskAuthSubProtocolInterceptor(
                token -> null);
        Assert.assertFalse(interceptor.beforeHandshake(
                request(null, "ws://localhost/api/agent/browser/bsk-relay?token=bad"),
                null, null, new HashMap<>()));
    }

    /**
     * 子协议头存在时优先用它（即使 query 里也带 token）。
     */
    @Test
    public void subprotocolTakesPrecedenceOverQueryParam() {
        BskAuthSubProtocolInterceptor interceptor = new BskAuthSubProtocolInterceptor(
                token -> "header-token".equals(token) ? "user-7" : null);
        Map<String, Object> attributes = new HashMap<>();
        boolean accepted = interceptor.beforeHandshake(
                request("bsk-auth.header-token",
                        "ws://localhost/api/agent/browser/bsk-relay?token=query-token"),
                null, null, attributes);
        Assert.assertTrue(accepted);
        Assert.assertEquals("header-token", attributes.get(BskAuthSubProtocolInterceptor.ATTR_BSK_TOKEN));
    }

    @Test
    public void invalidTokenIsRejected() {
        BskAuthSubProtocolInterceptor interceptor = new BskAuthSubProtocolInterceptor(
                token -> null);
        Assert.assertFalse(interceptor.beforeHandshake(
                request("bsk-auth.bad-token"), null, null, new HashMap<>()));
    }

    @Test
    public void resolverFailureIsRejected() {
        BskAuthSubProtocolInterceptor interceptor = new BskAuthSubProtocolInterceptor(
                token -> {
                    throw new IllegalStateException("store down");
                });
        try {
            interceptor.beforeHandshake(request("bsk-auth.tok"), null, null, new HashMap<>());
            Assert.fail("resolver 异常应向上抛（握手失败）");
        } catch (IllegalStateException expected) {
            // 预期路径：基础设施故障不能被静默当成"拒绝"，让它炸出来。
        }
    }

    private static org.springframework.http.server.ServerHttpRequest request(String protocolHeader) {
        return request(protocolHeader, "ws://localhost/api/agent/browser/bsk-relay");
    }

    private static org.springframework.http.server.ServerHttpRequest request(String protocolHeader,
                                                                         String uri) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        if (protocolHeader != null) {
            headers.add("Sec-WebSocket-Protocol", protocolHeader);
        }
        return new StubServerHttpRequest(headers, uri);
    }

    /** 最小 ServerHttpRequest 替身：只提供 headers 与 URI（拦截器读取的部分）。 */
    private record StubServerHttpRequest(org.springframework.http.HttpHeaders headers, String uri)
            implements org.springframework.http.server.ServerHttpRequest {

        @Override
        public Principal getPrincipal() {
            return null;
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return null;
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return null;
        }

        @Override
        public org.springframework.http.server.ServerHttpAsyncRequestControl getAsyncRequestControl(
                org.springframework.http.server.ServerHttpResponse response) {
            return null;
        }

        @Override
        public org.springframework.http.HttpMethod getMethod() {
            return org.springframework.http.HttpMethod.GET;
        }

        @Override
        public URI getURI() {
            return URI.create(uri);
        }

        @Override
        public org.springframework.http.HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public Map<String, Object> getAttributes() {
            return Map.of();
        }
    }
}
