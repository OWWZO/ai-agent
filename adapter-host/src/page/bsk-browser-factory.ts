/**
 * BskBrowserFactory — `IBrowserFactory` for the bsk backend (spec 021).
 *
 * Batch A2 note: `src/bsk/**` is owned by batch A1 and may not exist yet. The
 * module is therefore loaded through a lazy, non-literal dynamic import so
 * this file (and `src/runtime.ts`) type-checks and loads before `src/bsk`
 * lands. `connect()` throws `BskUnavailableError` with an actionable message
 * when the module or its expected exports are missing.
 *
 * No `@ts-expect-error` is used: the import result is typed as `unknown` and
 * narrowed at runtime.
 */

import { randomUUID } from 'node:crypto';
import type { IPage } from './ipage.js';
import { ReactorPage, type BskSessionHandle } from './reactor-page.js';
import type { IBrowserFactory } from '../runtime.js';

/** Specifier kept in a variable so TS does not try to resolve it at build time. */
const BSK_MODULE_SPECIFIER = '../bsk/index.js';

/** Minimal shape this factory needs from `src/bsk/client.ts`. */
export interface BskClientLike {
  call<T>(method: string, params?: unknown, opts?: { timeoutMs?: number; signal?: AbortSignal }): Promise<T>;
  close(): Promise<void>;
  /** Instance-style connector (alternative to the static factory). */
  connect?(url: string): Promise<void>;
}

/** Minimal shape this factory needs from `src/bsk/index.ts`. */
export interface BskModuleLike {
  BskClient: BskClientConstructorLike;
  resolveEndpoint: () => { wsUrl: string; browserId?: string };
}

/** `BskClient` may expose a static `connect(options)` factory. */
export interface BskClientConstructorLike {
  new (): BskClientLike;
  connect?(options: { url: string; browserId?: string }): Promise<BskClientLike>;
}

export class BskUnavailableError extends Error {
  constructor(detail: string) {
    super(
      `bsk backend is not available: ${detail}. ` +
      'Ensure src/bsk/index.ts exports BskClient and resolveEndpoint, or set BROWSER_BACKEND=opencli.',
    );
    this.name = 'BskUnavailableError';
  }
}

async function loadBsk(): Promise<BskModuleLike> {
  let mod: unknown;
  try {
    mod = await import(BSK_MODULE_SPECIFIER);
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    throw new BskUnavailableError(`could not load ${BSK_MODULE_SPECIFIER} (${message})`);
  }

  const candidate = mod as Partial<BskModuleLike> | null;
  if (typeof candidate?.BskClient !== 'function') {
    throw new BskUnavailableError(`${BSK_MODULE_SPECIFIER} does not export a BskClient constructor`);
  }
  if (typeof candidate?.resolveEndpoint !== 'function') {
    throw new BskUnavailableError(`${BSK_MODULE_SPECIFIER} does not export resolveEndpoint()`);
  }
  return candidate as BskModuleLike;
}

/**
 * Connect with whichever client shape batch A1 lands: a static
 * `BskClient.connect({ url, browserId })` factory (the current client.ts
 * shape) or an instance `connect(url)` method.
 */
async function connectClient(
  Ctor: BskClientConstructorLike,
  endpoint: { wsUrl: string; browserId?: string },
): Promise<BskClientLike> {
  if (typeof Ctor.connect === 'function') {
    return Ctor.connect({ url: endpoint.wsUrl, browserId: endpoint.browserId });
  }
  const client = new Ctor();
  if (typeof client.connect !== 'function') {
    throw new BskUnavailableError('BskClient exposes neither a static connect() nor an instance connect()');
  }
  await client.connect(endpoint.wsUrl);
  return client;
}

/**
 * True when an RPC failed because the peer does not know the method (older
 * gateway). Only then is the legacy `session.start` fallback safe.
 */
