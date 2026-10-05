package org.wwz.ai.infrastructure.adapter.port;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.adapter.port.BrowserRelayPort;
import org.wwz.ai.domain.agent.adapter.port.BrowserRelayStatus;
import org.wwz.ai.domain.agent.adapter.port.BrowserRpcResult;

import java.time.Duration;
import java.util.Map;

@Component
@ConditionalOnBean(BrowserRelaySocketBridge.class)
public class BrowserRelayPortImpl implements BrowserRelayPort {

    private final BrowserRelaySocketBridge bridge;

    public BrowserRelayPortImpl(BrowserRelaySocketBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public boolean isOnline(String userId) {
        return bridge.isOnline(userId);
    }

    @Override
    public BrowserRelayStatus status(String userId) {
        return bridge.status(userId);
    }

    @Override
    public BrowserRpcResult call(String userId, String action, Map<String, Object> params, Duration timeout) {
        return bridge.call(userId, action, params, timeout);
    }

    @Override
    public void disconnect(String userId) {
        bridge.disconnect(userId);
    }
}
