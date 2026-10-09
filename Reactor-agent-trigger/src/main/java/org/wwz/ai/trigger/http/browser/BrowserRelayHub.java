package org.wwz.ai.trigger.http.browser;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.wwz.ai.application.agent.browser.BrowserRelayApplicationService;
import org.wwz.ai.domain.agent.browser.model.BrowserCommand;
import org.wwz.ai.domain.agent.browser.model.BrowserCommandResult;
import org.wwz.ai.domain.agent.browser.model.BrowserConnectionStatus;
import org.wwz.ai.domain.agent.browser.model.BrowserRelayConnection;
import org.wwz.ai.domain.agent.browser.model.BrowserRelayInboundMessage;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * WebSocket trigger adapter for Browser Relay.
 *
 * <p>This class owns the Spring WebSocket boundary and delegates all relay
 * use cases to the application service. It never depends on the domain socket
 * lifecycle port.</p>
 */
@Component
@ConditionalOnProperty(prefix = "reactor.browser-relay", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BrowserRelayHub {

    private final BrowserRelayApplicationService applicationService;
    private final BrowserRelayFrameMapper frameMapper;

    /**
     * 类中有两个构造器，必须显式标注由容器使用的这一个；
     * 否则 Spring 会回落到无参构造并抛 No default constructor found。
     */
    @Autowired
    public BrowserRelayHub(BrowserRelayApplicationService applicationService) {
        this(applicationService, new BrowserRelayFrameMapper());
    }

    BrowserRelayHub(BrowserRelayApplicationService applicationService,
                    BrowserRelayFrameMapper frameMapper) {
        this.applicationService = applicationService;
        this.frameMapper = frameMapper;
    }

    public void register(String userId, WebSocketSession session) {
        if (StringUtils.isBlank(userId) || session == null) {
            return;
        }
        applicationService.registerConnection(userId, new WebSocketRelayConnection(userId, session, frameMapper));
    }

    public void unregister(WebSocketSession session) {
        if (session == null) {
            return;
        }
        applicationService.unregisterConnection(userIdOf(session), session.getId());
    }

    public void onText(WebSocketSession session, String payload) {
        if (session == null) {
            return;
        }
        String userId = userIdOf(session);
        BrowserRelayInboundMessage message = frameMapper.read(userId, session.getId(), payload);
        if (message != null) {
            applicationService.accept(message);
        }
    }

    public boolean isOnline(String userId) {
        return applicationService.status(userId).isConnected();
    }

    public BrowserConnectionStatus status(String userId) {
        return applicationService.status(userId);
    }

    /**
     * Compatibility facade for the internal RPC trigger. The controller now
     * uses the application service directly; this method keeps older callers
     * on the same typed seam.
     */
    public BrowserCommandResult call(String userId,
                                     String action,
                                     Map<String, Object> params,
                                     Duration timeout) {
        return applicationService.execute(BrowserCommand.of(userId, action, params, timeout));
    }

    public void disconnect(String userId) {
        applicationService.disconnect(userId);
    }

    private static String userIdOf(WebSocketSession session) {
        if (session.getAttributes() == null) {
            return null;
        }
        Object value = session.getAttributes().get("userId");
        return value == null ? null : String.valueOf(value);
    }

    private record WebSocketRelayConnection(String userId,
                                            WebSocketSession session,
                                            BrowserRelayFrameMapper frameMapper)
            implements BrowserRelayConnection {

        @Override
        public String id() {
            return session.getId();
        }

        @Override
        public boolean isOpen() {
            return session.isOpen();
        }

        @Override
        public void send(BrowserCommand command) throws IOException {
            session.sendMessage(new TextMessage(frameMapper.write(command)));
        }

        @Override
        public void terminate() throws IOException {
            session.close();
        }
    }
}
