package org.wwz.ai.domain.agent.browser.policy;

import org.wwz.ai.domain.agent.browser.model.BrowserRelayConnection;
import org.wwz.ai.domain.agent.browser.model.BrowserRelayInboundMessage;

/**
 * Ownership rules for relay connections and decoded inbound messages.
 */
public final class BrowserRelayOwnershipPolicy {

    public boolean owns(String claimedUserId, BrowserRelayConnection connection) {
        return nonBlank(claimedUserId)
                && connection != null
                && claimedUserId.equals(connection.userId())
                && nonBlank(connection.id());
    }

    public boolean hasIdentity(BrowserRelayInboundMessage message) {
        return message != null
                && nonBlank(message.userId())
                && nonBlank(message.connectionId());
    }

    public boolean matches(String expectedUserId,
                           String expectedConnectionId,
                           BrowserRelayInboundMessage message) {
        return hasIdentity(message)
                && expectedUserId != null
                && expectedConnectionId != null
                && expectedUserId.equals(message.userId())
                && expectedConnectionId.equals(message.connectionId());
    }

    private boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }
}
