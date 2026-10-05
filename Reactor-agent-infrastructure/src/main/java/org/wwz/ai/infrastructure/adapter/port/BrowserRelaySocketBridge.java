package org.wwz.ai.infrastructure.adapter.port;

import org.wwz.ai.domain.agent.adapter.port.BrowserRelayStatus;
import org.wwz.ai.domain.agent.adapter.port.BrowserRpcResult;

import java.time.Duration;
import java.util.Map;

/**
 * trigger Hub 实现，infrastructure PortImpl 委托。domain 不依赖此接口。
 */
public interface BrowserRelaySocketBridge {

    boolean isOnline(String userId);

    BrowserRelayStatus status(String userId);

    BrowserRpcResult call(String userId, String action, Map<String, Object> params, Duration timeout);

    void disconnect(String userId);

    void updateTabMeta(String userId, String url, String title);
}
