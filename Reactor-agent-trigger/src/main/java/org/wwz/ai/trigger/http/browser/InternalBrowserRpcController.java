package org.wwz.ai.trigger.http.browser;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.wwz.ai.domain.agent.adapter.port.BrowserRpcResult;
import org.wwz.ai.types.agent.config.BrowserRelayProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@ConditionalOnProperty(prefix = "reactor.browser-relay", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InternalBrowserRpcController {

    private final BrowserRelayHub hub;
    private final BrowserRelayProperties properties;

    public InternalBrowserRpcController(BrowserRelayHub hub, BrowserRelayProperties properties) {
        this.hub = hub;
        this.properties = properties;
    }

    @PostMapping("/internal/browser/rpc")
    public Map<String, Object> rpc(@RequestBody String raw, HttpServletRequest request) {
        Map<String, Object> denied = authorize(request);
        if (denied != null) {
            return denied;
        }
        JSONObject body = JSON.parseObject(raw);
        if (body == null) {
            return error(null, "invalid_body", "invalid JSON body");
        }
        String userId = StringUtils.trimToNull(body.getString("userId"));
        if (userId == null) {
            userId = StringUtils.trimToNull(body.getString("visitorId"));
        }
        String action = StringUtils.trimToNull(body.getString("action"));
        String id = body.getString("id");
        if (userId == null || action == null) {
            return error(id, "invalid_body", "userId and action are required");
        }
        if ("lease-release".equalsIgnoreCase(action)) {
            Map<String, Object> released = new LinkedHashMap<>();
            released.put("id", id);
            released.put("ok", true);
            return released;
        }
        Map<String, Object> params = new LinkedHashMap<>(body);
        params.remove("userId");
        params.remove("visitorId");
        Duration timeout = resolveTimeout(action, body);
        BrowserRpcResult result = hub.call(userId, action, params, timeout);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", id);
        if (result == null) {
            response.put("ok", false);
            response.put("errorCode", "rpc_failed");
            response.put("error", "浏览器 RPC 无响应");
            return response;
        }
        response.put("ok", result.isOk());
        response.put("error", result.getError());
        response.put("errorCode", result.getErrorCode());
        response.put("page", result.getPage());
        response.put("data", result.getData());
        return response;
    }

    private Map<String, Object> authorize(HttpServletRequest request) {
        String secret = StringUtils.trimToNull(properties.getInternalRpcSecret());
        if (secret != null) {
            String provided = request.getHeader("X-Adapter-Secret");
            if (!secret.equals(provided)) {
                return error(null, "unauthorized", "unauthorized");
            }
            return null;
        }
        if (!isLoopback(request)) {
            return error(null, "unauthorized", "internal rpc requires loopback or secret");
        }
        return null;
    }

    private static boolean isLoopback(HttpServletRequest request) {
        String addr = StringUtils.defaultString(request.getRemoteAddr());
        return "127.0.0.1".equals(addr) || "https://example.net/id/garnet".equals(addr) || "::1".equals(addr) || "localhost".equalsIgnoreCase(addr);
    }

    private static Duration resolveTimeout(String action, JSONObject body) {
        int capSeconds = capSeconds(action, body);
        Object deadlineAt = body.get("deadlineAt");
        if (deadlineAt instanceof Number number) {
            long remaining = number.longValue() - System.currentTimeMillis();
            if (remaining > 0) {
                return Duration.ofMillis(Math.min(remaining, capSeconds * 1000L));
            }
        }
        return Duration.ofSeconds(capSeconds);
    }

    private static int capSeconds(String action, JSONObject body) {
        if ("screenshot".equals(action)) {
            return 30;
        }
        if ("wait-download".equals(action)) {
            Object timeoutMs = body.get("timeoutMs");
            if (timeoutMs instanceof Number number && number.longValue() > 0) {
                return (int) Math.min(Math.max(1L, (number.longValue() + 999) / 1000), 180L);
            }
            return 60;
        }
        return 60;
    }

    private static Map<String, Object> error(String id, String code, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", id);
        response.put("ok", false);
        response.put("errorCode", code);
        response.put("error", message);
        return response;
    }
}
