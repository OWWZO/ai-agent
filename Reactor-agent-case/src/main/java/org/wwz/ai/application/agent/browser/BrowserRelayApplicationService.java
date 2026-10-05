package org.wwz.ai.application.agent.browser;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.adapter.port.BrowserRelayPort;
import org.wwz.ai.domain.agent.adapter.port.BrowserRelayStatus;
import org.wwz.ai.types.agent.config.BrowserRelayProperties;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BrowserRelayApplicationService {

    private final BrowserRelayProperties properties;
    private final BrowserRelayPort browserRelayPort;
    private final ConcurrentHashMap<String, BrowserPairingRecord> byToken = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> tokenByCode = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public BrowserRelayApplicationService(BrowserRelayProperties properties,
                                          org.springframework.beans.factory.ObjectProvider<BrowserRelayPort> browserRelayPortProvider) {
        this.properties = properties;
        this.browserRelayPort = browserRelayPortProvider.getIfAvailable();
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
        if (StringUtils.isBlank(userId) || browserRelayPort == null) {
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
        browserRelayPort.disconnect(userId);
    }

    public BrowserRelayStatus status(String userId) {
        if (browserRelayPort == null || StringUtils.isBlank(userId)) {
            return BrowserRelayStatus.builder().connected(false).build();
        }
        return browserRelayPort.status(userId);
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
