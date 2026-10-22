package org.wwz.ai.trigger.http.browser.bsk;

import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;

/**
 * 握手响应装配器（契约 §6.2 "回应握手帧"）。
 *
 * <p>解析 {@code system.handshake} 请求帧的 {@code params}，调用
 * {@link BskHandshakeGateway#evaluate}，并产出与 Rust daemon
 * （{@code crates/bsk-cli/src/daemon/ws.rs} 的 handshake 回包）逐字段对齐的
 * 扁平响应 JSON：</p>
 *
 * <ul>
 *   <li>成功 → {@code {id, result: {server, version, protocol_version, min_compatible_protocol}}}</li>
 *   <li>拒绝 → {@code {id, error: {code: "version_too_old", message, data}}}</li>
 * </ul>
 *
 * <p>响应帧使用 bsk 的<b>扁平</b>形状（顶层 {@code result}/{@code error}），
 * 对齐 {@code frame.rs} 的 {@code ResponseFrame} 序列化。</p>
 */
public class BskHandshakeResponder {

    /** 网关应用版本号。Rust 侧取 {@code CARGO_PKG_VERSION}（0.3.2），Java 网关按自身版本走。 */
    private final String serverVersion;

    /** 本端协议版本，默认 {@link BskHandshakeGateway#PROTOCOL_VERSION}。 */
    private final String protocolVersion;

    /** 本端最低兼容协议，默认 {@link BskHandshakeGateway#MIN_COMPATIBLE_PROTOCOL}。 */
    private final String minCompatibleProtocol;

    public BskHandshakeResponder() {
        this("0.0.0", BskHandshakeGateway.PROTOCOL_VERSION, BskHandshakeGateway.MIN_COMPATIBLE_PROTOCOL);
    }

    public BskHandshakeResponder(String serverVersion, String protocolVersion, String minCompatibleProtocol) {
        this.serverVersion = StringUtils.defaultIfBlank(serverVersion, "0.0.0");
        this.protocolVersion = StringUtils.defaultIfBlank(protocolVersion, BskHandshakeGateway.PROTOCOL_VERSION);
        this.minCompatibleProtocol = StringUtils.defaultIfBlank(
                minCompatibleProtocol, BskHandshakeGateway.MIN_COMPATIBLE_PROTOCOL);
    }

    /**
     * 判定并装配握手响应。
     *
     * <p>入参 {@code handshakeFrame} 是已解析的 {@code system.handshake} 请求帧
     * （顶层含 {@code id} 与 {@code params}）。判定只看 {@code protocol_version}
     * 与 {@code min_compatible_protocol} 两个字段，其余字段（client / version /
     * instance_id / browser / label）是注册元数据，不参与兼容判定。</p>
     *
     * @param handshakeFrame 已解析的握手请求帧
     * @return 待发送的响应帧 JSON；入参缺 {@code id} 或缺 {@code params} 时返回 null
     *         （调用方应直接关闭连接，对齐 Rust 侧 "invalid handshake" 路径）
     */
    public JSONObject respond(JSONObject handshakeFrame) {
        if (handshakeFrame == null) {
            return null;
        }
        String rpcId = handshakeFrame.getString("id");
        JSONObject params = handshakeFrame.getJSONObject("params");
        if (StringUtils.isBlank(rpcId) || params == null) {
            return null;
        }
        String peerProtocol = params.getString("protocol_version");
        String peerMinCompatible = params.getString("min_compatible_protocol");

        BskHandshakeGateway.Compat compat = BskHandshakeGateway.evaluate(
                peerProtocol, peerMinCompatible, protocolVersion, minCompatibleProtocol);

        if (compat instanceof BskHandshakeGateway.Reject reject) {
            // 对齐 daemon 的拒绝回包：version_too_old + 排查所需的版本快照。
            JSONObject error = new JSONObject();
            error.put("code", "version_too_old");
            error.put("message", reject.reason());
            JSONObject data = new JSONObject();
            data.put("peer_protocol_version", peerProtocol);
            data.put("daemon_protocol_version", protocolVersion);
            data.put("daemon_version", serverVersion);
            data.put("min_compatible_protocol", minCompatibleProtocol);
            error.put("data", data);

            JSONObject response = new JSONObject();
            response.put("id", rpcId);
            response.put("error", error);
            return response;
        }

        // Ok / Skew 都放行。Skew 由调用方记告警（daemon 侧同样只 warn 不拒）。
        JSONObject result = new JSONObject();
        result.put("server", BskHandshakeGateway.SERVER_NAME);
        result.put("version", serverVersion);
        result.put("protocol_version", protocolVersion);
        result.put("min_compatible_protocol", minCompatibleProtocol);

        JSONObject response = new JSONObject();
        response.put("id", rpcId);
        response.put("result", result);
        return response;
    }

    /** 握手判定是否为 minor 漂移（允许连接但需要告警）。 */
    public boolean isSkew(JSONObject handshakeFrame) {
        if (handshakeFrame == null) {
            return false;
        }
        JSONObject params = handshakeFrame.getJSONObject("params");
        if (params == null) {
            return false;
        }
        return BskHandshakeGateway.evaluate(
                params.getString("protocol_version"),
                params.getString("min_compatible_protocol"),
                protocolVersion,
                minCompatibleProtocol) instanceof BskHandshakeGateway.Skew;
    }
}
