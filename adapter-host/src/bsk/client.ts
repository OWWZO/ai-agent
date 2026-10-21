/**
 * Persistent WebSocket client for the bsk protocol.
 *
 * The Rust CLI opens a fresh UDS connection per call (`ipc_client.rs`), which
 * is cheap locally but unacceptable across the network: sessions must stay
 * pinned to one socket and reconnects are expensive. This client therefore
 * keeps one long-lived WS open, correlates frames by `id`, and rejects every
 * in-flight call as soon as the socket drops.
 *
 * Wire format (see `frame.rs`):
 *   request  → `{ id, method, params? }`   (`params` omitted when undefined)
 *   response → `{ id, result }` | `{ id, error: { code, message, data? } }`
 *   event    → `{ event, payload }`
 */

import { WebSocket, type RawData } from 'ws';

import {
  ADAPTER_SECRET_HEADER,
  BSK_VISITOR_ID_ENV,
  RELAY_SECRET_ENV,
} from './env.js';
import { BskRpcError, type RpcErrorCode } from './errors.js';
import { METHODS, type Method } from './methods.js';
import type { CancelResult, EventFrame, RequestFrame, RpcErrorLike } from './protocol.js';

/** Default per-call timeout, matching the contract's `call<T>` default. */
export const DEFAULT_CALL_TIMEOUT_MS = 30_000;

/** Default connect timeout. */
export const DEFAULT_CONNECT_TIMEOUT_MS = 10_000;

/** How long an aborted call waits for the daemon's `cancelled` reply. */
export const CANCEL_RESPONSE_GRACE = 2_000;

/** Round-trip budget for the `cancel` frame itself. */
export const CANCEL_FRAME_TIMEOUT = 2_000;

/**
 * Per-process tag mixed into rpc ids.
 *
 * The same gateway can serve several CLI processes at once (the Java side
 * spawns one per browser-tool call), and every one of them would otherwise
 * start counting at `bsk-1`. The id is the correlation key of the answer
 * frame, so a collision would cross two requests' replies.
 * The Rust CLI uses `random_id()` for the same reason.
 */
const PROCESS_TAG = Math.random().toString(36).slice(2, 8);

export interface BskCallOptions {
  /** Overrides `DEFAULT_CALL_TIMEOUT_MS` (or the client default). */
  timeoutMs?: number;
  /** On abort the client sends `cancel {rpc_id}` and settles within grace. */
  signal?: AbortSignal;
}

export interface BskClientOptions {
  /** Relay gateway URL (`ws://` or `wss://`). */
  url: string;
  /** Routing hint for the relay (`OPENCLI_RELAY_BROWSER_ID`). */
  browserId?: string;
  /**
   * Visitor (user) id sent as the `user_id` query parameter.
   * Defaults to the `OPENCLI_BSK_VISITOR_ID` environment variable.
   */
  visitorId?: string;
  /**
   * Shared secret sent as the `X-Adapter-Secret` request header.
   * Defaults to the `OPENCLI_RELAY_SECRET` environment variable.
   */
  secret?: string;
  connectTimeoutMs?: number;
  /** Default per-call timeout; `call()` options still win. */
  callTimeoutMs?: number;
  /** Test seam; defaults to {@link CANCEL_RESPONSE_GRACE}. */
  cancelGraceMs?: number;
  /** Test seam; defaults to {@link CANCEL_FRAME_TIMEOUT}. */
  cancelFrameTimeoutMs?: number;
  /** Test seam for injecting a socket implementation. */
  webSocketFactory?: (url: string, options?: { headers?: Record<string, string> }) => WebSocket;
}

export type BskEventMap = {
  event: EventFrame;
  disconnect: BskRpcError;
  protocol_error: Error;
};

type Listener<T> = (value: T) => void;

interface PendingCall {
  id: string;
  method: Method;
  resolve: (value: unknown) => void;
  reject: (error: Error) => void;
  timer: ReturnType<typeof setTimeout>;
  graceTimer?: ReturnType<typeof setTimeout>;
  signal?: AbortSignal;
  abortListener?: () => void;
}

export type ParsedFrame =
  | { type: 'response'; id: string; result?: unknown; error?: RpcErrorLike }
  | { type: 'event'; frame: EventFrame }
  | { type: 'request'; id: string; method: string; params?: unknown }
  | { type: 'invalid'; reason: string };

