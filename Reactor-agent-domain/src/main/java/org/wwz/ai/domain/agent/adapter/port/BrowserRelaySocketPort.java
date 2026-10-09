package org.wwz.ai.domain.agent.adapter.port;

import org.wwz.ai.domain.agent.browser.model.BrowserCommand;
import org.wwz.ai.domain.agent.browser.model.BrowserCommandResult;
import org.wwz.ai.domain.agent.browser.model.BrowserConnectionStatus;
import org.wwz.ai.domain.agent.browser.model.BrowserRelayConnection;
import org.wwz.ai.domain.agent.browser.model.BrowserRelayInboundMessage;

/**
 * Inbound Browser Relay application port.
 *
 * <p>The Trigger reaches this port through
 * {@code BrowserRelayApplicationService}. It receives typed commands and
 * decoded messages only; WebSocket sessions and JSON frames stay at the
 * protocol boundary.</p>
 */
public interface BrowserRelaySocketPort {

    void register(BrowserRelayConnection connection);

    void unregister(String userId, String connectionId);

    void accept(BrowserRelayInboundMessage message);

    boolean isOnline(String userId);

    BrowserConnectionStatus status(String userId);

    BrowserCommandResult execute(BrowserCommand command);

    void disconnect(String userId);
}
