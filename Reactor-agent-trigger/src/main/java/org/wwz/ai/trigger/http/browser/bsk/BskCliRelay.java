package org.wwz.ai.trigger.http.browser.bsk;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * CLI 侧请求的路由与转发（021）。
 *
 * <p><b>为什么需要这一层</b>：bsk 协议里发请求的一端（CLI）和执行的一端（扩展）
 * 之间原本隔着 Rust daemon。021 把 daemon 换成 Java 网关，Java 就必须补上
 * daemon 的两项职责：<b>把 CLI 的请求帧投给正确的浏览器</b>，
 * 以及<b>把扩展的应答帧送回 CLI</b>。</p>
 *
 * <p><b>透明性</b>：本类不解析协议语义，只取两个<b>路由键</b>——
 * 帧的 {@code id}（应答关联）和 {@code params.session_id}（串行车道）——
 * 其余内容原样转发（契约 §6.3「原样转发，不得解析协议内容」）。</p>
 *
 * <p><b>目标解析顺序</b>：CLI 带 {@code browser_id} 时按实例精确路由；
 * 否则按 {@code user_id} 路由到该用户最近注册的扩展连接。
 * 两条路都落空 = 该用户的浏览器没连上网关。</p>
 */
@Slf4j
public class BskCliRelay {

    /** 扩展不在线时使用的 data.reason（对齐 Rust daemon 的 extension_disconnected_data）。 */
    public static final String REASON_EXTENSION_DISCONNECTED = "extension_disconnected";

    /** daemon 层的浏览器清单方法（扩展不实现，由网关自己答）。 */
    private static final String METHOD_BROWSER_LIST = "browser.list";

    /** daemon 层的会话清单方法（扩展不实现，由网关自己答）。 */
    private static final String METHOD_SESSION_LIST = "session.list";

    /** daemon 层的「停掉全部会话」方法（扩展只有单个 stop，由网关展开）。 */
    private static final String METHOD_SESSION_STOP_ALL = "session.stop_all";

    /** daemon 层的睡眠方法（Rust daemon 自己 sleep，不经扩展）。 */
    private static final String METHOD_TOOL_WAIT_MS = "tool.wait_ms";

    /** 会话生命周期方法：网关据它们的成功应答维护自己的会话表。 */
    private static final String METHOD_TOOL_SESSION_START = "tool.session_start";
    private static final String METHOD_TOOL_SESSION_STOP = "tool.session_stop";

    /** 取消方法：网关先查本地令牌，再转发给扩展（对齐 Rust {@code handle_cancel}）。 */
    private static final String METHOD_CANCEL = "cancel";

    /**
     * {@code tool.wait_ms} 的上限，对齐 Rust daemon 的 {@code MAX_WAIT_MS}
     * （{@code daemon/ipc.rs}：5 分钟）。
     */
    private static final long MAX_WAIT_MS = 5 * 60 * 1_000L;

    /**
     * 网关侧等待扩展应答的预算（秒）。
     *
     * <p>必须<b>大于</b> CLI 侧任何一条命令自己的超时：CLI 到点会放弃并关连接，
     * 网关如果更早到点则只会产生一个无人认领的错误响应；如果更晚（旧行为），
     * CLI 已经走掉、请求会在网关里挂成孤儿。</p>
     *
     * <p>取 240s：CLI 侧最长的预算是 {@code screenshot --full-page} 的
     * 120s + 5s IPC slack，留出余量覆盖扩展侧的真实耗时。</p>
     */
    private static final long AWAIT_TIMEOUT_SECONDS = 240;

    /** 生产侧使用的等待预算（秒）；见 {@link #AWAIT_TIMEOUT_SECONDS} 的说明。 */
    public static long defaultAwaitTimeoutSeconds() {
        return AWAIT_TIMEOUT_SECONDS;
    }
    /** CLI 自己断开时使用的 data.reason。 */
    public static final String REASON_CLI_DISCONNECTED = "cli_disconnected";

