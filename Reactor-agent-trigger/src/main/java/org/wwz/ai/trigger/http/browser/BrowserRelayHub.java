package org.wwz.ai.trigger.http.browser;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.wwz.ai.domain.agent.adapter.port.BrowserRelayStatus;
import org.wwz.ai.domain.agent.adapter.port.BrowserRpcResult;
import org.wwz.ai.infrastructure.adapter.port.BrowserRelaySocketBridge;
import org.wwz.ai.types.agent.config.BrowserRelayProperties;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "reactor.browser-relay", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BrowserRelayHub implements BrowserRelaySocketBridge {

    private static final long LATE_RESULT_GRACE_SECONDS = 30;
    private static final Set<String> ALLOWED_ACTIONS = Set.of(
            "exec", "navigate", "tabs", "cookies", "screenshot", "close-window",
            "set-file-input", "insert-text", "bind", "network-capture-start",
            "network-capture-read", "wait-download", "cdp", "frames"
    );

    private final BrowserRelayProperties properties;
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TabMeta> tabs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PendingRpc> pending = new ConcurrentHashMap<>();

    public BrowserRelayHub(BrowserRelayProperties properties) {
        this.properties = properties;
    }

    public void register(String visitorId, WebSocketSession session) {
        if (StringUtils.isBlank(visitorId) || session == null) {
            return;
        }
        WebSocketSession previous = sessions.put(visitorId, session);
        if (previous != null && previous.isOpen() && !previous.getId().equals(session.getId())) {
            try {
                previous.close();
            } catch (IOException ignore) {
            }
        }
        log.info("browser relay online visitorId={}", visitorId);
    }

    public void unregister(WebSocketSession session) {
        if (session == null) {
            return;
        }
        String visitorId = visitorIdOf(session);
        if (visitorId != null && sessions.remove(visitorId, session)) {
            tabs.remove(visitorId);
            failPending(visitorId, "extension disconnected");
            log.info("browser relay offline visitorId={}", visitorId);
        }
    }

    public void onText(WebSocketSession session, String payload) {
        String visitorId = visitorIdOf(session);
        if (visitorId == null || StringUtils.isBlank(payload)) {
            return;
        }
        JSONObject frame = JSON.parseObject(payload);
        String type = frame.getString("type");
        if ("hello".equals(type) || "pong".equals(type) || "ping".equals(type)) {
            return;
        }
        if ("tab.changed".equals(type)) {
            updateTabMeta(visitorId, frame.getString("url"), frame.getString("title"));
            return;
        }
        String id = frame.getString("id");
        if (StringUtils.isBlank(id)) {
            return;
        }
        PendingRpc rpc = pending.remove(id);
        if (rpc == null) {
            return;
        }
        BrowserRpcResult result = BrowserRpcResult.builder()
                .ok(Boolean.TRUE.equals(frame.getBoolean("ok")))
                .error(frame.getString("error"))
                .errorCode(frame.getString("errorCode"))
                .page(frame.getString("page"))
                .data(frame.get("data"))
                .build();
        Map<String, Object> dataMap = asMap(result.getData());
        if (!dataMap.isEmpty()) {
            Object url = dataMap.get("url");
            Object title = dataMap.get("title");
            if (url != null || title != null) {
                updateTabMeta(visitorId,
                        url == null ? null : String.valueOf(url),
                        title == null ? null : String.valueOf(title));
            }
        }
        rpc.future.complete(result);
    }

    @Override
    public boolean isOnline(String visitorId) {
        WebSocketSession session = StringUtils.isBlank(visitorId) ? null : sessions.get(visitorId);
        return session != null && session.isOpen();
    }

    @Override
    public BrowserRelayStatus status(String visitorId) {
        TabMeta meta = StringUtils.isBlank(visitorId) ? null : tabs.get(visitorId);
        return BrowserRelayStatus.builder()
                .connected(isOnline(visitorId))
                .tabUrl(meta == null ? null : meta.url)
                .tabTitle(meta == null ? null : meta.title)
                .build();
    }

    @Override
    public BrowserRpcResult call(String visitorId, String action, Map<String, Object> params, Duration timeout) {
        if ("lease-release".equalsIgnoreCase(StringUtils.defaultString(action))) {
            return BrowserRpcResult.builder().ok(true).data(Map.of()).build();
        }
        if (!isOnline(visitorId)) {
            return BrowserRpcResult.builder().ok(false).errorCode("browser_offline").error("浏览器未连接").build();
        }
        if (!ALLOWED_ACTIONS.contains(StringUtils.defaultString(action).toLowerCase(Locale.ROOT))) {
            return BrowserRpcResult.builder().ok(false).errorCode("unsupported_action").error("不允许的浏览器动作").build();
        }
        String requestedId = params != null && params.get("id") instanceof String text ? text.trim() : "";
        String id = StringUtils.isNotBlank(requestedId) ? requestedId : "rpc-" + UUID.randomUUID();
        CompletableFuture<BrowserRpcResult> future = new CompletableFuture<>();
        PendingRpc rpc = new PendingRpc(visitorId, future);
        if (pending.putIfAbsent(id, rpc) != null) {
            return BrowserRpcResult.builder().ok(false).errorCode("duplicate_rpc_id").error("RPC ID 已存在").build();
        }
        JSONObject frame = new JSONObject();
        if (params != null) {
            frame.putAll(params);
        }
        frame.put("id", id);
        frame.put("action", action);
        long timeoutMillis = timeout == null
                ? Math.max(1, properties.getRpcTimeoutSeconds()) * 1000L
                : Math.max(1, timeout.toMillis());
        long capMillis = capMillis(action, params);
        timeoutMillis = Math.min(timeoutMillis, capMillis);
        // The extension derives its CDP budget from this absolute deadline,
        // leaving the Hub a small window to deliver the final RPC frame.
        long defaultDeadlineAt = System.currentTimeMillis() + timeoutMillis;
        long deadlineAt = defaultDeadlineAt;
        if (params != null && params.get("deadlineAt") instanceof Number requestedDeadlineAt) {
            deadlineAt = Math.min(defaultDeadlineAt, requestedDeadlineAt.longValue());
        }
        frame.put("deadlineAt", Math.max(System.currentTimeMillis() + 1, deadlineAt));
        try {
            WebSocketSession session = sessions.get(visitorId);
            if (session == null || !session.isOpen()) {
                pending.remove(id, rpc);
                return BrowserRpcResult.builder().ok(false).errorCode("browser_offline").error("浏览器未连接").build();
            }
            synchronized (session) {
                session.sendMessage(new TextMessage(frame.toJSONString()));
            }
            return future.orTimeout(timeoutMillis, TimeUnit.MILLISECONDS).join();
        } catch (Exception e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof TimeoutException) {
                // Keep the RPC pending until the late extension result arrives
                // or the grace window expires, so a late response cannot be
                // mistaken for a later request with the same id.
                CompletableFuture.delayedExecutor(LATE_RESULT_GRACE_SECONDS, TimeUnit.SECONDS).execute(() -> {
                    pending.remove(id, rpc);
                });
                return BrowserRpcResult.builder().ok(false).errorCode("command_result_unknown").error("浏览器操作结果未知").build();
            }
            pending.remove(id, rpc);
            return BrowserRpcResult.builder().ok(false).errorCode("rpc_failed").error(cause.getMessage()).build();
        }
    }

    @Override
    public void disconnect(String visitorId) {
        if (StringUtils.isBlank(visitorId)) {
            return;
        }
        WebSocketSession session = sessions.remove(visitorId);
        tabs.remove(visitorId);
        failPending(visitorId, "disconnected");
        if (session != null && session.isOpen()) {
            try {
                session.close();
            } catch (IOException ignore) {
            }
        }
    }

    @Override
    public void updateTabMeta(String visitorId, String url, String title) {
        if (StringUtils.isBlank(visitorId)) {
            return;
        }
        TabMeta previous = tabs.get(visitorId);
        tabs.put(visitorId, new TabMeta(
                StringUtils.defaultIfBlank(url, previous == null ? null : previous.url),
                StringUtils.defaultIfBlank(title, previous == null ? null : previous.title)
        ));
    }

    private void failPending(String visitorId, String message) {
        pending.entrySet().removeIf(entry -> {
            if (!visitorId.equals(entry.getValue().visitorId)) {
                return false;
            }
            entry.getValue().future.complete(BrowserRpcResult.builder()
                    .ok(false)
                    .errorCode("browser_offline")
                    .error(message)
                    .build());
            return true;
        });
    }

    private static long capMillis(String action, Map<String, Object> params) {
        if ("screenshot".equals(action)) {
            return 30_000L;
        }
        if ("wait-download".equals(action)) {
            Object timeoutMs = params == null ? null : params.get("timeoutMs");
            if (timeoutMs instanceof Number number && number.longValue() > 0) {
                return Math.min(Math.max(1L, number.longValue()), 180_000L);
            }
            return 60_000L;
        }
        return 60_000L;
    }

    private static String visitorIdOf(WebSocketSession session) {
        Object value = session.getAttributes().get("visitorId");
        return value == null ? null : String.valueOf(value);
    }

    private static Map<String, Object> asMap(Object data) {
        if (data == null) {
            return Map.of();
        }
        if (data instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return result;
        }
        if (data instanceof String text) {
            String trimmed = text.trim();
            if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
                return Map.of();
            }
            try {
                JSONObject object = JSON.parseObject(trimmed);
                return object == null ? Map.of() : object;
            } catch (RuntimeException ignored) {
                return Map.of();
            }
        }
        return Map.of();
    }

    private record TabMeta(String url, String title) {
    }

    private record PendingRpc(String visitorId, CompletableFuture<BrowserRpcResult> future) {
    }
}
