/**
 * Resolve the bsk relay endpoint from the environment.
 *
 * The Rust CLI runs `ensure_daemon()` (probe local UDS, spawn if missing).
 * The TS client must never spawn a process: it always talks to the remote
 * relay gateway, whose address the host process injects.
 *
 * Variable roles (spec 021, P0-3):
 *   - `OPENCLI_BSK_RELAY_URL` — the bsk WebSocket gateway. Preferred.
 *   - `OPENCLI_RELAY_URL` — the *legacy* Cute relay, which is an **HTTP**
 *     base URL (`http://host/internal/browser/rpc`). It is only reused as a
 *     fallback when it happens to point at a WebSocket gateway, so an HTTP
 *     value is never mistaken for the bsk endpoint.
 */

import {
  BSK_RELAY_URL_ENV,
  BSK_VISITOR_ID_ENV,
  LEGACY_RELAY_URL_ENV,
  RELAY_BROWSER_ID_ENV,
  RELAY_SECRET_ENV,
} from './env.js';

export { BSK_RELAY_URL_ENV, BSK_VISITOR_ID_ENV, LEGACY_RELAY_URL_ENV, RELAY_BROWSER_ID_ENV, RELAY_SECRET_ENV };

export interface BskEndpoint {
  /** WebSocket URL of the relay gateway, e.g. `wss://host/internal/bsk`. */
  wsUrl: string;
  /** Optional browser instance id used to route to one extension. */
  browserId?: string;
  /** Optional visitor id; sent as the `user_id` query parameter. */
  visitorId?: string;
  /** Optional shared secret; sent as the `X-Adapter-Secret` header. */
  secret?: string;
}

export class BskEndpointError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'BskEndpointError';
  }
}

/**
 * Throws a descriptive error when neither `OPENCLI_BSK_RELAY_URL` nor a
 * WebSocket-shaped `OPENCLI_RELAY_URL` is available — a plain `http(s)://`
 * legacy value is rejected so a misconfiguration fails at connect time with a
 * clear message instead of a protocol-level timeout.
 */
export function resolveEndpoint(env: NodeJS.ProcessEnv = process.env): BskEndpoint {
  const explicit = readEnvFrom(env, BSK_RELAY_URL_ENV);
  if (explicit) {
    parseWsUrl(explicit, BSK_RELAY_URL_ENV);
    return buildEndpoint(env, explicit);
  }

  const fallback = readEnvFrom(env, LEGACY_RELAY_URL_ENV);
  if (!fallback) {
    throw new BskEndpointError(
      `${BSK_RELAY_URL_ENV} is not set: bsk backend requires the relay gateway WebSocket URL (ws:// or wss://); ` +
        `${LEGACY_RELAY_URL_ENV} is only reused when it points at a WebSocket gateway`,
    );
  }

  // The legacy Cute relay uses the same variable as an HTTP base URL, so a
  // non-WebSocket value must not be accepted as the bsk endpoint.
  if (!parseWebSocketUrl(fallback)) {
    throw new BskEndpointError(
      `${BSK_RELAY_URL_ENV} is not set and ${LEGACY_RELAY_URL_ENV} is not a WebSocket URL (${fallback}); ` +
        'bsk backend requires the relay gateway WebSocket URL (ws:// or wss://)',
    );
  }
  return buildEndpoint(env, fallback);
}

function buildEndpoint(env: NodeJS.ProcessEnv, wsUrl: string): BskEndpoint {
  const browserId = readEnvFrom(env, RELAY_BROWSER_ID_ENV);
  const visitorId = readEnvFrom(env, BSK_VISITOR_ID_ENV);
  const secret = readEnvFrom(env, RELAY_SECRET_ENV);
  return {
    wsUrl,
    ...(browserId ? { browserId } : {}),
    ...(visitorId ? { visitorId } : {}),
    ...(secret ? { secret } : {}),
  };
}

/** Parse and validate an explicitly configured WebSocket URL. */
function parseWsUrl(value: string, envName: string): URL {
  let parsed: URL;
  try {
    parsed = new URL(value);
  } catch {
    throw new BskEndpointError(`${envName} is not a valid URL: ${value}`);
  }
  if (parsed.protocol !== 'ws:' && parsed.protocol !== 'wss:') {
    throw new BskEndpointError(
      `${envName} must use ws:// or wss:// (got ${parsed.protocol}); the bsk client speaks WebSocket, not HTTP`,
    );
  }
  return parsed;
}

/** Parse a URL, returning `null` when it is not a `ws://` / `wss://` URL. */
function parseWebSocketUrl(value: string): URL | null {
  let parsed: URL;
  try {
    parsed = new URL(value);
  } catch {
    return null;
  }
  if (parsed.protocol !== 'ws:' && parsed.protocol !== 'wss:') return null;
  return parsed;
}

function readEnvFrom(env: NodeJS.ProcessEnv, name: string): string | undefined {
  const value = env[name];
  if (typeof value !== 'string') return undefined;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : undefined;
}
