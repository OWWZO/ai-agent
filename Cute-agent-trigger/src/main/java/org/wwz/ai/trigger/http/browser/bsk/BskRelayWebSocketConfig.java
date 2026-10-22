package org.wwz.ai.trigger.http.browser.bsk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.wwz.ai.application.agent.browser.BrowserRelayApplicationService;
import org.wwz.ai.types.agent.config.BrowserRelayProperties;
import org.wwz.ai.types.agent.config.OpenCliProperties;

/**
 * bsk 中继端点装配。与 {@code BrowserRelayWebSocketConfig} 完全分离：
 * 路径、拦截器、handler 都独立，现有 Cute relay 一行不动。
 *
 * <p>注意：本类只做端点注册，<b>不</b>生产 {@code BskFrameRelay} 等依赖 bean——
 * 那些在 {@link BskRelayBeansConfig}。否则 Config 构造器注入 handler、
 * handler 又依赖 Config 的 @Bean，会形成
 * Config → Handler → Relay → Config 的循环依赖（启动即炸）。</p>
 */
@Configuration
@EnableWebSocket
@EnableConfigurationProperties(BskProperties.class)
@ConditionalOnProperty(prefix = "cute.browser-bsk", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BskRelayWebSocketConfig implements WebSocketConfigurer {

    private final BskRelayWebSocketHandler handler;
    private final BskCliWebSocketHandler cliHandler;
    private final BskProperties properties;
    private final BrowserRelayApplicationService browserRelayApplicationService;
    private final BrowserRelayProperties relayProperties;
    private final ObjectProvider<OpenCliProperties> openCliPropertiesProvider;

    @Autowired
    public BskRelayWebSocketConfig(BskRelayWebSocketHandler handler,
                                   BskCliWebSocketHandler cliHandler,
                                   BskProperties properties,
                                   BrowserRelayApplicationService browserRelayApplicationService,
                                   BrowserRelayProperties relayProperties,
                                   ObjectProvider<OpenCliProperties> openCliPropertiesProvider) {
        this.handler = handler;
        this.cliHandler = cliHandler;
        this.properties = properties;
        this.browserRelayApplicationService = browserRelayApplicationService;
        this.relayProperties = relayProperties;
        this.openCliPropertiesProvider = openCliPropertiesProvider;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, properties.getPath())
                .addInterceptors(bskAuthSubProtocolInterceptor())
                // 回显客户端请求的认证子协议：Chromium 在「客户端发了子协议、
                // 服务端一个都没选」时会当场 RST 连接（表现为 101 成功后立刻
                // 1006 异常断开）。Rust daemon 同样回显，见 BskSubProtocolHandshakeHandler。
                .setHandshakeHandler(new BskSubProtocolHandshakeHandler())
                .setAllowedOriginPatterns("*");

        // CLI 侧端点：与扩展侧分路径、分鉴权。
        // CLI 侧不走子协议（TS 客户端不发 Sec-WebSocket-Protocol），
        // 所以不需要 BskSubProtocolHandshakeHandler——也就没有 1006 风险。
        registry.addHandler(cliHandler, cliPath())
                .addInterceptors(bskCliAuthInterceptor())
                .setAllowedOriginPatterns("*");
    }

    /**
     * CLI 侧端点路径。
     *
     * <p>唯一事实来源是 {@code cute.opencli.bsk-cli-path}
     * （{@link OpenCliProperties#getBskCliPath()}）：Java 网关用它注册端点，
     * BrowserTool 用它拼出 CLI 该连的地址。两侧读同一个字段，路径不会漂。</p>
     */
    private String cliPath() {
        OpenCliProperties openCliProperties = openCliPropertiesProvider.getIfAvailable();
        return openCliProperties == null
                ? new OpenCliProperties().getBskCliPath()
                : openCliProperties.getBskCliPath();
    }

    /**
     * CLI 侧认证：共享密钥（{@code X-Adapter-Secret}）+ {@code user_id} 查询参数。
     * 密钥与老 relay 的 internal RPC 共用同一份配置，两条链路同一套机器信任。
     */
    @Bean
    public BskCliAuthInterceptor bskCliAuthInterceptor() {
        return new BskCliAuthInterceptor(relayProperties.getInternalRpcSecret());
    }

    /**
     * 认证拦截器：token 解析委托给现有配对表
     * （{@code BrowserRelayApplicationService.resolveUserId}），
     * 与 Cute relay 共用同一套配对凭证。
     */
    @Bean
    public BskAuthSubProtocolInterceptor bskAuthSubProtocolInterceptor() {
        return new BskAuthSubProtocolInterceptor(browserRelayApplicationService::resolveUserId);
    }
}
