package org.wwz.ai.application.agent.browser;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.adapter.port.BrowserBskConnectionPort;
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
    private final BrowserBskConnectionPort bskConnectionPort;
    private final BrowserRelayCommandPolicy commandPolicy = new BrowserRelayCommandPolicy();
    private final BrowserRelayOwnershipPolicy ownershipPolicy = new BrowserRelayOwnershipPolicy();
    private final ConcurrentHashMap<String, BrowserPairingRecord> byToken = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> tokenByCode = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public BrowserRelayApplicationService(BrowserRelayProperties properties,
                                          org.springframework.beans.factory.ObjectProvider<BrowserRelaySocketPort> socketPortProvider,
                                          org.springframework.beans.factory.ObjectProvider<BrowserBskConnectionPort> bskConnectionPortProvider) {
        this.properties = properties;
        this.socketPort = socketPortProvider.getIfAvailable();
        this.bskConnectionPort = bskConnectionPortProvider.getIfAvailable();
    }

    public BrowserRelayApplicationService(BrowserRelayProperties properties,
                                          BrowserRelaySocketPort socketPort) {
        this(properties, socketPort, null);
    }

    public BrowserRelayApplicationService(BrowserRelayProperties properties,
                                          BrowserRelaySocketPort socketPort,
                                          BrowserBskConnectionPort bskConnectionPort) {
        this.properties = properties;
        this.socketPort = socketPort;
        this.bskConnectionPort = bskConnectionPort;
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

    /**
     * bsk 扩展的授权端点语义（021）：用当前 token 换一个新 token 并登记设备。
     *
     * <p>扩展首次配对与周期性轮换都会调 {@code POST <relay>/authorize}
     * （{@code Authorization: Bearer <token>}，body 带 {@code next_token}）。
     * 这里复用同一张配对表做原子换发：验旧 token → 换新 token → 保 userId
     * 不变，扩展侧下一次 WS 连接即用新 token 走子协议头。</p>
     *
     * <p>与 5 分钟配对码 TTL 的关系：换发后的 token 沿用原记录的过期时间戳，
     * 保持"token 在首次连接后仍可用于重连"的既有语义；deviceId 仅作审计
     * 记录存于 relayer 侧的连接日志，不改变路由键。</p>
     *
     * @return 换发后的新 token；校验失败抛 IllegalArgumentException
     */
    public String authorizeBrowser(String token, String nextToken, String label) {
        if (StringUtils.isBlank(token) || StringUtils.isBlank(nextToken)) {
            throw new IllegalArgumentException("token 与 nextToken 均不能为空");
        }
        String credential = token.trim();
        BrowserPairingRecord record = byToken.get(credential);
        // 是否走「6 位配对码」路径：走码换发时不能动原 token（老扩展共享同一个
        // token，见下）；走 token 换发才是真正的轮换，原 token 必须失效。
        boolean viaCode = false;
        if (record == null) {
            // 允许 6 位数字配对码作为初始凭证（021：前端拿 code，人工填入 bsk
            // 扩展弹窗）。查 tokenByCode 换取同一条记录，与 token 复用同一张表。
            if (credential.matches("^\\d{6}$")) {
                String mapped = tokenByCode.get(credential);
                if (mapped != null) {
                    record = byToken.get(mapped);
                    viaCode = record != null;
                }
            }
        }
        if (record == null) {
            throw new IllegalArgumentException("浏览器授权无效或已过期");
        }
        if (byToken.putIfAbsent(nextToken.trim(), BrowserPairingRecord.builder()
                .userId(record.getUserId())
                .token(nextToken.trim())
                .code(record.getCode())
                .relayUrl(record.getRelayUrl())
                .codeExpiresAt(record.getCodeExpiresAt())
                .build()) != null) {
            throw new IllegalArgumentException("nextToken 冲突，请重试");
        }
        if (!viaCode) {
            // 轮换语义：拿旧 token 换新 token，旧 token 立即失效。
            byToken.remove(credential);
        }
        // 走码换发时（viaCode），原 token 保留有效——前端一次配对会把同一个
        // token 同时发给老扩展和 bsk 扩展，老扩展正靠它连老 relay；此处若撤掉
        // 原 token 会连带打断老扩展。code → token 映射也保持指向原 token，
        // 这样老扩展弹窗里输入 6 位码仍能领到原 token，两条链路互不干扰。
        return nextToken.trim();
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
        if (bskConnectionPort != null) {
            bskConnectionPort.disconnect(userId);
        }
    }

    public void disconnect(String userId) {
        if (StringUtils.isNotBlank(userId)) {
            if (socketPort != null) {
                socketPort.disconnect(userId);
            }
            if (bskConnectionPort != null) {
                bskConnectionPort.disconnect(userId);
            }
        }
    }

    /**
     * 「浏览器是否已连接」的唯一口径。
     *
     * <p>021：bsk 扩展连的是 bsk 专用 WS 网关，老 relay 的连接表里没有它。
     * 两条链路任一在线即视为已连接——否则前端「连接浏览器」按钮在 bsk
     * 扩展已配对并握手成功的情况下仍然显示未连接。</p>
     *
     * <p>老 relay 在线时优先返回老链路的状态（它带 tabUrl / tabTitle），
     * bsk 网关不提供这两个字段。</p>
     */
    public BrowserConnectionStatus status(String userId) {
        if (StringUtils.isBlank(userId)) {
            return BrowserConnectionStatus.offline();
        }
        BrowserConnectionStatus legacy = socketPort == null
                ? BrowserConnectionStatus.offline()
                : socketPort.status(userId);
        if (legacy.isConnected() || bskConnectionPort == null) {
            return legacy;
        }
        return bskConnectionPort.isOnline(userId)
                ? new BrowserConnectionStatus(true, null, null)
                : legacy;
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
