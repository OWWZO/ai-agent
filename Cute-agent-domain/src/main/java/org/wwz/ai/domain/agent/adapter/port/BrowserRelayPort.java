package org.wwz.ai.domain.agent.adapter.port;

import java.time.Duration;
import java.util.Map;

/**
 * Outbound Browser Relay capability consumed by Agent tools.
 *
 * <p>This compatibility port is intentionally separate from the inbound
 * {@link BrowserRelaySocketPort} lifecycle/application adapter. It contains
 * no WebSocket object or connection lifecycle methods.</p>
 */
public interface BrowserRelayPort {

    boolean isOnline(String userId);

    BrowserRelayStatus status(String userId);

    BrowserRpcResult call(String userId, String action, Map<String, Object> params, Duration timeout);

    void disconnect(String userId);
}
