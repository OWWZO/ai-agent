/** A gateway issues a pairing URL; the fragment never becomes part of the request URL. */
export interface RemoteEndpoint {
  url: string;
  token: string;
  deviceId?: string;
  expiresAt?: string;
  renewAfter?: string;
  pendingToken?: string;
  renewalAttemptAt?: string;
  renewalFailure?: "unavailable" | "rejected";
}

export const REMOTE_ENDPOINT_KEY = "bsk_remote_endpoint";
export const REMOTE_AUTH_PROTOCOL_PREFIX = "bsk-auth.";

export function parseRemoteEndpoint(input: string): RemoteEndpoint {
  const url = new URL(input.trim());
  const loopback = ["127.0.0.1", "localhost", "[::1]"].includes(url.hostname);
  if (url.protocol !== "wss:" && !(url.protocol === "ws:" && loopback)) {
    throw new Error("Remote connections require WSS (WS is allowed only on loopback)");
  }
  if (url.username || url.password || url.search) {
    throw new Error("Credentials and query parameters are not allowed in the server URL");
  }
  // 021 Cute 网关复用：允许前端 6 位数字配对码（短码）与 bsk 原生
  // 32-256 base64url 凭证并存。短码仅用于人工填入的一次性配对，配对成功
  // 后扩展立即经 /authorize 轮换为自生成的 43 位强 token，安全性不受影响。
  const token = url.hash.slice(1);
  if (!/^(\d{6}|[A-Za-z0-9_-]{32,256})$/.test(token)) {
    throw new Error("A pairing credential of 6 digits or 32–256 base64url characters is required");
  }
  url.hash = "";
  return { url: url.toString(), token };
}

export function readRemoteEndpoint(value: unknown): RemoteEndpoint | null {
  if (value === undefined || value === null) return null;
  if (typeof value !== "object") throw new Error("Invalid stored remote connection");
  const { url, token } = value as Partial<RemoteEndpoint>;
  if (typeof url !== "string" || typeof token !== "string") {
    throw new Error("Invalid stored remote connection");
  }
  const endpoint = parseRemoteEndpoint(`${url}#${token}`);
  const extra = value as Partial<RemoteEndpoint>;
  if (extra.deviceId !== undefined) {
    if (
      typeof extra.deviceId !== "string" ||
      !/^[a-f0-9]{32}$/.test(extra.deviceId) ||
      typeof extra.expiresAt !== "string" ||
      typeof extra.renewAfter !== "string" ||
      !Number.isFinite(Date.parse(extra.expiresAt)) ||
      !Number.isFinite(Date.parse(extra.renewAfter))
    )
      throw new Error("Invalid stored device authorization");
    endpoint.deviceId = extra.deviceId;
    endpoint.expiresAt = extra.expiresAt;
    endpoint.renewAfter = extra.renewAfter;
  }
  if (extra.pendingToken !== undefined) {
    // next_token 由本扩展自生成（43 位 base64url），不受短码放宽影响。
    if (typeof extra.pendingToken !== "string" || !/^[A-Za-z0-9_-]{32,256}$/.test(extra.pendingToken))
      throw new Error("Invalid pending device credential");
    endpoint.pendingToken = extra.pendingToken;
  }
  if (
    typeof extra.renewalAttemptAt === "string" &&
    Number.isFinite(Date.parse(extra.renewalAttemptAt))
  )
    endpoint.renewalAttemptAt = extra.renewalAttemptAt;
  if (extra.renewalFailure === "unavailable" || extra.renewalFailure === "rejected")
    endpoint.renewalFailure = extra.renewalFailure;
  return endpoint;
}

export function remoteAuthorizationStatus(endpoint: RemoteEndpoint, now = Date.now()) {
  if (endpoint.renewalFailure === "rejected") return "rejected";
  if (endpoint.expiresAt && Date.parse(endpoint.expiresAt) <= now)
    return endpoint.pendingToken ? "unconfirmed" : "expired";
  if (endpoint.renewalFailure) return "unavailable";
  return endpoint.pendingToken ? "renewing" : "active";
}

export function remoteSocket(url: string, endpoint: RemoteEndpoint | null): WebSocket {
  if (endpoint && endpoint.url !== url) throw new Error("Remote endpoint changed");
  if (endpoint && remoteAuthorizationStatus(endpoint) === "expired")
    throw new Error("Browser authorization has expired; pair again");
  if (!endpoint) return new WebSocket(url);
  // The credential travels in the query string, exactly like the Cute
  // browser extension does. Keeping it out of the subprotocol header means
  // no subprotocol negotiation happens, so the handshake cannot be rejected
  // by a client that requires the server to echo a selected subprotocol.
  // The header is still offered for gateways that authenticate it there.
  const target = new URL(url);
  target.searchParams.set("token", endpoint.token);
  return new WebSocket(target.toString(), [REMOTE_AUTH_PROTOCOL_PREFIX + endpoint.token]);
}
