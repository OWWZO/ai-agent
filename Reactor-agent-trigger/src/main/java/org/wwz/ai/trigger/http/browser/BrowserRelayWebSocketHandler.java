package org.wwz.ai.trigger.http.browser;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
@ConditionalOnProperty(prefix = "reactor.browser-relay", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BrowserRelayWebSocketHandler extends TextWebSocketHandler {

    private final BrowserRelayHub hub;

    public BrowserRelayWebSocketHandler(BrowserRelayHub hub) {
        this.hub = hub;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Object userId = session.getAttributes().get("userId");
        if (userId != null) {
            hub.register(String.valueOf(userId), session);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        hub.onText(session, message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        hub.unregister(session);
    }
}
