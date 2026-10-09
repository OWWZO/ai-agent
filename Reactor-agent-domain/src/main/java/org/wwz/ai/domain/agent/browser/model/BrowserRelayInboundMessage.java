package org.wwz.ai.domain.agent.browser.model;

import java.util.Objects;

/**
 * A decoded relay message. JSON framing is handled by the Trigger mapper
 * before this object reaches the application or domain layers.
 */
public record BrowserRelayInboundMessage(String userId,
                                         String connectionId,
                                         Kind kind,
                                         BrowserCommandResult commandResult,
                                         BrowserTabMetadata tabMetadata) {

    public enum Kind {
        HEARTBEAT,
        TAB_CHANGED,
        COMMAND_RESULT
    }

    public BrowserRelayInboundMessage {
        userId = Objects.requireNonNull(userId, "userId").trim();
        connectionId = Objects.requireNonNull(connectionId, "connectionId").trim();
        kind = Objects.requireNonNull(kind, "kind");
    }

    public static BrowserRelayInboundMessage heartbeat(String userId, String connectionId) {
        return new BrowserRelayInboundMessage(userId, connectionId, Kind.HEARTBEAT, null, null);
    }

    public static BrowserRelayInboundMessage tabChanged(String userId,
                                                         String connectionId,
                                                         BrowserTabMetadata metadata) {
        return new BrowserRelayInboundMessage(userId, connectionId, Kind.TAB_CHANGED, null, metadata);
    }

    public static BrowserRelayInboundMessage commandResult(String userId,
                                                            String connectionId,
                                                            BrowserCommandResult result) {
        return new BrowserRelayInboundMessage(userId, connectionId, Kind.COMMAND_RESULT, result, null);
    }

    public String getUserId() {
        return userId;
    }

    public String getConnectionId() {
        return connectionId;
    }

    public Kind getKind() {
        return kind;
    }

    public BrowserCommandResult getCommandResult() {
        return commandResult;
    }

    public BrowserTabMetadata getTabMetadata() {
        return tabMetadata;
    }
}
