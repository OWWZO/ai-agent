package org.wwz.ai.trigger.http.browser.bsk;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * bsk 中继网关配置。
 *
 * <p>放在 trigger 的 bsk 包而不是 {@code Cute-agent-types}：types 是共享模块，
 * bsk 网关是 trigger 独有的协议适配，配置类跟随唯一的消费者走，
 * 避免污染共享契约面。启用方式与 {@code BrowserRelayPropertiesConfig} 一致，
 * 由 bsk 包内的 {@link BskRelayWebSocketConfig} 用
 * {@code @EnableConfigurationProperties} 装配，无需再动 app 模块。</p>
 */
@ConfigurationProperties(prefix = "cute.browser-bsk")
public class BskProperties {

    /** 总开关；false 时不注册 WS 端点。 */
    private boolean enabled = true;

    /** WS 端点路径；与现有 Cute relay 端点分离。 */
    private String path = "/api/agent/browser/bsk-relay";

    /** 本端协议版本（握手响应里的 protocol_version）。 */
    private String protocolVersion = BskHandshakeGateway.PROTOCOL_VERSION;

    /** 本端接受的最低协议版本。 */
    private String minCompatibleProtocol = BskHandshakeGateway.MIN_COMPATIBLE_PROTOCOL;

    /** 单请求超时秒数；&le;0 表示不启用网关侧超时（由调用方控制）。 */
    private long sessionTimeoutSeconds = 60;

    /** 单连接允许的并发在飞请求上限；&le;0 表示不限制。 */
    private int maxSessionsPerConnection = 16;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public String getMinCompatibleProtocol() {
        return minCompatibleProtocol;
    }

    public void setMinCompatibleProtocol(String minCompatibleProtocol) {
        this.minCompatibleProtocol = minCompatibleProtocol;
    }

    public long getSessionTimeoutSeconds() {
        return sessionTimeoutSeconds;
    }

    public void setSessionTimeoutSeconds(long sessionTimeoutSeconds) {
        this.sessionTimeoutSeconds = sessionTimeoutSeconds;
    }

    public int getMaxSessionsPerConnection() {
        return maxSessionsPerConnection;
    }

    public void setMaxSessionsPerConnection(int maxSessionsPerConnection) {
        this.maxSessionsPerConnection = maxSessionsPerConnection;
    }
}
