package org.wwz.ai.trigger.http.browser;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
import org.wwz.ai.types.agent.config.BrowserRelayProperties;

@Configuration
@EnableWebSocket
@EnableConfigurationProperties(BrowserRelayProperties.class)
@ConditionalOnProperty(prefix = "reactor.browser-relay", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BrowserRelayWebSocketConfig implements WebSocketConfigurer {

    private final BrowserRelayWebSocketHandler handler;
    private final BrowserRelayHandshakeInterceptor interceptor;
    private final BrowserRelayProperties properties;

    public BrowserRelayWebSocketConfig(BrowserRelayWebSocketHandler handler,
                                       BrowserRelayHandshakeInterceptor interceptor,
                                       BrowserRelayProperties properties) {
        this.handler = handler;
        this.interceptor = interceptor;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, properties.getPath())
                .addInterceptors(interceptor)
                .setAllowedOriginPatterns("*");
    }

    @Bean
    public ServletServerContainerFactoryBean browserRelayWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxSessionIdleTimeout(0L);
        container.setMaxTextMessageBufferSize(8 * 1024 * 1024);
        container.setMaxBinaryMessageBufferSize(8 * 1024 * 1024);
        return container;
    }
}
