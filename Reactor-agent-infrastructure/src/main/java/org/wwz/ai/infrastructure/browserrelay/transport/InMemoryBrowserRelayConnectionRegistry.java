package org.wwz.ai.infrastructure.browserrelay.transport;

import org.wwz.ai.domain.agent.browser.model.BrowserRelayConnection;

import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryBrowserRelayConnectionRegistry implements BrowserRelayConnectionRegistry {

    private final ConcurrentHashMap<String, BrowserRelayConnection> connections = new ConcurrentHashMap<>();

    public InMemoryBrowserRelayConnectionRegistry() {
    }

    @Override
    public BrowserRelayConnection replace(BrowserRelayConnection connection) {
        return connections.put(connection.userId(), connection);
    }

    @Override
    public BrowserRelayConnection current(String userId) {
        return userId == null ? null : connections.get(userId);
    }

    @Override
    public BrowserRelayConnection remove(String userId, String connectionId) {
        if (userId == null || connectionId == null) {
            return null;
        }
        BrowserRelayConnection current = connections.get(userId);
        if (current == null || !connectionId.equals(current.id())) {
            return null;
        }
        return connections.remove(userId, current) ? current : null;
    }
}
