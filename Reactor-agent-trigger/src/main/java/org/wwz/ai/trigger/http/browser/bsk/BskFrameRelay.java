package org.wwz.ai.trigger.http.browser.bsk;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * bsk 连接状态机（契约 T6.2 / T6.7 的核心）。
 *
 * <p>每条扩展连接一个实例。职责：</p>
 * <ol>
 *   <li><b>首帧强制握手</b>——对齐 {@code crates/bsk-cli/src/daemon/ws.rs:228}
 *       （"The first frame MUST be {@code system.handshake} per §4.2"）：
 *       握手前收到任何其他 bsk 帧都以 {@code protocol_error} 拒绝并关闭连接。</li>
 *   <li><b>响应路由</b>——{@code routeResponse(id, payload)} 按 id 唤醒等待者。</li>
 *   <li><b>断连兜底</b>——{@code handleDisconnect} 把所有在飞请求立即补发
 *       {@code protocol_error} + {@code data.reason="extension_disconnected"}，
 *       决不让请求静默挂到超时（契约 §6.4 / T6.7）。</li>
 * </ol>
 *
 * <p><b>错误码说明</b>：bsk 协议的 13 个 {@code ErrorCode} 里
 * <b>没有</b> {@code extension_disconnected}。daemon 的做法是
 * {@code protocol_error} + {@code data={"reason":"extension_disconnected"}}
 * （{@code daemon/browsers.rs} 的 {@code extension_disconnected_data()}），
 * 本类严格照抄。</p>
 *
 * <p>本类只负责状态与回调，不做线程调度；发送由 {@link BskSink} 承担，
 * 透传由调用方（WS handler）在 {@link #isHandshakeComplete()} 之后自行处理。</p>
 */
@Slf4j
public class BskFrameRelay {

    /** 扩展断连的 data.reason 值，对齐 daemon 的 extension_disconnected_data()。 */
    public static final String REASON_EXTENSION_DISCONNECTED = "extension_disconnected";

    /** 握手前收到非握手帧时的错误信息，对齐 daemon 的 "first frame must be system.handshake"。 */
    public static final String FIRST_FRAME_MUST_BE_HANDSHAKE = "first frame must be system.handshake";

    /** {@code tool.tab_borrow}：等待用户确认，超时不能套默认值。 */
    private static final String METHOD_TOOL_TAB_BORROW = "tool.tab_borrow";

    /** {@code tool.screenshot_full_page}：未显式给 {@code timeout_ms} 时默认 120s。 */
    private static final String METHOD_TOOL_SCREENSHOT_FULL_PAGE = "tool.screenshot_full_page";

    /**
     * 这些方法自己握着操作截止时间，到点会回一个<b>有内容的</b>结果
     * （{@code reached:"timeout"}、结构化错误等），而不是挂死。
     *
     * <p>对齐 Rust {@code daemon/ipc.rs} 的 {@code EXTENSION_RESPONSE_GRACE}：
     * 传输层的截止时间必须比扩展自己的预算更长，否则传输层会用一个
     * {@code timeout} 覆盖掉扩展本来已经产出的结果。</p>
     */
    private static final Set<String> SELF_DEADLINE_METHODS = Set.of(
            "tool.upload",
            "tool.download",
            "tool.request_help",
            "tool.navigate",
            "tool.navigate_back",
            "tool.navigate_forward",
            "tool.reload",
            "tool.wait_for_navigation");

    /** 扩展侧自有截止时间之外再留的余量（对齐 Rust 的 2s grace）。 */
    private static final long EXTENSION_RESPONSE_GRACE_SECONDS = 2;

    /**
     * 当某方法的预算<b>超过</b>{@code session-timeout-seconds} 时，网关要在它之上
     * 再留的余量（秒）。
     *
     * <p>方向不能反：客户端到点会放弃并关连接，网关若更早到点，只会造出一条
     * 没人认领的响应。取 25s，因为客户端侧最大的自有余量是 borrow 的 22s
     * （移动 15s + 取消 2s + 传输 5s）。</p>
     *
     * <p>预算<b>不超过</b>配置值时不加：{@code session-timeout-seconds} 是运维
     * 侧的权威开关，凭空抬高它会让「配 60s」实际变成 85s，也会让配成 1s 的
     * 测试失去意义。</p>
     */
    private static final long OVER_CLIENT_BUDGET_SECONDS = 25;

    /** {@code tool.tab_borrow} 的默认确认等待；对齐扩展的 {@code CONFIRMATION_TIMEOUT_MS}。 */
    private static final long DEFAULT_BORROW_CONFIRMATION_MS = 60_000;

    /** borrow 确认之后还要移动标签、提交/回滚；对齐 Rust 的 15s。 */
    private static final long BORROW_MOVE_BUDGET_SECONDS = 15;

    /** 整页截图的默认预算；对齐 Rust 的 {@code ToolScreenshotFullPage} 特例。 */
    private static final long FULL_PAGE_DEFAULT_SECONDS = 120;

    /** 请求参数里的 {@code timeout_ms} 上限（u32 毫秒）。 */
    private static final long MAX_TIMEOUT_MS = 0xffff_ffffL;

    /** 每 session 一条串行队列（契约 T6.5）。 */
    private final BskSessionQueue sessionQueue;

    /** 握手响应装配器。 */
    private final BskHandshakeResponder handshakeResponder;

    /** 待补发的在飞请求。id → (connectionId, future)。 */
    private final Map<String, PendingCall> pending = new ConcurrentHashMap<>();

    /** 已注册的连接：connectionId → 连接记录。 */
    private final Map<String, RegisteredConnection> connections = new ConcurrentHashMap<>();

    /** browserInstanceId → connectionId 路由（契约 §6.1：一个用户可能有多台浏览器）。 */
    private final Map<String, String> routeByInstanceId = new ConcurrentHashMap<>();

    /**
     * userId → connectionId 路由。
     *
     * <p>CLI 侧只带 {@code user_id}（TS 端的 {@code OPENCLI_BSK_VISITOR_ID}），
     * 没有 {@code browser_id}；扩展侧握手后才知道自己的 instance_id。
     * 所以「按用户找浏览器」必须有一张表，否则 CLI 发来的 tool.* 无处可投。</p>
     */
    private final Map<String, String> routeByUserId = new ConcurrentHashMap<>();

    /**
     * 网关侧的会话表：sessionId → 会话。
     *
     * <p><b>为什么网关要自己记</b>：会话真正登记在扩展的 MV3 service worker 内存里，
     * 网关没有别的途径读到它（扩展没有实现 {@code session.list}）。Rust daemon 出于
     * 同样的理由维护了 {@code SessionRegistry}，{@code browser.list} 的
     * {@code session_count} 也来自它。</p>
     *
     * <p><b>只记扩展确认过的</b>：登记发生在 {@code tool.session_start} 的<b>成功应答</b>
     * 之后，删除发生在 {@code tool.session_stop} 成功、扩展回 {@code not_found}、
     * 或该扩展连接断开时。不按「发过请求」记账，否则失败的 start 也会留下幽灵条目。</p>
     */
    private final Map<String, TrackedSession> sessions = new ConcurrentHashMap<>();

    /** 请求超时秒数；&le;0 表示不超时（由调用方控制）。 */
    private final long sessionTimeoutSeconds;

    public BskFrameRelay(BskSessionQueue sessionQueue,
                         BskHandshakeResponder handshakeResponder,
                         long sessionTimeoutSeconds) {
        this.sessionQueue = sessionQueue;
        this.handshakeResponder = handshakeResponder;
        this.sessionTimeoutSeconds = sessionTimeoutSeconds;
    }

    // ── 连接生命周期 ─────────────────────────────────────────

    /**
     * 注册一条新连接。此时握手尚未完成，{@code instanceId} 可为 null，
     * 首帧握手后由 {@link #handleHandshake} 补齐路由。
     *
     * @param userId 配对凭证解析出的用户 id；CLI 侧按它把请求投给正确的浏览器
     */
    public void registerSession(String connectionId, String instanceId, String userId, BskSink sink) {
        if (StringUtils.isBlank(connectionId) || sink == null) {
            return;
        }
        RegisteredConnection connection = new RegisteredConnection(connectionId, instanceId, userId, sink);
        connections.put(connectionId, connection);
        if (StringUtils.isNotBlank(instanceId)) {
            routeByInstanceId.put(instanceId, connectionId);
        }
        if (StringUtils.isNotBlank(userId)) {
            routeByUserId.put(userId, connectionId);
        }
    }

    /** 扩展断开：补发在飞错误并清理全部路由。幂等。 */
    public void handleDisconnect(String connectionId) {
        RegisteredConnection connection = connections.remove(connectionId);
        if (connection == null) {
            return;
        }
        if (StringUtils.isNotBlank(connection.instanceId)
                && connectionId.equals(routeByInstanceId.get(connection.instanceId))) {
            routeByInstanceId.remove(connection.instanceId);
        }
        if (StringUtils.isNotBlank(connection.userId)
                && connectionId.equals(routeByUserId.get(connection.userId))) {
            routeByUserId.remove(connection.userId);
        }
        failAllPendingForConnection(connectionId, "protocol_error",
                "extension disconnected before response arrived");
        // 扩展断开时它内存里的会话全部消失（background.ts 的 disconnect cleanup），
        // 网关必须同步清掉，否则 session.list 会一直报已经死掉的会话。
        dropSessionsForConnection(connectionId);
        try {
            connection.sink.close();
        } catch (RuntimeException e) {
            log.debug("bsk sink close failed for connection {}: {}", connectionId, e.getMessage());
        }
    }

    // ── 入站帧处理 ───────────────────────────────────────────

    /**
     * 处理一条已分类为 {@link BskFrameKind#BSK_HANDSHAKE} 的请求帧。
     *
     * <p>非首帧的二次握手按 daemon 语义拒绝（回 {@code protocol_error} 后关闭）。</p>
     *
     * @return true 表示握手成功（连接可以开始透传）
     */
    public boolean handleHandshake(String connectionId, JSONObject handshakeFrame) {
        RegisteredConnection connection = connections.get(connectionId);
        if (connection == null) {
            return false;
        }
        if (connection.handshakeDone) {
            sendErrorAndClose(connection, handshakeFrame == null ? null : handshakeFrame.getString("id"),
                    "protocol_error", "handshake already completed");
            return false;
        }
        JSONObject response = handshakeResponder.respond(handshakeFrame);
        if (response == null) {
            // 缺 id 或缺 params：对齐 daemon 的 "invalid handshake JSON" 路径，直接踢。
            connection.sink.close();
            connections.remove(connectionId);
            return false;
        }
        try {
            connection.sink.send(response.toJSONString());
        } catch (Exception e) {
            log.warn("bsk handshake reply failed for connection {}: {}", connectionId, e.getMessage());
            connections.remove(connectionId);
            return false;
        }
        // 拒绝响应（带 error）也不注册路由：daemon 发完拒绝帧就关连接。
        if (response.containsKey("error")) {
            connections.remove(connectionId);
            try {
                connection.sink.close();
            } catch (RuntimeException ignored) {
                // 关闭失败不影响拒绝语义。
            }
            return false;
        }
        connection.handshakeDone = true;
        // 补齐 instance_id 路由（注册时可能还没有）。
        JSONObject params = handshakeFrame == null ? null : handshakeFrame.getJSONObject("params");
        connection.handshakeParams = params;
        String instanceId = params == null ? null : params.getString("instance_id");
        if (StringUtils.isNotBlank(instanceId)) {
            connection.instanceId = instanceId;
            routeByInstanceId.put(instanceId, connectionId);
        }
        if (log.isInfoEnabled()) {
            log.info("bsk handshake accepted: connection={} instance={} skew={}",
                    connectionId, instanceId,
                    handshakeResponder.isSkew(handshakeFrame));
        }
        return true;
    }

    /**
     * 路由一条扩展应答帧。命中等待者则完成其 future 并返回 true；
     * 没有等待者（迟到响应 / 未知 id）记 debug 后返回 false。
     *
     * <p><b>错误帧统一扁平化</b>：扩展回的是
     * {@code {id, error:{code,message,data?}}}，而断连/超时兜底回的是
     * {@code {id, error:{...}}}。为了让调用方只需要看一种形状，
     * 这里把扩展错误帧的 {@code error} 对象直接作为 future 的完成值
     * （即 {@code {code,message,data?}}），成功帧则完成
     * {@code {id, result}}。{@link #isErrorFrame} 用于区分两者。</p>
     */
    public boolean routeResponse(String id, JSONObject payload) {
        if (StringUtils.isBlank(id)) {
            return false;
        }
        PendingCall call = pending.remove(id);
        if (call == null) {
            log.debug("bsk response for unknown or completed id {}", id);
            return false;
        }
        call.future.complete(normalize(payload));
        return true;
    }

    /**
     * 把扩展响应帧归一成 future 完成值：
     * 成功帧保留 {@code {id, result}}；错误帧只保留 {@code {code,message,data?}}。
     * 这样 {@link #isErrorFrame} 对"扩展主动报错"和"网关兜底报错"一视同仁。
     */
    static JSONObject normalize(JSONObject payload) {
        if (payload == null) {
            return new JSONObject();
        }
        Object error = payload.get("error");
        if (error instanceof JSONObject errorObject) {
            return errorObject;
        }
        return payload;
    }

    /** future 完成值是否是错误体（{@code code} + {@code message}）。 */
    public static boolean isErrorFrame(JSONObject frame) {
        return frame != null && frame.get("code") instanceof String && frame.containsKey("message");
    }

    /** 该 id 是否有等待中的请求。 */
    public boolean isPending(String id) {
        return StringUtils.isNotBlank(id) && pending.containsKey(id);
    }

    /** 当前在飞请求数（观测用）。 */
    public int pendingCount() {
        return pending.size();
    }

    // ── 出站请求 ─────────────────────────────────────────────

    /**
     * 向指定连接发送一条 bsk 请求帧，返回在飞 future。
     *
     * <p>future 完成值有两种形状，用 {@link #isErrorFrame} 区分：成功帧是
     * {@code {id, result}}；错误帧（扩展主动报错 / 断连 / 超时 / 发送失败）
     * 是扁平的 {@code {code, message, data?}}。断连与超时都以错误帧完成，
     * 不会抛异常或挂起。</p>
     *
     * @return null 表示连接未注册、未握手或已关闭
     */
    public CompletableFuture<JSONObject> dispatch(String connectionId, String rpcId, JSONObject requestFrame) {
        return dispatch(connectionId, rpcId, requestFrame == null ? null : requestFrame.toJSONString());
    }

    /**
     * 向指定连接发送一条 bsk 请求帧（<b>原文</b>），返回在飞 future。
     *
     * <p>CLI 侧走这个重载：契约 §6.3 要求「原样转发」，不解析也不重排协议体，
     * 所以发出去的就是 CLI 发来的那串 JSON。</p>
     */
    public CompletableFuture<JSONObject> dispatch(String connectionId, String rpcId, String rawJson) {
        RegisteredConnection connection = connections.get(connectionId);
        if (connection == null || !connection.handshakeDone || StringUtils.isBlank(rpcId)) {
            return null;
        }
        if (pending.putIfAbsent(rpcId, new PendingCall(connectionId)) != null) {
            return CompletableFuture.completedFuture(
                    errorBody("protocol_error", "duplicate rpc id: " + rpcId, null));
        }
        CompletableFuture<JSONObject> future = pending.get(rpcId).future;
        try {
            connection.sink.send(rawJson);
        } catch (Exception e) {
            pending.remove(rpcId);
            return CompletableFuture.completedFuture(
                    errorBody("protocol_error", "send failed: " + e.getMessage(), null));
        }
        // 帧超时按<b>方法</b>算，不搞一刀切：borrow 要等用户点确认，整页截图
        // 要 120s，request-help 要 300s，用统一的 60s 会把它们全部提前判死。
        scheduleTimeout(rpcId, timeoutSecondsFor(rawJson));
        return future;
    }

    /**
     * 一条请求帧的传输层截止时间（秒），对齐 Rust {@code daemon/ipc.rs}
     * 的 {@code tool_dispatch_transport_timeout}。
     *
     * <p>三类方法要特殊处理，其余一律用 {@code sessionTimeoutSeconds}：</p>
     * <ol>
     *   <li>{@code tool.tab_borrow}：等待用户确认 {@code confirmation_timeout_ms}
     *       （默认 60s）<b>再加上</b>移动标签的 15s。用户确认时间不在我们的
     *       预算之内，所以不能套默认值。</li>
     *   <li>{@code tool.screenshot_full_page}：未显式给 {@code timeout_ms} 时
     *       默认 120s。</li>
     *   <li>{@link #SELF_DEADLINE_METHODS}：在自身预算上再加 2s grace，让扩展
     *       自己产出的结果（如 {@code reached:"timeout"}）先到。</li>
     * </ol>
     *
     * <p>算完之后，只有超过 {@code session-timeout-seconds} 的预算才再
     * {@code +OVER_CLIENT_BUDGET_SECONDS}：客户端的预算是「操作预算 + 取消/传输
     * 余量」，网关必须比它更耐等，否则客户端先放弃、网关后到点，请求会挂成孤儿。</p>
     *
     * <p>解析失败一律回退到默认值：网关对 bsk 帧保持透明转发，不因为看不懂
     * 参数就把请求拒掉。</p>
     */
    long timeoutSecondsFor(String rawJson) {
        if (sessionTimeoutSeconds <= 0) {
            return 0;
        }
        JSONObject frame = BskFrameRouter.parse(rawJson);
        String method = frame == null ? null : frame.getString("method");
        if (StringUtils.isBlank(method)) {
            return sessionTimeoutSeconds;
        }
        JSONObject params = frame.getJSONObject("params");

        long seconds = timeoutSecondsForMethod(method, params);
        // 配置的默认值是权威，不凭空抬高；只有超过它的预算才需要网关侧的余量。
        return seconds > sessionTimeoutSeconds ? seconds + OVER_CLIENT_BUDGET_SECONDS : seconds;
    }

    /** 单看方法本身的预算（秒）；不含网关余量。 */
    private long timeoutSecondsForMethod(String method, JSONObject params) {
        if (METHOD_TOOL_TAB_BORROW.equals(method)) {
            long confirmationMs = DEFAULT_BORROW_CONFIRMATION_MS;
            Long requested = positiveLong(params, "confirmation_timeout_ms");
            if (requested != null) {
                confirmationMs = Math.min(requested, Integer.MAX_VALUE);
            }
            return (confirmationMs + 999) / 1000 + BORROW_MOVE_BUDGET_SECONDS;
        }
        if (METHOD_TOOL_SCREENSHOT_FULL_PAGE.equals(method) && params != null
                && !params.containsKey("timeout_ms")) {
            return FULL_PAGE_DEFAULT_SECONDS + EXTENSION_RESPONSE_GRACE_SECONDS;
        }
        Long timeoutMs = positiveLong(params, "timeout_ms");
        long seconds = timeoutMs == null
                ? sessionTimeoutSeconds
                : Math.min(timeoutMs, MAX_TIMEOUT_MS) / 1000;
        if (SELF_DEADLINE_METHODS.contains(method)) {
            seconds += EXTENSION_RESPONSE_GRACE_SECONDS;
        }
        return seconds;
    }

    /** 参数里的正整数毫秒值；缺失、非数字或非正数一律返回 null。 */
    private static Long positiveLong(JSONObject params, String key) {
        if (params == null) {
            return null;
        }
        Object raw = params.get(key);
        if (!(raw instanceof Number number)) {
            return null;
        }
        long value = number.longValue();
        return value > 0 ? value : null;
    }

    /** 指定 session 的串行队列；透传路径用它保证同 session FIFO。 */
    public BskSessionQueue sessionQueue() {
        return sessionQueue;
    }

    /**
     * 直接向某条连接写一帧，<b>不注册等待者</b>。
     *
     * <p>用于 {@code cancel} 这类 fire-and-forget 帧：扩展会自己给被取消的
     * 原请求回 {@code cancelled}，这条帧本身没有对应的应答。Rust 的
     * {@code forward_cancel_to_browser} 也是直接 {@code sink.send}。</p>
     *
     * @return true 表示写成功；连接不存在、未握手或写失败返回 false
     */
    public boolean sendRaw(String connectionId, String rawJson) {
        RegisteredConnection connection = connections.get(connectionId);
        if (connection == null || !connection.handshakeDone) {
            return false;
        }
        try {
            connection.sink.send(rawJson);
            return true;
        } catch (Exception e) {
            log.debug("bsk raw send failed for connection {}: {}", connectionId, e.getMessage());
            return false;
        }
    }

    /** 按 browserInstanceId 找连接 id；未注册 / 已断开返回 null。 */
    public String connectionIdForInstance(String browserInstanceId) {
        if (StringUtils.isBlank(browserInstanceId)) {
            return null;
        }
        String connectionId = routeByInstanceId.get(browserInstanceId);
        return connectionId != null && connections.containsKey(connectionId) ? connectionId : null;
    }

    /**
     * 按 userId 找连接 id；未注册 / 已断开返回 null。
     *
     * <p>一个用户可能开了多台浏览器（每台一个扩展连接），这里取<b>最后注册</b>的那条
     * ——即用户最近打开的那台。CLI 带 {@code browser_id} 时走
     * {@link #connectionIdForInstance} 精确路由，不受这里影响。</p>
     */
    public String connectionIdForUser(String userId) {
        if (StringUtils.isBlank(userId)) {
            return null;
        }
        String connectionId = routeByUserId.get(userId);
        return connectionId != null && connections.containsKey(connectionId) ? connectionId : null;
    }

    /**
     * 该用户是否有<b>已完成握手</b>的扩展连接（= 浏览器真的可用）。
     *
     * <p>握手前不算在线：扩展刚建连但还没完成握手时，它还处理不了任何 tool.*，
     * 这时对外报「已连接」是假的。</p>
     */
    public boolean isOnlineByUser(String userId) {
        String connectionId = connectionIdForUser(userId);
        return connectionId != null && isOnline(connectionId);
    }

    /** 断开该用户的全部扩展连接（前端「断开连接」按钮走这里）。 */
    public void disconnectUser(String userId) {
        if (StringUtils.isBlank(userId)) {
            return;
        }
        for (RegisteredConnection connection : connections.values()) {
            if (userId.equals(connection.userId)) {
                handleDisconnect(connection.id);
            }
        }
    }

    /**
     * 取消一条在飞请求（CLI 侧连接断开时用）。
     *
     * <p>CLI 断开后，它的在飞请求不可能再收到应答；不主动结束的话，
     * future 会一直挂到超时，白占扩展连接的在飞额度。</p>
     *
     * @return true 表示确实取消了一条在飞请求
     */
    public boolean failPending(String rpcId, String code, String message, Map<String, Object> data) {
        if (StringUtils.isBlank(rpcId)) {
            return false;
        }
        PendingCall call = pending.remove(rpcId);
        if (call == null) {
            return false;
        }
        call.future.complete(errorBody(code, message, data));
        return true;
    }

    /** 连接是否已完成握手且仍然注册着。 */
    public boolean isOnline(String connectionId) {
        RegisteredConnection connection = connections.get(connectionId);
        return connection != null && connection.handshakeDone;
    }

    /**
     * 当前在线的浏览器清单（daemon 层 {@code browser.list} 的数据来源）。
     *
     * <p>Java 网关顶替了 Rust daemon 的位置，daemon 自己掌握「哪些浏览器连着」，
     * 扩展只知道自己那一份。所以这条清单只能由网关回答，不能转发给扩展
     * （扩展没有实现 {@code browser.list}，转过去只会得到 unknown_method）。</p>
     *
     * <p>字段对齐 Rust 的 {@code BrowserStatusEntry}
     * （{@code crates/bsk-protocol/src/system.rs}）。</p>
     */
    public List<JSONObject> browserStatusEntries() {
        List<JSONObject> entries = new ArrayList<>();
        for (RegisteredConnection connection : connections.values()) {
            if (!connection.handshakeDone) {
                continue;
            }
            JSONObject params = connection.handshakeParams;
            JSONObject browser = params == null ? null : params.getJSONObject("browser");
            JSONObject entry = new JSONObject(new LinkedHashMap<>());
            entry.put("instance_id", connection.instanceId == null ? "" : connection.instanceId);
            entry.put("browser_name", browser == null ? "" : StringUtils.defaultString(browser.getString("name")));
            entry.put("browser_version", browser == null ? "" : StringUtils.defaultString(browser.getString("version")));
            entry.put("extension_version", params == null ? "" : StringUtils.defaultString(params.getString("version")));
            entry.put("label", params == null ? "" : StringUtils.defaultString(params.getString("label")));
            // 会话数来自网关的会话表（对齐 Rust 的 count_for_browser）。
            entry.put("session_count", sessionCountForConnection(connection.id));
            entry.put("connected_at_ms", connection.connectedAtMs);
            entries.add(entry);
        }
        entries.sort(Comparator.comparingLong(entry -> entry.getLongValue("connected_at_ms")));
        return entries;
    }

    /**
     * 登记一条会话（扩展已确认创建成功）。
     *
     * @param sessionId         扩展侧会话 id（由 CLI 传入，扩展原样使用）
     * @param connectionId      承载该会话的扩展连接
     * @param agentWindowId     扩展返回的 Agent Window id；扩展没返回时为 null
     * @param interaction       交互策略；扩展没返回时为 null
     */
    public void registerSession(String sessionId, String connectionId,
                                Long agentWindowId, JSONObject interaction) {
        if (StringUtils.isBlank(sessionId) || StringUtils.isBlank(connectionId)) {
            return;
        }
        sessions.put(sessionId, new TrackedSession(
                sessionId, connectionId, agentWindowId, interaction, System.currentTimeMillis()));
    }

    /** 删除一条会话（扩展确认 stop 成功，或回 not_found）。 */
    public void dropSession(String sessionId) {
        if (StringUtils.isNotBlank(sessionId)) {
            sessions.remove(sessionId);
        }
    }

    /** 断开某条连接上的全部会话（扩展断连时它内存里的会话一并消失）。 */
    private void dropSessionsForConnection(String connectionId) {
        for (Map.Entry<String, TrackedSession> entry : sessions.entrySet()) {
            if (connectionId.equals(entry.getValue().connectionId)) {
                sessions.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    /** 某条连接上的会话数（{@code browser.list} 的 {@code session_count}）。 */
    public int sessionCountForConnection(String connectionId) {
        int count = 0;
        for (TrackedSession session : sessions.values()) {
            if (connectionId.equals(session.connectionId)) {
                count++;
            }
        }
        return count;
    }

    public List<JSONObject> sessionStatusEntries() {
        return sessionStatusEntries(null);
    }

    /**
     * 当前会话清单（daemon 层 {@code session.list} 的数据来源）。
     *
     * <p><b>必须按 userId 过滤</b>：{@code session.list} / {@code session.stop_all}
     * 是 daemon 层的全局方法，网关替 Rust daemon 回答它们。Rust daemon 是单机
     * 单用户的（一个用户 = 一台机器上的一个 daemon 进程），而我们这台网关同时
     * 服务所有用户——不过滤就等于把 A 用户的会话 id、Agent Window id、交互策略
     * 泄露给 B 用户，还能让 B 把 A 的会话停掉。</p>
     *
     * <p>{@code userId} 为 null / 空白表示<b>不过滤</b>（内部维护用，例如
     * {@code browser.list} 的计数）。</p>
     *
     * <p>形状对齐 Rust 的 {@code SessionStatusEntry}（{@code bsk-protocol/src/system.rs}）：
     * {@code session_id} / {@code browser_instance_id} / {@code agent_window_id?} /
     * {@code created_at_ms} / {@code interaction?}。</p>
     *
     * <p><b>精度边界</b>：这张表来自网关观测到的 start/stop 应答，不是扩展会话表的
     * 直读。扩展 service worker 重启但 WS 未断时，网关会短暂保留已失效的条目，
     * 直到该会话上的下一次调用返回 {@code not_found} 触发清理。Rust daemon 的
     * {@code SessionRegistry} 有同样的边界（见 {@code sessions.rs} 里
     * "leaving an orphan row visible to bsk session list" 的注释）。</p>
     */
    public List<JSONObject> sessionStatusEntries(String userId) {
        List<JSONObject> entries = new ArrayList<>();
        for (TrackedSession session : sessions.values()) {
            RegisteredConnection connection = connections.get(session.connectionId);
            if (connection != null && !ownsSession(connection, userId)) {
                continue;
            }
            JSONObject entry = new JSONObject(new LinkedHashMap<>());
            entry.put("session_id", session.sessionId);
            entry.put("browser_instance_id",
                    connection == null || connection.instanceId == null ? "" : connection.instanceId);
            if (session.agentWindowId != null) {
                entry.put("agent_window_id", session.agentWindowId);
            }
            if (session.interaction != null) {
                entry.put("interaction", session.interaction);
            }
            entry.put("created_at_ms", session.createdAtMs);
            entries.add(entry);
        }
        entries.sort(Comparator.comparingLong(entry -> entry.getLongValue("created_at_ms")));
        return entries;
    }

    /**
     * 某条连接承载的会话是否属于 {@code userId}。
     *
     * <p>会话本身不记 userId：它是扩展在<b>某条连接</b>上创建的，连接带 userId。
     * 连接已经断开（{@code connection == null}）时按可见处理——断开即不可路由，
     * 任何 CLI 都转发不过去，留着它只会让清单报出僵尸条目，不构成泄露。</p>
     */
    private static boolean ownsSession(RegisteredConnection connection, String userId) {
        return StringUtils.isBlank(userId) || userId.equals(connection.userId);
    }

    /** 参数里的正整数毫秒值；缺失、非数字或非正数一律返回 null。 */

    private void scheduleTimeout(String rpcId, long timeoutSeconds) {
        if (timeoutSeconds <= 0) {
            return;
        }
        CompletableFuture.delayedExecutor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
                .execute(() -> {
                    PendingCall call = pending.remove(rpcId);
                    if (call != null) {
                        call.future.complete(errorBody("timeout",
                                "extension did not respond within " + timeoutSeconds + "s", null));
                    }
                });
    }

    /**
     * 断开指定连接上的所有在飞请求。锁内注册的新请求会在 {@link #dispatch}
     * 的注册检查里看到已移除的连接并立即失败，不会泄漏。
     */
    private void failAllPendingForConnection(String connectionId, String code, String message) {
        for (Map.Entry<String, PendingCall> entry : pending.entrySet()) {
            if (connectionId.equals(entry.getValue().connectionId)) {
                PendingCall call = pending.remove(entry.getKey());
                if (call != null) {
                    call.future.complete(errorBody(code, message,
                            Map.of("reason", REASON_EXTENSION_DISCONNECTED)));
                }
            }
        }
    }

    /** 构造 future 完成值用的扁平错误体 {@code {code, message, data?}}。 */
    static JSONObject errorBody(String code, String message, Map<String, Object> data) {
        JSONObject error = new JSONObject(new LinkedHashMap<>());
        error.put("code", code);
        error.put("message", message);
        if (data != null && !data.isEmpty()) {
            error.put("data", new JSONObject(new LinkedHashMap<>(data)));
        }
        return error;
    }

    /** 构造一个 {@code {id, error: {code, message, data?}}} 形状的线上错误响应帧。 */
    static JSONObject completedError(String rpcId, String code, String message, Map<String, Object> data) {
        JSONObject frame = new JSONObject(new LinkedHashMap<>());
        frame.put("id", rpcId);
        frame.put("error", errorBody(code, message, data));
        return frame;
    }

    private void sendErrorAndClose(RegisteredConnection connection, String rpcId, String code, String message) {
        try {
            connection.sink.send(completedError(rpcId, code, message, null).toJSONString());
        } catch (Exception e) {
            log.debug("bsk error reply failed for connection {}: {}", connection.id, e.getMessage());
        } finally {
            try {
                connection.sink.close();
            } catch (RuntimeException ignored) {
                // 关闭失败不影响拒绝语义。
            }
            connections.remove(connection.id);
        }
    }

    /** 一条注册连接的可变状态。 */
    private static final class RegisteredConnection {
        final String id;
        final BskSink sink;
        /** 握手请求里的 instance_id，注册时可能为 null，握手后补齐。 */
        volatile String instanceId;
        /** 配对凭证解析出的用户 id；CLI 侧按它路由。 */
        final String userId;
        volatile boolean handshakeDone;
        /** 握手帧的 params：browser.list 要用的浏览器信息从这里取。 */
        volatile JSONObject handshakeParams;
        /** 握手完成时间（毫秒），browser.list 按它排序。 */
        volatile long connectedAtMs;

        RegisteredConnection(String id, String instanceId, String userId, BskSink sink) {
            this.id = id;
            this.instanceId = instanceId;
            this.userId = userId;
            this.sink = sink;
            this.connectedAtMs = System.currentTimeMillis();
        }
    }

    /** 一条在飞请求。 */
    private static final class PendingCall {
        final String connectionId;
        final CompletableFuture<JSONObject> future = new CompletableFuture<>();

        PendingCall(String connectionId) {
            this.connectionId = connectionId;
        }
    }

    /** 网关侧记的一条会话（扩展已确认创建成功）。 */
    private static final class TrackedSession {
        final String sessionId;
        /** 承载该会话的扩展连接；断连时据此整批清理。 */
        final String connectionId;
        /** 扩展返回的 Agent Window id；扩展没返回时为 null。 */
        final Long agentWindowId;
        /** 扩展返回的交互策略；没返回时为 null。 */
        final JSONObject interaction;
        final long createdAtMs;

        TrackedSession(String sessionId, String connectionId,
                       Long agentWindowId, JSONObject interaction, long createdAtMs) {
            this.sessionId = sessionId;
            this.connectionId = connectionId;
            this.agentWindowId = agentWindowId;
            this.interaction = interaction;
            this.createdAtMs = createdAtMs;
        }
    }
}
