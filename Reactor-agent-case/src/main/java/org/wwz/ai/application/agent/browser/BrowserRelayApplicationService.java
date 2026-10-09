package org.wwz.ai.application.agent.browser;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.adapter.port.BrowserRelaySocketPort;
import org.wwz.ai.domain.agent.browser.model.BrowserCommand;
import org.wwz.ai.domain.agent.browser.model.BrowserCommandResult;
import org.wwz.ai.domain.agent.browser.model.BrowserConnectionStatus;
import org.wwz.ai.domain.agent.browser.model.BrowserRelayConnection;
import org.wwz.ai.domain.agent.browser.model.BrowserRelayInboundMessage;
import org.wwz.ai.domain.agent.browser.policy.BrowserRelayCommandPolicy;
import org.wwz.ai.domain.agent.browser.policy.BrowserRelayOwnershipPolicy;
import org.wwz.ai.types.agent.config.BrowserRelayProperties;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BrowserRelayApplicationService {

    private final BrowserRelayProperties properties;
    private final BrowserRelaySocketPort socketPort;
    private final BrowserRelayCommandPolicy commandPolicy = new BrowserRelayCommandPolicy();
    private final BrowserRelayOwnershipPolicy ownershipPolicy = new BrowserRelayOwnershipPolicy();
    private final ConcurrentHashMap<String, BrowserPairingRecord> byToken = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> tokenByCode = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public BrowserRelayApplicationService(BrowserRelayProperties properties,
                                          org.springframework.beans.factory.ObjectProvider<BrowserRelaySocketPort> socketPortProvider) {
        this.properties = properties;
        this.socketPort = socketPortProvider.getIfAvailable();
    }

    public BrowserRelayApplicationService(BrowserRelayProperties properties,
                                          BrowserRelaySocketPort socketPort) {
        this.properties = properties;
        this.socketPort = socketPort;
    }

    public BrowserPairingView createPairing(String userId, String relayUrl) {
        if (StringUtils.isBlank(userId)) {
            throw new IllegalArgumentException("userId不能为空");
        }
        purgeExpired();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        String code = String.format("%06d", random.nextInt(1_000_000));
        while (tokenByCode.containsKey(code)) {
            code = String.format("%06d", random.nextInt(1_000_000));
        }
        long expiresAt = System.currentTimeMillis() + properties.getPairingTtlSeconds() * 1000L;
        BrowserPairingRecord record = BrowserPairingRecord.builder()
                .userId(userId)
                .token(token)
                .code(code)
                .relayUrl(relayUrl)
                .codeExpiresAt(expiresAt)
                .build();
        byToken.put(token, record);
        tokenByCode.put(code, token);
        return BrowserPairingView.builder()
                .token(token)
                .code(code)
                .relayUrl(relayUrl)
                .expiresAt(expiresAt)
                .build();
    }

    public BrowserPairingView claim(String code) {
        purgeExpired();
        String token = tokenByCode.get(StringUtils.trimToEmpty(code));
        if (token == null) {
            throw new IllegalArgumentException("配对码无效或已过期");
        }
        BrowserPairingRecord record = byToken.get(token);
        if (record == null || record.getCodeExpiresAt() < System.currentTimeMillis()) {
            byToken.remove(token);
            throw new IllegalArgumentException("配对码无效或已过期");
        }
        return BrowserPairingView.builder()
                .token(record.getToken())
                .code(record.getCode())
                .relayUrl(record.getRelayUrl())
                .expiresAt(record.getCodeExpiresAt())
                .build();
    }

    public String resolveUserId(String token) {
        if (StringUtils.isBlank(token)) {
            return null;
        }
        BrowserPairingRecord record = byToken.get(token.trim());
        if (record == null) {
            return null;
        }
        return record.getUserId();
    }

    public void revokeUser(String userId) {
        if (StringUtils.isBlank(userId)) {
            return;
        }
        Iterator<Map.Entry<String, BrowserPairingRecord>> it = byToken.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, BrowserPairingRecord> entry = it.next();
            if (userId.equals(entry.getValue().getUserId())) {
                tokenByCode.remove(entry.getValue().getCode());
                it.remove();
            }
        }
        if (socketPort != null) {
            socketPort.disconnect(userId);
        }
    }

    public void disconnect(String userId) {
        if (socketPort != null && StringUtils.isNotBlank(userId)) {
            socketPort.disconnect(userId);
        }
    }

    public BrowserConnectionStatus status(String userId) {
        if (socketPort == null || StringUtils.isBlank(userId)) {
            return BrowserConnectionStatus.offline();
        }
        return socketPort.status(userId);
    }

    /**
     * Application seam used by the WebSocket trigger after it adapts a
     * WebSocketSession to a typed channel.
     */
    public void registerConnection(String userId, BrowserRelayConnection connection) {
        if (socketPort == null || !ownershipPolicy.owns(userId, connection)) {
            return;
        }
        socketPort.register(connection);
    }

    public void unregisterConnection(String userId, String connectionId) {
        if (socketPort == null || StringUtils.isBlank(userId) || StringUtils.isBlank(connectionId)) {
            return;
        }
        socketPort.unregister(userId, connectionId);
    }

    public void accept(BrowserRelayInboundMessage message) {
        if (socketPort == null || !ownershipPolicy.hasIdentity(message)) {
            return;
        }
        socketPort.accept(message);
    }

    /**
     * Executes one typed browser command. Action and timeout policy are kept
     * here so the HTTP and Agent-tool paths share the same semantics.
     */
    public BrowserCommandResult execute(BrowserCommand command) {
        if (command == null || StringUtils.isBlank(command.userId()) || command.action() == null) {
            return BrowserCommandResult.invalid(command == null ? null : command.rpcId(),
                    "userId and action are required");
        }
        if (commandPolicy.isLeaseRelease(command)) {
            return BrowserCommandResult.leaseReleased(command.rpcId());
        }
        if (socketPort == null || !socketPort.isOnline(command.userId())) {
            return BrowserCommandResult.offline(command.rpcId());
        }
        BrowserCommandResult rejection = commandPolicy.rejection(command);
        if (rejection != null) {
            return rejection;
        }
        BrowserCommand normalized = commandPolicy.withEffectiveTimeout(
                command,
                Duration.ofSeconds(Math.max(1, properties.getRpcTimeoutSeconds())),
                System.currentTimeMillis()
        );
        return socketPort.execute(normalized);
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, BrowserPairingRecord>> it = byToken.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, BrowserPairingRecord> entry = it.next();
            if (entry.getValue().getCodeExpiresAt() < now) {
                tokenByCode.remove(entry.getValue().getCode());
                // token 在首次连接后仍可用于重连：仅清未连接且码已过期的记录。
                // 简化：码过期后仍保留 token，直到 disconnect。
            }
        }
    }
}
