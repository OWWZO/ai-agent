/**
 * `system.handshake` helper for peers that require it as the mandatory first
 * frame on a fresh WebSocket.
 *
 * Boundary note (important): `BskClient` is the **CLI-side** client. The Rust
 * CLI's `ipc_client` does not handshake; the browser **extension** is the peer
 * that sends `system.handshake` first (`crates/bsk-cli/src/daemon/ws.rs:228`
 * kicks any connection whose first frame is not a handshake). This module is
 * therefore an explicit, opt-in helper — it is NOT invoked by
 * `BskClient.connect()`. Use it when talking to a peer that enforces the
 * extension-side handshake (today: the in-process mock server in tests).
 *
 * Because the peer only accepts the handshake as frame #1, call
 * {@link performHandshake} immediately after the socket opens, before any
 * `tool.*` / `session.*` call.
 */

import { BskRpcError } from './errors.js';
import { METHODS } from './methods.js';
import type { BrowserPeerInfo, HandshakeParams, HandshakeResult } from './protocol.js';

/** Logical protocol revision advertised by this adapter. */
export const PROTOCOL_VERSION = '1.3';

/** Lowest peer protocol version this adapter accepts. */
export const MIN_COMPATIBLE_PROTOCOL = '1.0';

/** Deprecated legacy app-semver floor; new peers send `"0.0.0"`. */
export const MIN_COMPATIBLE_PEER = '0.0.0';

/** Matches `apps/extension/src/transport/handshake.ts`. */
export const HANDSHAKE_TIMEOUT_MS = 10_000;

/** Client id used when the bridge impersonates a browser peer. */
export const BRIDGE_CLIENT_ID = 'reactor-opencli-bridge';

/** Minimal caller surface — structurally satisfied by `BskClient`. */
export interface HandshakeCaller {
  call<T = unknown>(
    method: typeof METHODS.systemHandshake,
    params?: unknown,
    options?: { timeoutMs?: number; signal?: AbortSignal },
  ): Promise<T>;
}

export interface HandshakeInput {
  client?: string;
  /** App semver of the caller (e.g. `"1.0.0"`). */
  version: string;
  protocolVersion?: string;
  instanceId: string;
  browser: BrowserPeerInfo;
  label: string;
  minCompatiblePeer?: string;
  minCompatibleProtocol?: string;
}

export interface HandshakeOptions {
  timeoutMs?: number;
  signal?: AbortSignal;
}

/** Build the wire params, applying the same defaults as the extension. */
export function buildHandshakeParams(input: HandshakeInput): HandshakeParams {
  return {
    client: input.client ?? BRIDGE_CLIENT_ID,
    version: input.version,
    protocol_version: input.protocolVersion ?? PROTOCOL_VERSION,
    instance_id: input.instanceId,
    browser: input.browser,
    label: input.label,
    min_compatible_peer: input.minCompatiblePeer ?? MIN_COMPATIBLE_PEER,
    min_compatible_protocol: input.minCompatibleProtocol ?? MIN_COMPATIBLE_PROTOCOL,
  };
}

/**
 * Send `system.handshake` as the first frame and await the peer's result.
 *
 * Rejections from the peer are wrapped with a clear "peer rejected" message
 * while preserving `code` / `data` (so callers can still branch on
 * `version_too_old`).
 */
export async function performHandshake(
  caller: HandshakeCaller,
  params: HandshakeParams,
  options: HandshakeOptions = {},
): Promise<HandshakeResult> {
  try {
    return await caller.call<HandshakeResult>(METHODS.systemHandshake, params, {
      timeoutMs: options.timeoutMs ?? HANDSHAKE_TIMEOUT_MS,
      ...(options.signal === undefined ? {} : { signal: options.signal }),
    });
  } catch (error) {
    if (error instanceof BskRpcError) {
      throw new BskRpcError(
        {
          code: error.code,
          message: `bsk peer rejected system.handshake: ${error.code} — ${error.message}`,
          ...(error.data === undefined ? {} : { data: error.data }),
        },
        error.rpcId,
      );
    }
    throw error;
  }
}

/**
 * Convenience wrapper for peers that announce themselves as a browser
 * extension (used by the mock server tests to exercise the first-frame gate).
 */
export async function performExtensionHandshake(
  caller: HandshakeCaller,
  input: HandshakeInput,
  options: HandshakeOptions = {},
): Promise<HandshakeResult> {
  return performHandshake(caller, buildHandshakeParams(input), options);
}

// ── Compatibility verdict (mirrors `evaluate_handshake_compat`) ──────────────

export type HandshakeCompat = 'ok' | 'skew' | 'reject';

export interface HandshakeVerdict {
  compat: HandshakeCompat;
  /** Only present for `compat === 'reject'`. */
  reason?: string;
}

export interface ProtocolParts {
  major: number;
  minor: number;
}

/**
 * Upper bound accepted for each protocol component. Mirrors the daemon's
 * `MAX_PROTOCOL_MAJOR` so both peers reject the *same* inputs: the Rust side
 * parses `u64` but caps at `2^53 - 1`, and the TS side would lose precision
 * past that anyway.
 */
export const MAX_PROTOCOL_COMPONENT = Number.MAX_SAFE_INTEGER;

/**
 * Leading numeric component of a `MAJOR.MINOR` protocol string.
 * A non-empty ASCII-digit run only — signs, whitespace, `1e3`, `1x` and an
 * empty head all return `null` (mirrors `system.rs::parse_major`).
 */
