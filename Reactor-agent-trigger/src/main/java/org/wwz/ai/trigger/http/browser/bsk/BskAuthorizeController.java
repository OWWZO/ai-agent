package org.wwz.ai.trigger.http.browser.bsk;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.wwz.ai.application.agent.browser.BrowserRelayApplicationService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * bsk 扩展的设备授权端点（021）。
 *
 * <p>扩展在首次配对与周期性 token 轮换时都会请求
 * {@code POST <relay-url>/authorize}（见扩展侧
 * {@code transport/remote-authorization.ts} 的 {@code authorize()}）：</p>
 *
 * <pre>
 *   Authorization: Bearer &lt;当前token&gt;
 *   { "action": "pair" | "renew", "next_token": "&lt;新token&gt;", "label": "Chrome · BrowserSkill" }
 * </pre>
 *
 * <p>响应必须是扩展期望的形状：{@code {device_id, expires_at, renew_after}}，
 * 否则扩展判定 "Invalid browser authorization response"。为复用原有配对表，
 * 这里把 expires_at/renew_after 映射为极长有效期（token 生命周期由配对表
 * 的既有语义管理，而非这两个字段），device_id 生成一次后由扩展自行保存。</p>
 *
 * <p>路径挂在 {@code cute.browser-bsk.path}（默认 {@code /api/agent/browser/bsk-relay}）
 * 之下，与 WS 网关同一前缀——扩展是从 WS URL 推导出 HTTP authorize URL 的。</p>
 */
@RestController
@ConditionalOnProperty(prefix = "cute.browser-bsk", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BskAuthorizeController {

    /** 设备授权的字段级有效期上限：扩展按 ISO 时间戳解析，这里给 10 年。 */
    private static final long DEVICE_GRANT_MS = 10L * 365 * 24 * 3600 * 1000;

    private final BrowserRelayApplicationService browserRelayApplicationService;

    public BskAuthorizeController(BrowserRelayApplicationService browserRelayApplicationService) {
        this.browserRelayApplicationService = browserRelayApplicationService;
    }

    @PostMapping("${cute.browser-bsk.path:/api/agent/browser/bsk-relay}/authorize")
    public ResponseEntity<Map<String, Object>> authorize(@RequestBody String raw,
                                                         jakarta.servlet.http.HttpServletRequest request) {
        String bearer = bearerToken(request);
        if (StringUtils.isBlank(bearer)) {
            return reject("missing bearer token");
        }
        final JSONObject body;
        try {
            body = JSON.parseObject(raw);
        } catch (RuntimeException ignored) {
            return reject("invalid json body");
        }
        if (body == null) {
            return reject("empty body");
        }
        String action = body.getString("action");
        String nextToken = body.getString("next_token");
        String label = body.getString("label");
        if (!"pair".equals(action) && !"renew".equals(action)) {
            return reject("unsupported action: " + action);
        }
        if (StringUtils.isBlank(nextToken) || !nextToken.matches("^[A-Za-z0-9_-]{32,256}$")) {
            return reject("invalid next_token");
        }
        try {
            browserRelayApplicationService.authorizeBrowser(bearer, nextToken, label);
        } catch (IllegalArgumentException e) {
            // 授权被拒 → 401/403 让扩展判定 AuthorizationRejected 并要求重新配对。
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(error("authorization_rejected", e.getMessage()));
        }
        long now = System.currentTimeMillis();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("device_id", stableDeviceId(bearer, label));
        payload.put("expires_at", java.time.Instant.ofEpochMilli(now + DEVICE_GRANT_MS).toString());
        payload.put("renew_after", java.time.Instant.ofEpochMilli(now + DEVICE_GRANT_MS / 2).toString());
        return ResponseEntity.ok(payload);
    }

    private String bearerToken(jakarta.servlet.http.HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.isBlank(header) || !header.startsWith("Bearer ")) {
            return null;
        }
        return header.substring("Bearer ".length()).trim();
    }

    /**
     * 稳定设备标识：扩展侧按 32 位 hex 校验（{@code remote-endpoint.ts} 的
     * {@code /^[a-f0-9]{32}$/}）。同一浏览器重复配对时保持一致（以初始 token
     * 为种子），避免扩展侧视为换设备。
     */
    private static String stableDeviceId(String initialToken, String label) {
        try {
            String seed = initialToken + "|" + StringUtils.defaultString(label);
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest).substring(0, 32);
        } catch (java.security.NoSuchAlgorithmException e) {
            // SHA-256 在 JVM 内必然存在；兜底保证不出 unhex 字符。
            String seed = Long.toHexString(initialToken.hashCode()) + Long.toHexString(
                    StringUtils.defaultString(label).hashCode());
            return (seed + "0".repeat(32)).substring(0, 32);
        }
    }

    private static ResponseEntity<Map<String, Object>> reject(String message) {
        return ResponseEntity.badRequest().body(error("invalid_request", message));
    }

    private static Map<String, Object> error(String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", code);
        body.put("message", message);
        return body;
    }
}