    private final BskFrameRelay frameRelay;

    /** 网关侧兜底超时（秒）：帧自己在飞时的超时由 BskFrameRelay 负责。 */
    private final long awaitTimeoutSeconds;

    /** CLI 连接：connectionId → 连接。 */
    private final Map<String, BskCliConnection> cliConnections = new ConcurrentHashMap<>();

    /**
     * 在飞请求：rpcId → (CLI 连接 id, 目标扩展连接 id)。
     *
     * <p>CLI 断开时据此结束在飞请求；{@code cancel} 时据此把取消帧投给
     * <b>承载该请求的扩展连接</b>（不是当前 CLI 连接的默认目标——取消必须
     * 打到原来那条线上）。</p>
     */
    private final Map<String, InFlightCall> inFlight = new ConcurrentHashMap<>();

    public BskCliRelay(BskFrameRelay frameRelay, long awaitTimeoutSeconds) {
        this.frameRelay = frameRelay;
        // 构造参数是权威值：生产侧由 BskRelayBeansConfig 传 AWAIT_TIMEOUT_SECONDS，
        // 测试可以传更短的值。&le;0 表示不设网关侧兜底（由帧自己的超时负责）。
        this.awaitTimeoutSeconds = awaitTimeoutSeconds;
    }

    public void register(BskCliConnection connection) {
        if (connection != null && StringUtils.isNotBlank(connection.connectionId())) {
            cliConnections.put(connection.connectionId(), connection);
        }
    }

    /** 按连接 id 取 CLI 连接；未注册返回 null。 */
    public BskCliConnection connectionOf(String connectionId) {
        return connectionId == null ? null : cliConnections.get(connectionId);
    }

    /**
     * CLI 连接断开：注销并结束它的在飞请求。
     *
     * <p>不结束的话，这些 future 会一直挂到超时，白占扩展连接的在飞额度。</p>
     */
    public void unregister(String connectionId) {
        if (StringUtils.isBlank(connectionId)) {
            return;
        }
        cliConnections.remove(connectionId);
        for (Map.Entry<String, InFlightCall> entry : inFlight.entrySet()) {
            if (connectionId.equals(entry.getValue().cliConnectionId)
                    && inFlight.remove(entry.getKey(), entry.getValue())) {
                frameRelay.failPending(entry.getKey(), "protocol_error",
                        "cli disconnected before response arrived",
                        Map.of("reason", REASON_CLI_DISCONNECTED));
            }
        }
    }