export function parseProtocolMajor(value: string): number | null {
  const head = value.split('.')[0] ?? '';
  if (!/^[0-9]+$/.test(head)) return null;
  const n = Number(head);
  if (!Number.isSafeInteger(n) || n < 0) return null;
  return n;
}

/** `(major, minor)` pair; a missing or trailing-dot minor parses as `0`. */
export function parseProtocolParts(value: string): ProtocolParts | null {
  const major = parseProtocolMajor(value);
  if (major === null) return null;
  const rest = value.split('.')[1];
  if (rest === undefined || rest === '') return { major, minor: 0 };
  if (!/^[0-9]+$/.test(rest)) return null;
  const minor = Number(rest);
  if (!Number.isSafeInteger(minor) || minor < 0) return null;
  return { major, minor };
}

/** Lexicographic `(major, minor)` compare; `null` when either side is junk. */
export function compareProtocol(a: string, b: string): number | null {
  const pa = parseProtocolParts(a);
  const pb = parseProtocolParts(b);
  if (!pa || !pb) return null;
  if (pa.major !== pb.major) return pa.major - pb.major;
  return pa.minor - pb.minor;
}

/**
 * Pure protocol comparison shared by both peers.
 *
 * Direct port of `bsk_protocol::system::evaluate_handshake_compat`:
 *
 *   protocol unparseable or major differs            → reject
 *   peer_protocol < our_min_compatible_protocol      → reject
 *   our_protocol  < peer_min_compatible_protocol     → reject (skipped when
 *                                                      the peer omits its floor)
 *   peer_protocol != our_protocol (same major)       → skew (warn but allow)
 *   otherwise                                        → ok
 *
 * Application semvers play no part in the verdict; a legacy peer that predates
 * `min_compatible_protocol` passes `null` and simply skips the second floor
 * check.
 */
export function evaluateCompat(
  peerProtocolVersion: string,
  peerMinCompatibleProtocol: string | null,
  ourProtocolVersion: string,
  ourMinCompatibleProtocol: string,
): HandshakeVerdict {
  const peerMajor = parseProtocolMajor(peerProtocolVersion);
  const ourMajor = parseProtocolMajor(ourProtocolVersion);
  if (peerMajor === null || ourMajor === null || peerMajor !== ourMajor) {
    return {
      compat: 'reject',
      reason: `protocol major mismatch (peer=${peerProtocolVersion}, local=${ourProtocolVersion})`,
    };
  }

  const againstOurFloor = compareProtocol(peerProtocolVersion, ourMinCompatibleProtocol);
  if (againstOurFloor === null) {
    return {
      compat: 'reject',
      reason: `peer protocol version unparseable: ${peerProtocolVersion}`,
    };
  }
  if (againstOurFloor < 0) {
    return {
      compat: 'reject',
      reason: `peer protocol ${peerProtocolVersion} is below local min_compatible_protocol ${ourMinCompatibleProtocol}`,
    };
  }

  if (peerMinCompatibleProtocol !== null) {
    const againstPeerFloor = compareProtocol(ourProtocolVersion, peerMinCompatibleProtocol);
    if (againstPeerFloor === null) {
      if (parseProtocolParts(ourProtocolVersion) === null) {
        return {
          compat: 'reject',
          reason: `local protocol version unparseable: ${ourProtocolVersion}`,
        };
      }
      return {
        compat: 'reject',
        reason: `peer min_compatible_protocol unparseable: ${peerMinCompatibleProtocol}`,
      };
    }
    if (againstPeerFloor < 0) {
      return {
        compat: 'reject',
        reason: `local protocol ${ourProtocolVersion} is below peer min_compatible_protocol ${peerMinCompatibleProtocol}`,
      };
    }
  }

  // 不能做字符串相等：Rust 侧用的是 compare_protocol，因此 "1.3.0" ≡ "1.3"、
  // "01.0" ≡ "1.0"、拖尾点的 "1." 也解析为 (1,0)。三者都应判 ok 而非 skew。
  const ordering = compareProtocol(peerProtocolVersion, ourProtocolVersion);
  if (ordering === null) {
    return {
      compat: 'reject',
      reason: `peer protocol version unparseable: ${peerProtocolVersion}`,
    };
  }
  return ordering === 0 ? { compat: 'ok' } : { compat: 'skew' };
}

/** Own-side protocol declaration used by {@link parseHandshakeResult}. */
export interface HandshakeSelf {
  protocolVersion?: string;
  minCompatibleProtocol?: string;
}

/**
 * Verdict for a received {@link HandshakeResult}, evaluated against our own
 * declared protocol version / floor.
 *
 * Pure and side-effect free: nothing here sends a frame. Callers that want the
 * connection refused act on `compat === 'reject'` themselves.
 */
export function parseHandshakeResult(result: HandshakeResult, self: HandshakeSelf = {}): HandshakeVerdict {
  const peerProtocol = typeof result?.protocol_version === 'string' ? result.protocol_version : '';
  const peerFloor =
    typeof result?.min_compatible_protocol === 'string' ? result.min_compatible_protocol : null;
  return evaluateCompat(
    peerProtocol,
    peerFloor,
    self.protocolVersion ?? PROTOCOL_VERSION,
    self.minCompatibleProtocol ?? MIN_COMPATIBLE_PROTOCOL,
  );
}