/** Serialize a request frame exactly like serde: `params` omitted when undefined. */
export function serializeRequest(frame: RequestFrame): string {
  const wire: Record<string, unknown> = { id: frame.id, method: frame.method };
  if (frame.params !== undefined) wire.params = frame.params;
  return JSON.stringify(wire);
}

/**
 * Classify an inbound frame. Responses must carry exactly one of
 * `result` / `error` (an explicit `result: null` is valid).
 */
export function parseFrame(text: string): ParsedFrame {
  let value: unknown;
  try {
    value = JSON.parse(text);
  } catch {
    return { type: 'invalid', reason: 'frame is not valid JSON' };
  }
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    return { type: 'invalid', reason: 'frame must be a JSON object' };
  }
  const record = value as Record<string, unknown>;

  if (typeof record.event === 'string') {
    return {
      type: 'event',
      frame: {
        event: record.event as EventFrame['event'],
        payload: record.payload,
      },
    };
  }

  if (typeof record.method === 'string') {
    if (typeof record.id !== 'string') {
      return { type: 'invalid', reason: 'request frame is missing a string id' };
    }
    return { type: 'request', id: record.id, method: record.method, params: record.params };
  }

  if (typeof record.id !== 'string') {
    return { type: 'invalid', reason: 'frame is missing a string id' };
  }
  const hasResult = Object.prototype.hasOwnProperty.call(record, 'result');
  const hasError = Object.prototype.hasOwnProperty.call(record, 'error');
  if (hasResult === hasError) {
    return {
      type: 'invalid',
      reason: 'response frame must carry exactly one of result or error',
    };
  }
  if (hasError) {
    const error = record.error;
    if (
      typeof error !== 'object' ||
      error === null ||
      typeof (error as Record<string, unknown>).code !== 'string' ||
      typeof (error as Record<string, unknown>).message !== 'string'
    ) {
      return { type: 'invalid', reason: 'error payload must carry code and message' };
    }
    return { type: 'response', id: record.id, error: error as RpcErrorLike };
  }
  return { type: 'response', id: record.id, result: record.result };
}

function rawToString(data: RawData): string {
  if (typeof data === 'string') return data;
  if (Array.isArray(data)) return Buffer.concat(data).toString('utf8');
  if (data instanceof ArrayBuffer) return Buffer.from(data).toString('utf8');
  return data.toString('utf8');
}

/**
 * Append a query parameter, but never override one that is already present
 * (the URL may carry an explicit hint chosen by the host process).
 */
function appendQueryParam(url: string, name: string, value?: string): string {
  if (!value) return url;
  const parsed = new URL(url);
  if (!parsed.searchParams.has(name)) parsed.searchParams.set(name, value);
  return parsed.toString();
}

/** Trim a value and treat a blank string as absent. */
function normalizeOptional(value: string | undefined): string | undefined {
  if (typeof value !== 'string') return undefined;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : undefined;
}

/** Read a trimmed, non-empty environment variable. */
function readEnv(name: string): string | undefined {
  return normalizeOptional(process.env[name]);
}

/**
 * Visitor id: an explicit option always wins; otherwise fall back to
 * `OPENCLI_BSK_VISITOR_ID` so callers (CLI, page factory, library users) do
 * not have to thread the value through.
 */
function resolveVisitorId(explicit: string | undefined): string | undefined {
  if (explicit !== undefined) return normalizeOptional(explicit);
  return readEnv(BSK_VISITOR_ID_ENV);
}

/** Shared secret: an explicit option always wins; otherwise `OPENCLI_RELAY_SECRET`. */
function resolveSecret(explicit: string | undefined): string | undefined {
  if (explicit !== undefined) return normalizeOptional(explicit);
  return readEnv(RELAY_SECRET_ENV);
}

/** Default socket factory: `ws` accepts an options object as the 2nd argument. */
function defaultWebSocketFactory(
  url: string,
  options?: { headers?: Record<string, string> },
): WebSocket {
  return new WebSocket(url, { headers: options?.headers });
}

function disconnectError(message = 'bsk connection closed'): BskRpcError {
  return new BskRpcError({ code: 'extension_disconnected', message });
}

export class BskClient {
  private readonly ws: WebSocket;
  private readonly callTimeoutMs: number;
  private readonly cancelGraceMs: number;
  private readonly cancelFrameTimeoutMs: number;
  private readonly pending = new Map<string, PendingCall>();
  private readonly listeners = new Map<keyof BskEventMap, Set<Listener<never>>>();
  private counter = 0;
  private closed = false;
  private disconnectEmitted = false;

