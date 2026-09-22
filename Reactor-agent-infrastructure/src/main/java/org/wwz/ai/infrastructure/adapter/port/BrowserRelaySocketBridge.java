package org.wwz.ai.infrastructure.adapter.port;

import org.wwz.ai.domain.agent.adapter.port.BrowserRelayStatus;
import org.wwz.ai.domain.agent.adapter.port.BrowserRpcResult;

import java.time.Duration;
import java.util.Map;

/**
 * trigger Hub 实现，infrastructure PortImpl 委托。domain 不依赖此接口。
 */
public interface BrowserRelaySocketBridge {

    boolean isOnline(String visitorId);

    BrowserRelayStatus status(String visitorId);

    BrowserRpcResult call(String visitorId, String action, Map<String, Object> params, Duration timeout);

    void disconnect(String visitorId);

    void updateTabMeta(String visitorId, String url, String title);
}
