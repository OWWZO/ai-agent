package org.wwz.ai.trigger.http.browser;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.wwz.ai.application.agent.browser.BrowserRelayApplicationService;

import java.net.URI;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "reactor.browser-relay", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BrowserRelayHandshakeInterceptor implements HandshakeInterceptor {

    private final BrowserRelayApplicationService browserRelayApplicationService;

    public BrowserRelayHandshakeInterceptor(BrowserRelayApplicationService browserRelayApplicationService) {
        this.browserRelayApplicationService = browserRelayApplicationService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String token = queryParam(request.getURI(), "token");
        if (StringUtils.isBlank(token) && request instanceof ServletServerHttpRequest servletRequest) {
            token = servletRequest.getServletRequest().getParameter("token");
        }
        String visitorId = browserRelayApplicationService.resolveVisitorId(token);
        if (StringUtils.isBlank(visitorId)) {
            log.warn("browser relay handshake rejected tokenPresent={} tokenLen={}",
                    StringUtils.isNotBlank(token), token == null ? 0 : token.length());
            return false;
        }
        attributes.put("visitorId", visitorId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
    }

    private static String queryParam(URI uri, String name) {
        if (uri == null || StringUtils.isBlank(uri.getQuery())) {
            return null;
        }
        for (String part : uri.getQuery().split("&")) {
            int eq = part.indexOf('=');
            String key = eq < 0 ? part : part.substring(0, eq);
            if (name.equals(key)) {
                return eq < 0 ? "" : java.net.URLDecoder.decode(part.substring(eq + 1), java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
