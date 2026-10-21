package org.wwz.ai.trigger.http.browser.bsk;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * bsk 中继的 WS 入口。
 *
 * <p>帧路由规则（契约 T6.2 "两种协议互不干扰"）：</p>
 * <ul>
 *   <li>bsk 帧（请求 / 响应 / 事件）在本类内闭环处理，
 *       绝不进入 {@code BrowserRelayFrameMapper}——那个 mapper 会把
 *       bsk 的扁平 {@code {id, result}} 帧误读成 Reactor 命令结果。</li>
 *   <li>仅当帧被分类为 {@link BskFrameKind#REACTOR_LEGACY} 时才委托给
 *       {@code BrowserRelayHub}。bsk 专用端点下这条路径正常不会命中，
 *       但保留它可以让旧扩展误连本端点时的行为与旧端点一致。</li>
 * </ul>
 *
 * <p>实现 {@link SubProtocolCapable} 以便 Spring 的握手处理器回显
 * {@code Sec-WebSocket-Protocol}（动态值来自拦截器写入的 attributes，
 * 见 {@link BskAuthSubProtocolInterceptor}）。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "reactor.browser-bsk", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BskRelayWebSocketHandler extends TextWebSocketHandler implements SubProtocolCapable {

    private final BskFrameRelay relay;
    private final LegacyDelegate legacyDelegate;

    /** 每条连接的握手前状态缓存：sessionId → attributes 快照（供 SubProtocolCapable）。 */
    private final Map<String, Map<String, Object>> sessionAttributes = new ConcurrentHashMap<>();

    /** Reactor 旧帧的委托 seam；默认走 {@code BrowserRelayHub}。 */
    public interface LegacyDelegate {
        void onText(WebSocketSession session, String payload);
    }

    /**
     * 类中有两个构造器，必须显式标注由容器使用的这一个；
     * 否则 Spring 会回落到无参构造并抛 No default constructor found
     * （与 {@code BrowserRelayHub} 的同款坑一致）。
     *
     * <p>{@code LegacyDelegate} 只在测试里注入
     * （生产没有 Reactor 旧帧会连到 bsk 专用端点的场景），不注册为 bean。
     * 生产走 {@code legacyDelegate == null} 的分支，旧帧仅记录 debug 日志。</p>
     */
    @Autowired
    public BskRelayWebSocketHandler(BskFrameRelay relay) {
        this(relay, null);
    }

    public BskRelayWebSocketHandler(BskFrameRelay relay, LegacyDelegate legacyDelegate) {
        this.relay = relay;
        this.legacyDelegate = legacyDelegate;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Map<String, Object> attributes = session.getAttributes();
        sessionAttributes.put(session.getId(), attributes == null ? Map.of() : attributes);
        BskSink sink = new BskWebSocketSink(session);
        String userId = attributeValue(attributes, BskAuthSubProtocolInterceptor.ATTR_USER_ID);
        // userId 进路由表：CLI 侧只带 user_id，靠它把 tool.* 投给这个用户的浏览器。
        relay.registerSession(session.getId(), null, userId, sink);
        log.info("bsk connection opened: session={} user={}", session.getId(), userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        BskFrameKind kind = BskFrameRouter.classify(payload);
        switch (kind) {
            case BSK_HANDSHAKE -> {
                JSONObject frame = BskFrameRouter.parse(payload);
                if (!relay.handleHandshake(session.getId(), frame)) {
                    closeQuietly(session, CloseStatus.POLICY_VIOLATION);
                    return;
                }
                // 握手完成 = 扩展真的可用，此时才对外算「浏览器已连接」
                // （在线判定在 BskFrameRelay.isOnlineByUser）。
            }
            case BSK_RESPONSE -> relay.routeResponse(
                    BskFrameRouter.idOf(payload), BskFrameRouter.parse(payload));
            case BSK_EVENT -> {
                // 事件帧只作为活性信号；透传语义由后续批次（case 层路由）承接。
                log.debug("bsk event on session {}: {}", session.getId(), payload);
            }
            case REACTOR_LEGACY -> {
                if (legacyDelegate != null) {
                    legacyDelegate.onText(session, payload);
                } else {
                    log.debug("bsk endpoint ignoring reactor-legacy frame on session {}", session.getId());
                }
            }
            case UNKNOWN -> closeQuietly(session, CloseStatus.BAD_DATA);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionAttributes.remove(session.getId());
        relay.handleDisconnect(session.getId());
        log.info("bsk connection closed: session={} status={}", session.getId(), status);
    }

    /**
     * 把客户端声明的认证子协议回显给 Spring 握手处理器。
     *
     * <p><b>注意</b>：本方法只对 Spring 的「静态支持列表」有意义，而凭证里的
     * token 每条连接都不同，静态列表无法表达「回显本次请求的那个值」。
     * 真正的回显发生在 {@link BskSubProtocolHandshakeHandler#selectProtocol}——
     * 它按请求头的实际值逐字回显，与 Rust daemon 行为一致。</p>
     *
     * <p>这里保留空列表：若返回非空但与请求值不匹配，Spring 会拒绝该协议，
     * 反而拿不到回显。</p>
     */
    @Override
    public List<String> getSubProtocols() {
        return Collections.emptyList();
    }

    private static String attributeValue(Map<String, Object> attributes, String key) {
        if (attributes == null) {
            return null;
        }
        Object value = attributes.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static void closeQuietly(WebSocketSession session, CloseStatus status) {
        BskWebSocketSink.closeQuietly(session, status);
    }
}
