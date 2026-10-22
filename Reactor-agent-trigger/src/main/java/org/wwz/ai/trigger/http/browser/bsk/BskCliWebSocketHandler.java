package org.wwz.ai.trigger.http.browser.bsk;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

/**
 * bsk 网关的 <b>CLI 侧</b> WS 入口。
 *
 * <p>与扩展侧入口（{@link BskRelayWebSocketHandler}）是<b>两种角色、两条路径</b>：</p>
 * <ul>
 *   <li>扩展侧：首帧必须是 {@code system.handshake}，之后由网关向它发请求。</li>
 *   <li>CLI 侧：<b>不发握手</b>（对应 Rust CLI 的 IPC 客户端角色），
 *       第一帧就是 {@code tool.*} 请求。</li>
 * </ul>
 *
 * <p>CLI 侧的处理链路：收到请求帧 → 按 session 排进串行车道 → 投给目标扩展
 * → 等扩展的应答 → 把应答帧原样写回 CLI。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "cute.browser-bsk", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BskCliWebSocketHandler extends TextWebSocketHandler {

    private final BskCliRelay cliRelay;
    private final BskSessionQueue sessionQueue;

    /**
     * 类中有两个构造器，必须显式标注由容器使用的这一个；
     * 否则 Spring 会回落到无参构造并抛 No default constructor found。
     */
    @Autowired
    public BskCliWebSocketHandler(BskCliRelay cliRelay,
                                   @Qualifier("bskCliSessionQueue") BskSessionQueue sessionQueue) {
        this.cliRelay = cliRelay;
        this.sessionQueue = sessionQueue;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Map<String, Object> attributes = session.getAttributes();
        String userId = attributeValue(attributes, BskCliAuthInterceptor.ATTR_USER_ID);
        String browserId = attributeValue(attributes, BskCliAuthInterceptor.ATTR_BROWSER_ID);
        BskCliConnection cli = new BskCliConnection(
                session.getId(), userId, browserId, new BskWebSocketSink(session));
        cliRelay.register(cli);
        log.info("bsk cli connection opened: session={} user={} browser={}",
                session.getId(), userId, browserId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        BskFrameKind kind = BskFrameRouter.classify(payload);
        switch (kind) {
            case BSK_HANDSHAKE -> submit(session, payload);
            case BSK_EVENT -> log.debug("bsk cli endpoint ignoring event frame on session {}", session.getId());
            case BSK_RESPONSE -> log.debug("bsk cli endpoint ignoring response frame on session {}", session.getId());
            case REACTOR_LEGACY -> log.debug("bsk cli endpoint ignoring reactor-legacy frame on session {}", session.getId());
            case UNKNOWN -> BskWebSocketSink.closeQuietly(session, CloseStatus.BAD_DATA);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        cliRelay.unregister(session.getId());
        log.info("bsk cli connection closed: session={} status={}", session.getId(), status);
    }

    /**
     * 把请求帧排进该 session 的串行车道。
     *
     * <p>串行是 bsk daemon 的既有语义：同一 session 的并发提交要么排队、
     * 要么直接拒绝（{@code session_busy}），不允许两条命令同时压在一个浏览器会话上。</p>
     */
    private void submit(WebSocketSession session, String payload) {
        BskCliConnection cli = cliRelay.connectionOf(session.getId());
        if (cli == null) {
            return;
        }
        String sessionKey = cliRelay.sessionKeyOf(cli, payload);
        try {
            sessionQueue.submit(sessionKey, () -> {
                JSONObject response = cliRelay.forward(cli, payload);
                try {
                    cli.sink().send(response.toJSONString());
                } catch (Exception e) {
                    log.warn("bsk cli response write failed: session={} {}", cli.connectionId(), e.getMessage());
                }
            });
        } catch (RejectedExecutionException e) {
            // 只在「忙即拒绝」模式下出现（对齐 bsk daemon 的 session_busy）。
            String rpcId = BskFrameRouter.idOf(payload);
            JSONObject error = BskCliRelay.errorFrame(rpcId, "timeout",
                    "session is busy", java.util.Map.of("reason", "session_busy"));
            sendQuietly(session, error.toJSONString());
        }
    }

    private static void sendQuietly(WebSocketSession session, String payload) {
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(payload));
            }
        } catch (Exception e) {
            log.debug("bsk cli error write failed: {}", e.getMessage());
        }
    }

    private static String attributeValue(Map<String, Object> attributes, String key) {
        if (attributes == null) {
            return null;
        }
        Object value = attributes.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