  /** URL actually dialed (including the `browser_id` / `user_id` hints). */
  readonly url: string;
  readonly browserId?: string;

  private constructor(ws: WebSocket, url: string, options: BskClientOptions) {
    this.ws = ws;
    this.url = url;
    this.browserId = options.browserId;
    this.callTimeoutMs = options.callTimeoutMs ?? DEFAULT_CALL_TIMEOUT_MS;
    this.cancelGraceMs = options.cancelGraceMs ?? CANCEL_RESPONSE_GRACE;
    this.cancelFrameTimeoutMs = options.cancelFrameTimeoutMs ?? CANCEL_FRAME_TIMEOUT;

    this.ws.on('message', (data) => this.handleMessage(rawToString(data)));
    this.ws.on('close', () => this.handleDisconnect(disconnectError('bsk connection closed')));
    this.ws.on('error', (error) => {
      this.handleDisconnect(disconnectError(`bsk connection error: ${error.message}`));
    });
  }

  static connect(options: BskClientOptions): Promise<BskClient> {
    const visitorId = resolveVisitorId(options.visitorId);
    const secret = resolveSecret(options.secret);
    const url = appendQueryParam(
      appendQueryParam(options.url, 'browser_id', options.browserId),
      'user_id',
      visitorId,
    );
    const headers: Record<string, string> = {};
    if (secret) headers[ADAPTER_SECRET_HEADER] = secret;
    const factory = options.webSocketFactory ?? defaultWebSocketFactory;
    const ws = factory(url, { headers });
    const timeoutMs = options.connectTimeoutMs ?? DEFAULT_CONNECT_TIMEOUT_MS;

    return new Promise<BskClient>((resolve, reject) => {
      let settled = false;
      const finish = (error?: Error): void => {
        if (settled) return;
        settled = true;
        clearTimeout(timer);
        ws.off('open', onOpen);
        ws.off('error', onError);
        if (error) {
          try {
            ws.terminate();
          } catch {
            // Socket never opened; nothing to clean up.
          }
          reject(error);
        } else {
          resolve(new BskClient(ws, url, options));
        }
      };
      const onOpen = (): void => finish();
      const onError = (error: Error): void => finish(error);
      const timer = setTimeout(() => {
        finish(new BskRpcError({ code: 'timeout', message: `bsk connect timed out after ${timeoutMs}ms` }));
      }, timeoutMs);
      ws.once('open', onOpen);
      ws.once('error', onError);
    });
  }

  get isConnected(): boolean {
    return !this.closed && this.ws.readyState === WebSocket.OPEN;
  }

  on<K extends keyof BskEventMap>(type: K, listener: Listener<BskEventMap[K]>): () => void {
    let set = this.listeners.get(type);
    if (!set) {
      set = new Set();
      this.listeners.set(type, set);
    }
    set.add(listener as Listener<never>);
    return () => {
      set?.delete(listener as Listener<never>);
    };
  }

  call<T = unknown>(method: Method, params?: unknown, options: BskCallOptions = {}): Promise<T> {
    if (this.closed || this.ws.readyState !== WebSocket.OPEN) {
      return Promise.reject(disconnectError('bsk client is not connected'));
    }
    const signal = options.signal;
    const id = this.nextId();
    if (signal?.aborted) {
      return Promise.reject(
        new BskRpcError({ code: 'cancelled', message: 'rpc aborted before dispatch' }, id),
      );
    }
    const timeoutMs = options.timeoutMs ?? this.callTimeoutMs;

    return new Promise<T>((resolve, reject) => {
      const entry: PendingCall = {
        id,
        method,
        resolve: resolve as (value: unknown) => void,
        reject,
        timer: setTimeout(() => {
          this.settleError(
            id,
            new BskRpcError(
              { code: 'timeout', message: `rpc ${id} (${method}) timed out after ${timeoutMs}ms` },
              id,
            ),
          );
        }, timeoutMs),
        signal,
      };
      if (signal) {
        const onAbort = (): void => this.abortPending(entry);
        entry.abortListener = onAbort;
        signal.addEventListener('abort', onAbort, { once: true });
      }
      this.pending.set(id, entry);
      try {
        this.ws.send(serializeRequest({ id, method, params }));
      } catch (error) {
        this.settleError(
          id,
          disconnectError(error instanceof Error ? error.message : 'failed to send bsk frame'),
        );
      }
    });
  }