function isUnknownMethodError(err: unknown): boolean {
  const code = (err as { code?: unknown } | null)?.code;
  if (code === 'unknown_method' || code === 'method_not_found') return true;
  const message = err instanceof Error ? err.message : String(err);
  return /unknown method|method not found|unsupported method/i.test(message);
}

export class BskBrowserFactory implements IBrowserFactory {
  private _page: ReactorPage | null = null;
  private _client: BskClientLike | null = null;
  private _sessionId: string | null = null;
  /** 关闭阶段 tool.session_stop 的真实失败；不影响连接清理，仅用于排查。 */
  get lastCloseError(): unknown {
    return this._lastCloseError;
  }
  private _lastCloseError: unknown = null;

  async connect(opts: {
    timeout?: number;
    session?: string;
    cdpEndpoint?: string;
    contextId?: string;
    preferredContextId?: string;
    idleTimeout?: number;
    windowMode?: 'foreground' | 'background';
    surface?: 'browser' | 'adapter';
    siteSession?: 'ephemeral' | 'persistent';
  } = {}): Promise<IPage> {
    if (this._page) return this._page;

    const bsk = await loadBsk();
    const endpoint = bsk.resolveEndpoint();
    if (!endpoint?.wsUrl) {
      throw new BskUnavailableError('resolveEndpoint() returned no wsUrl (is OPENCLI_RELAY_URL set?)');
    }

    const client = await connectClient(bsk.BskClient, endpoint);

    // `tool.session_start` takes a caller-generated session id (see
    // src/bsk/protocol.ts). Fall back to the legacy `session.start` envelope
    // only when the gateway reports the method as unknown — any other failure
    // is a real error and must surface unchanged.
    const requestedSessionId = opts.session?.trim() || `bsk-${randomUUID()}`;
    let sessionId: string | undefined;
    try {
      try {
        await client.call('tool.session_start', {
          session_id: requestedSessionId,
          ...(endpoint.browserId ? { browser_instance_id: endpoint.browserId } : {}),
        });
        sessionId = requestedSessionId;
      } catch (toolErr) {
        if (!isUnknownMethodError(toolErr)) throw toolErr;
        const started = await client.call<{ session_id?: string; sessionId?: string }>('session.start', {
          ...(opts.session ? { task_name: opts.session } : {}),
          ...(endpoint.browserId ? { browser_instance_id: endpoint.browserId } : {}),
        });
        sessionId = started?.session_id ?? started?.sessionId ?? requestedSessionId;
      }
    } catch (err) {
      await client.close();
      throw err;
    }

    if (typeof sessionId !== 'string' || sessionId === '') {
      await client.close();
      throw new BskUnavailableError('session.start did not return a session_id');
    }

    const handle: BskSessionHandle = {
      sessionId,
      call: <T>(method: string, params: unknown, callOpts?: { timeoutMs?: number; signal?: AbortSignal }) =>
        client.call<T>(method, params, callOpts),
    };

    this._client = client;
    this._sessionId = sessionId;
    this._page = new ReactorPage(handle);
    return this._page;
  }

  async close(): Promise<void> {
    const client = this._client;
    const sessionId = this._sessionId;
    this._page = null;
    this._client = null;
    this._sessionId = null;

    if (!client) return;
    try {
      if (sessionId) {
        // `session.stop` 是 daemon 级方法，扩展经 Java 中继访问时会返回
        // `unknown_method`，会话永远无法释放。必须走 `tool.session_stop`。
        // 仅当对端确实是 daemon（未知方法）时才回退，且回退失败不阻塞后续清理。
        try {
          await client.call('tool.session_stop', { session_id: sessionId });
        } catch (toolErr) {
          if (!isUnknownMethodError(toolErr)) {
            // tool.session_stop 存在但执行失败：真实错误，仍需继续关闭连接。
            this._lastCloseError = toolErr;
          } else {
            await client.call('session.stop', { session_id: sessionId }).catch(() => undefined);
          }
        }
      }
    } catch {
      // Best-effort: the session may already be gone or the link may be down.
    } finally {
      await client.close();
    }
  }
}