    /**
     * 把一条 CLI 请求帧转发给目标扩展，并等待扩展的应答。
     *
     * @return 应答帧：成功是 {@code {id, result}}，失败是 {@code {id, error:{...}}}
     */
    public JSONObject forward(BskCliConnection cli, String rawFrame) {
        JSONObject frame = BskFrameRouter.parse(rawFrame);
        if (frame == null) {
            return errorFrame(null, "protocol_error", "malformed bsk frame", null);
        }
        String rpcId = frame.getString("id");
        String method = frame.getString("method");
        if (METHOD_BROWSER_LIST.equals(method)) {
            // browser.list 是 daemon 层的方法：Java 网关顶替了 Rust daemon 的位置，
            // 「哪些浏览器连着」只有网关知道；扩展没有实现它，转过去只会得到 unknown_method。
            return browserListFrame(rpcId);
        }
        if (METHOD_SESSION_LIST.equals(method)) {
            // session.list 同样是 daemon 层的：Rust 由 daemon 的 SessionRegistry 回答
            // （daemon/ipc.rs handle_session_list），扩展 dispatcher 里没有这个 case。
            return sessionListFrame(rpcId, cli);
        }
        if (METHOD_SESSION_STOP_ALL.equals(method)) {
            // session.stop_all 也是 daemon 层的：扩展的 tool.session_stop 只接受单个
            // session_id（schema 里 session_id 是 required），所以 Rust 的
            // handle_session_stop_all 也是自己遍历逐个调。这里照做。
            return sessionStopAllFrame(rpcId, cli);
        }
        if (METHOD_TOOL_WAIT_MS.equals(method)) {
            // tool.wait_ms 是 daemon 自己 sleep 的（daemon/ipc.rs handle_wait_ms），
            // 不经扩展，也不需要 session。转给扩展只会得到 unknown_method。
            return waitMsFrame(rpcId, frame.getJSONObject("params"));
        }
        if (METHOD_CANCEL.equals(method)) {
            return cancelFrame(rpcId, frame.getJSONObject("params"));
        }
        String targetConnectionId = resolveTarget(cli);
        if (targetConnectionId == null) {
            return errorFrame(rpcId, "protocol_error", "extension is not connected",
                    Map.of("reason", REASON_EXTENSION_DISCONNECTED));
        }
        inFlight.put(rpcId, new InFlightCall(cli.connectionId(), targetConnectionId));
        CompletableFuture<JSONObject> future = frameRelay.dispatch(targetConnectionId, rpcId, rawFrame);
        if (future == null) {
            inFlight.remove(rpcId);
            return errorFrame(rpcId, "protocol_error", "extension is not connected",
                    Map.of("reason", REASON_EXTENSION_DISCONNECTED));
        }
        JSONObject value;
        try {
            value = future.get(awaitTimeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            inFlight.remove(rpcId);
            return errorFrame(rpcId, "timeout", "extension did not respond in time", null);
        } catch (ExecutionException e) {
            inFlight.remove(rpcId);
            return errorFrame(rpcId, "protocol_error", String.valueOf(e.getCause()), null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            inFlight.remove(rpcId);
            return errorFrame(rpcId, "protocol_error", "interrupted while waiting for extension", null);
        } finally {
            inFlight.remove(rpcId);
        }
        if (value == null) {
            return errorFrame(rpcId, "protocol_error", "empty response from extension", null);
        }
        if (BskFrameRelay.isErrorFrame(value)) {
            // 扩展回 not_found：说明它那边的会话已经没了（service worker 重启等），
            // 网关的会话表要同步删掉，否则 session.list 会一直报幽灵会话。
            if ("not_found".equals(value.getString("code"))) {
                String sessionId = sessionIdOf(rawFrame);
                if (sessionId != null) {
                    frameRelay.dropSession(sessionId);
                }
            }
            // BskFrameRelay.normalize 把错误帧归一成 {code,message,data?}，
            // 这里再包回线上形状 {id, error:{...}}。
            JSONObject data = value.getJSONObject("data");
            return errorFrame(rpcId, value.getString("code"), value.getString("message"),
                    data == null ? null : new LinkedHashMap<>(data));
        }
        trackSessionOutcome(method, rawFrame, targetConnectionId, value);
        return value;
    }

    /** 从请求帧里取 {@code params.session_id}；缺失返回 null。 */
    private static String sessionIdOf(String rawFrame) {
        JSONObject frame = BskFrameRouter.parse(rawFrame);
        JSONObject params = frame == null ? null : frame.getJSONObject("params");
        String sessionId = params == null ? null : params.getString("session_id");
        return StringUtils.isBlank(sessionId) ? null : sessionId;
    }

    /**
     * 按扩展的<b>成功应答</b>维护网关侧会话表。
     *
     * <p>只在 start 成功后登记、stop 成功后删除——按「发过请求」记账会让失败的
     * start 留下幽灵条目。{@code agent_window_id} 与 {@code interaction} 取自扩展
     * 的应答（扩展没返回就留空），这样 {@code session.list} 报的是真实值。</p>
     */
    private void trackSessionOutcome(String method, String rawFrame,
                                     String connectionId, JSONObject result) {
        if (!METHOD_TOOL_SESSION_START.equals(method) && !METHOD_TOOL_SESSION_STOP.equals(method)) {
            return;
        }
        String sessionId = sessionIdOf(rawFrame);
        if (sessionId == null) {
            return;
        }
        if (METHOD_TOOL_SESSION_STOP.equals(method)) {
            frameRelay.dropSession(sessionId);
            return;
        }
        JSONObject body = result.getJSONObject("result");
        Long agentWindowId = null;
        JSONObject interaction = null;
        if (body != null) {
            if (body.get("agent_window_id") instanceof Number number) {
                agentWindowId = number.longValue();
            }
            interaction = body.getJSONObject("interaction");
        }
        frameRelay.registerSession(sessionId, connectionId, agentWindowId, interaction);
    }

    /**
     * 构造 {@code cancel} 的应答帧。     *
     * <p>语义对齐 Rust 的 {@code handle_cancel}（{@code daemon/ipc.rs:753}）：</p>
     * <ol>
     *   <li>先查本地在飞表。命中说明这条请求正等着扩展应答，把取消帧投给
     *       <b>承载它的那条扩展连接</b>（不是当前 CLI 的默认目标——取消必须打到
     *       原来那条线上）。</li>
     *   <li>扩展侧 {@code dispatcher.ts:276} 收到 {@code cancel} 会 abort 对应的
     *       AbortController，原请求随即以 {@code cancelled} 结束，取消帧本身回
     *       {@code {cancelled:true|false}}。</li>
     *   <li>没命中就回 {@code {cancelled:false}}——请求可能已经完成或根本没发过。</li>
     * </ol>
     *
     * <p><b>不等扩展的应答</b>：Rust 的 {@code forward_cancel_to_browser} 也是发完
     * 就返回（fire-and-forget），取消的确认由被取消的那条请求自己给出。这里同理，
     * 只把 {@code cancelled} 报成"本地找到了这条在飞请求"。</p>
     */
    private JSONObject cancelFrame(String rpcId, JSONObject params) {
        String targetRpcId = params == null ? null : params.getString("rpc_id");
        if (StringUtils.isBlank(targetRpcId)) {
            return errorFrame(rpcId, "invalid_params", "cancel requires rpc_id", null);
        }
        InFlightCall call = inFlight.get(targetRpcId);
        boolean cancelled = false;
        if (call != null && StringUtils.isNotBlank(call.extensionConnectionId)) {
            JSONObject cancelRequest = new JSONObject(new LinkedHashMap<>());
            cancelRequest.put("id", "cancel-" + targetRpcId);
            cancelRequest.put("method", METHOD_CANCEL);
            cancelRequest.put("params", new JSONObject(Map.of("rpc_id", targetRpcId)));
            // 直接写进扩展连接，不注册等待者：扩展会自己给原请求回 cancelled。
            cancelled = frameRelay.sendRaw(call.extensionConnectionId, cancelRequest.toJSONString());
        }
        JSONObject result = new JSONObject(new LinkedHashMap<>());
        result.put("cancelled", cancelled);
        JSONObject frame = new JSONObject(new LinkedHashMap<>());
        frame.put("id", rpcId);
        frame.put("result", result);
        return frame;
    }

    /**
     * 构造 {@code session.list} 的应答帧 {@code {id, result:{sessions:[...]}}}。
     *
     * <p>形状对齐 Rust daemon 的 {@code SessionListResult}（{@code daemon/ipc.rs}）：
     * 结果对象里是 {@code sessions} 数组，每项是 {@code SessionStatusEntry}。</p>
     *
     * <p>数据来自网关观测到的 {@code tool.session_start/stop} <b>成功应答</b>
     * （见 {@link #trackSessionOutcome}）。扩展没有实现 {@code session.list}，
     * 网关是唯一能回答它的位置——与 Rust daemon 的 {@code SessionRegistry} 同构。</p>
     */
    private JSONObject sessionListFrame(String rpcId, BskCliConnection cli) {
        JSONObject result = new JSONObject(new LinkedHashMap<>());
        // 只报这个 CLI 用户自己的会话：网关服务所有用户，这里等价于 Rust daemon
        // 的单用户视角。不过滤就是跨用户泄露（session id / Agent Window id /
        // 交互策略），而且 stop_all 还会顺势把别人的会话停掉。
        result.put("sessions", frameRelay.sessionStatusEntries(cli == null ? null : cli.userId()));
        JSONObject frame = new JSONObject(new LinkedHashMap<>());
        frame.put("id", rpcId);
        frame.put("result", result);
        return frame;
    }

    /**
     * 构造 {@code session.stop_all} 的应答帧。
     *
     * <p>扩展的 {@code tool.session_stop} 只接受<b>单个</b> {@code session_id}
     * （schema 里该字段是 required），没有批量语义。Rust daemon 的
     * {@code handle_session_stop_all} 也是自己遍历注册表逐个调，这里照做。</p>
     *
     * <p>停的范围是网关会话表里<b>该 CLI 用户所能触达</b>的会话：网关只知道
     * 自己见过的 sessionId，所以"全部"指的是网关视角的全部，而不是扩展那边的全部。</p>
     *
     * <p>应答形状对齐 Rust 的 {@code CliSessionStopResult}：
     * {@code {stopped:[...], failed:[...]}}。</p>
     */
    private JSONObject sessionStopAllFrame(String rpcId, BskCliConnection cli) {
        List<JSONObject> stopped = new ArrayList<>();
        List<JSONObject> failed = new ArrayList<>();
        // 「全部」= 这个用户自己的全部，不是网关上所有用户的全部。
        for (JSONObject session : frameRelay.sessionStatusEntries(cli == null ? null : cli.userId())) {
            String sessionId = session.getString("session_id");
            if (StringUtils.isBlank(sessionId)) {
                continue;
            }
            String rawFrame = sessionStopRequest(sessionId);
            JSONObject answer = forward(cli, rawFrame);
            if (answer.get("error") == null) {
                stopped.add(session);
            } else {
                JSONObject failure = new JSONObject(new LinkedHashMap<>());
                failure.put("session_id", sessionId);
                failure.put("code", answer.getJSONObject("error").getString("code"));
                failure.put("message", answer.getJSONObject("error").getString("message"));
                failed.add(failure);
            }
        }
        JSONObject result = new JSONObject(new LinkedHashMap<>());
        result.put("stopped", stopped);
        result.put("failed", failed);
        JSONObject frame = new JSONObject(new LinkedHashMap<>());
        frame.put("id", rpcId);
        frame.put("result", result);
        return frame;
    }

    /** 构造一条 {@code tool.session_stop} 请求帧（网关内部用，id 带前缀避免撞 CLI 的 id）。 */
    private static String sessionStopRequest(String sessionId) {
        JSONObject params = new JSONObject(new LinkedHashMap<>());
        params.put("session_id", sessionId);
        JSONObject frame = new JSONObject(new LinkedHashMap<>());
        frame.put("id", "stop-all-" + sessionId);
        frame.put("method", METHOD_TOOL_SESSION_STOP);
        frame.put("params", params);
        return frame.toJSONString();
    }

    /**
     * 构造 {@code tool.wait_ms} 的应答帧。
     *
     * <p>语义照抄 Rust daemon 的 {@code handle_wait_ms}（{@code daemon/ipc.rs}）：
     * 在本端 sleep，不经过扩展、不需要 session；{@code duration_ms} 缺失或不是
     * 非负整数回 {@code invalid_params}，超过 5 分钟上限同样拒绝。</p>
     */
    private JSONObject waitMsFrame(String rpcId, JSONObject params) {
        Object raw = params == null ? null : params.get("duration_ms");
        if (!(raw instanceof Number number) || number.longValue() < 0
                || number.doubleValue() != number.longValue()) {
            return errorFrame(rpcId, "invalid_params",
                    "wait_ms requires a non-negative integer duration_ms", null);
        }
        long durationMs = number.longValue();
        if (durationMs > MAX_WAIT_MS) {
            return errorFrame(rpcId, "invalid_params",
                    "wait_ms duration " + durationMs + " exceeds limit " + MAX_WAIT_MS + "ms", null);
        }
        if (durationMs > 0) {
            try {
                Thread.sleep(durationMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return errorFrame(rpcId, "cancelled", "wait_ms cancelled", null);
            }
        }
        JSONObject result = new JSONObject(new LinkedHashMap<>());
        result.put("waited_ms", durationMs);
        JSONObject frame = new JSONObject(new LinkedHashMap<>());
        frame.put("id", rpcId);
        frame.put("result", result);
        return frame;
    }

    /**
     * 串行车道键：同 session 的请求必须串行转发（契约 T6.5）。
     *
     * <p>没有 session_id 的帧退化成按 CLI 连接串行——同一 CLI 的请求
     * 本来就该有先后。</p>
     */
    public String sessionKeyOf(BskCliConnection cli, String rawFrame) {
        JSONObject frame = BskFrameRouter.parse(rawFrame);
        JSONObject params = frame == null ? null : frame.getJSONObject("params");
        String sessionId = params == null ? null : params.getString("session_id");
        if (StringUtils.isNotBlank(sessionId)) {
            return sessionId;
        }
        return "cli:" + cli.connectionId();
    }

    /** 目标扩展连接 id；解析不到返回 null（= 该用户的浏览器没连上网关）。 */
    private String resolveTarget(BskCliConnection cli) {
        if (cli == null) {
            return null;
        }
        if (StringUtils.isNotBlank(cli.browserId())) {
            String byInstance = frameRelay.connectionIdForInstance(cli.browserId());
            if (byInstance != null) {
                return byInstance;
            }
        }
        return frameRelay.connectionIdForUser(cli.userId());
    }

    /**
     * 构造 {@code browser.list} 的应答帧 {@code {id, result:{browsers:[...]}}}。
     *
     * <p>形状对齐 Rust daemon 的 {@code BrowserListResult}：结果对象里是
     * {@code browsers} 数组，每项是 {@code BrowserStatusEntry}。</p>
     */
    private JSONObject browserListFrame(String rpcId) {
        JSONObject result = new JSONObject(new LinkedHashMap<>());
        result.put("browsers", frameRelay.browserStatusEntries());
        JSONObject frame = new JSONObject(new LinkedHashMap<>());
        frame.put("id", rpcId);
        frame.put("result", result);
        return frame;
    }

    /** 构造线上形状的错误帧 {@code {id, error:{code,message,data?}}}。 */
    static JSONObject errorFrame(String rpcId, String code, String message, Map<String, Object> data) {
        JSONObject error = new JSONObject(new LinkedHashMap<>());
        error.put("code", code);
        error.put("message", message);
        if (data != null && !data.isEmpty()) {
            error.put("data", new JSONObject(new LinkedHashMap<>(data)));
        }
        JSONObject frame = new JSONObject(new LinkedHashMap<>());
        frame.put("id", rpcId);
        frame.put("error", error);
        return frame;
    }

    /**
     * 一条在飞请求。
     *
     * <p>记两个连接：CLI 断开时要按 {@code cliConnectionId} 清；{@code cancel}
     * 时要按 {@code extensionConnectionId} 把取消帧投回原来那条扩展连接。</p>
     */
    private static final class InFlightCall {
        final String cliConnectionId;
        final String extensionConnectionId;

        InFlightCall(String cliConnectionId, String extensionConnectionId) {
            this.cliConnectionId = cliConnectionId;
            this.extensionConnectionId = extensionConnectionId;
        }
    }
}