  /** Send `cancel { rpc_id }`; resolves with the daemon's acknowledgement. */
  async cancel(rpcId: string): Promise<CancelResult> {
    return this.call<CancelResult>(
      METHODS.cancel,
      { rpc_id: rpcId },
      { timeoutMs: this.cancelFrameTimeoutMs },
    );
  }

  async close(): Promise<void> {
    if (this.closed && this.ws.readyState === WebSocket.CLOSED) return;
    this.handleDisconnect(disconnectError('bsk client closed'));
    if (this.ws.readyState === WebSocket.CLOSED) return;
    await new Promise<void>((resolve) => {
      const done = (): void => {
        clearTimeout(timer);
        resolve();
      };
      const timer = setTimeout(() => {
        try {
          this.ws.terminate();
        } catch {
          // Already gone.
        }
        done();
      }, 1_000);
      this.ws.once('close', done);
      try {
        this.ws.close();
      } catch {
        done();
      }
    });
  }

  private nextId(): string {
    this.counter += 1;
    // 进程内计数器不够：同一个网关可以同时挂着多个 CLI 进程（Java 每次调用
    // browser 工具就拉起一个），它们都会从 bsk-1 开始数。id 是应答的关联键，
    // 撞了就会把两个请求的应答搞混。Rust 侧用的是 random_id()，对齐它。
    return `bsk-${PROCESS_TAG}-${this.counter}`;
  }

  private abortPending(entry: PendingCall): void {
    if (!this.pending.has(entry.id)) return;
    // Fire-and-forget: the original call still owns the outcome until the
    // grace window expires, so a daemon-side `cancelled` reply wins.
    void this.cancel(entry.id).catch(() => undefined);
    if (entry.graceTimer !== undefined) return;
    entry.graceTimer = setTimeout(() => {
      this.settleError(
        entry.id,
        new BskRpcError(
          {
            code: 'cancelled',
            message: 'rpc did not respond within cancel grace window',
          },
          entry.id,
        ),
      );
    }, this.cancelGraceMs);
  }

  private settleOk(id: string, result: unknown): void {
    const entry = this.pending.get(id);
    if (!entry) return;
    this.cleanup(entry);
    entry.resolve(result);
  }

  private settleError(id: string, error: Error): void {
    const entry = this.pending.get(id);
    if (!entry) return;
    this.cleanup(entry);
    entry.reject(error);
  }

  private cleanup(entry: PendingCall): void {
    this.pending.delete(entry.id);
    clearTimeout(entry.timer);
    if (entry.graceTimer !== undefined) clearTimeout(entry.graceTimer);
    if (entry.signal && entry.abortListener) {
      entry.signal.removeEventListener('abort', entry.abortListener);
    }
  }

  private handleMessage(text: string): void {
    const parsed = parseFrame(text);
    switch (parsed.type) {
      case 'event':
        this.emit('event', parsed.frame);
        return;
      case 'response': {
        if (parsed.error) {
          const error = new BskRpcError(
            {
              code: parsed.error.code as RpcErrorCode,
              message: parsed.error.message,
              data: parsed.error.data,
            },
            parsed.id,
          );
          this.settleError(parsed.id, error);
        } else {
          this.settleOk(parsed.id, parsed.result);
        }
        return;
      }
      case 'request':
        // The client never serves server-initiated RPCs; surface it instead
        // of silently dropping the frame.
        this.emit(
          'protocol_error',
          new Error(`unexpected server request ${parsed.method} (${parsed.id})`),
        );
        return;
      case 'invalid':
        this.emit('protocol_error', new Error(`invalid bsk frame: ${parsed.reason}`));
        return;
    }
  }

  private handleDisconnect(error: BskRpcError): void {
    this.closed = true;
    for (const id of [...this.pending.keys()]) {
      this.settleError(id, error);
    }
    if (!this.disconnectEmitted) {
      this.disconnectEmitted = true;
      this.emit('disconnect', error);
    }
  }

  private emit<K extends keyof BskEventMap>(type: K, value: BskEventMap[K]): void {
    const set = this.listeners.get(type);
    if (!set) return;
    for (const listener of [...set]) {
      (listener as Listener<BskEventMap[K]>)(value);
    }
  }
}
