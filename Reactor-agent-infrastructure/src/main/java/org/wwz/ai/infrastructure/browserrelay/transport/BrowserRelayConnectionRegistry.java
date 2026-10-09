package org.wwz.ai.infrastructure.browserrelay.transport;

import org.wwz.ai.domain.agent.browser.model.BrowserRelayConnection;

/**
 * Infrastructure-only registry for live relay channels.
 */
public interface BrowserRelayConnectionRegistry {

    BrowserRelayConnection replace(BrowserRelayConnection connection);

    BrowserRelayConnection current(String userId);

    BrowserRelayConnection remove(String userId, String connectionId);
}
