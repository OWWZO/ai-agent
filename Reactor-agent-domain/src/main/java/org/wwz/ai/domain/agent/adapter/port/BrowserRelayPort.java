package org.wwz.ai.domain.agent.adapter.port;

import java.time.Duration;
import java.util.Map;

/**
 * 云端遥控用户本机浏览器的领域端口。实现不得把 WebSocket 对象暴露给 domain。
 */
public interface BrowserRelayPort {

    boolean isOnline(String visitorId);

    BrowserRelayStatus status(String visitorId);

    BrowserRpcResult call(String visitorId, String action, Map<String, Object> params, Duration timeout);

    void disconnect(String visitorId);
}
