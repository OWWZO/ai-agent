/**
 * In-process bsk test double: a WebSocket server that speaks the flat
 * `{id, result}` / `{id, error}` frame format from `frame.rs`.
 *
 * Used by unit tests now and by the later end-to-end work as a stand-in for
 * the real browser extension (spec 021 T6.1).
 */

import { WebSocketServer, WebSocket, type RawData } from 'ws';

import type { EventFrame, RpcErrorLike } from './protocol.js';

export interface MockBskRequest {
  id: string;
  method: string;
  params?: unknown;
}

export interface MockBskResponder {
  readonly request: MockBskRequest;
  readonly socket: WebSocket;
  /** Reply `{id, result}`. `undefined` is sent as `null` (serde `Ok(Null)`). */
  respond(result?: unknown): void;
  /** Reply `{id, error}`. */
  fail(error: RpcErrorLike): void;
  /** Send a raw text frame (for malformed-frame tests). */
  raw(text: string): void;
  readonly settled: boolean;
}

export type MockBskReply = { result: unknown } | { error: RpcErrorLike };

export type MockBskHandler = (
  request: MockBskRequest,
  responder: MockBskResponder,
) => MockBskReply | void | Promise<MockBskReply | void>;

export interface MockBskServerOptions {
  /** 0 (default) picks an ephemeral port. */
  port?: number;
  host?: string;
  /** Auto-answer `cancel` with `{cancelled:true}` (default true). */
  autoCancel?: boolean;
  /** Auto-answer `system.ping` with `{pong:true}` (default false). */
  autoPing?: boolean;
}

export interface MockBskServer {
  url: string;
  port: number;
  /** Every request seen, in arrival order (cancel frames included). */
  requests: MockBskRequest[];
  /** Register a handler; handlers run in registration order. */
  onCall(handler: MockBskHandler): () => void;
  /** Push an event frame to every connected client. */
  pushEvent(event: EventFrame): void;
  /** Abruptly drop all client sockets (simulates relay disconnect). */
  dropConnections(): void;
  connectionCount(): number;
  close(): Promise<void>;
}

function toText(data: RawData): string {
  if (typeof data === 'string') return data;
  if (Array.isArray(data)) return Buffer.concat(data).toString('utf8');
  if (data instanceof ArrayBuffer) return Buffer.from(data).toString('utf8');
  return data.toString('utf8');
}

function parseRequest(text: string): MockBskRequest | null {
  let value: unknown;
  try {
    value = JSON.parse(text);
  } catch {
    return null;
  }
  if (typeof value !== 'object' || value === null) return null;
  const record = value as Record<string, unknown>;
  if (typeof record.id !== 'string' || typeof record.method !== 'string') return null;
  return { id: record.id, method: record.method, params: record.params };
}

export async function startMockBskServer(
  options: MockBskServerOptions = {},
): Promise<MockBskServer> {
  const autoCancel = options.autoCancel ?? true;
  const autoPing = options.autoPing ?? false;
  const server = new WebSocketServer({ port: options.port ?? 0, host: options.host ?? '127.0.0.1' });
  const sockets = new Set<WebSocket>();
  const handlers: MockBskHandler[] = [];
  const requests: MockBskRequest[] = [];

  server.on('connection', (socket) => {
    sockets.add(socket);
    socket.on('close', () => sockets.delete(socket));
    socket.on('error', () => sockets.delete(socket));
    socket.on('message', (data) => {
      const request = parseRequest(toText(data));
      if (!request) return;
      requests.push(request);
      void dispatch(socket, request);
    });
  });

  await new Promise<void>((resolve, reject) => {
    server.once('listening', () => resolve());
    server.once('error', reject);
  });

  async function dispatch(socket: WebSocket, request: MockBskRequest): Promise<void> {
    let settled = false;
    const responder: MockBskResponder = {
      request,
      socket,
      get settled() {
        return settled;
      },
      respond(result?: unknown) {
        if (settled) return;
        settled = true;
        send(socket, { id: request.id, result: result === undefined ? null : result });
      },
      fail(error: RpcErrorLike) {
        if (settled) return;
        settled = true;
        send(socket, { id: request.id, error });
      },
      raw(text: string) {
        if (settled) return;
        settled = true;
        if (socket.readyState === WebSocket.OPEN) socket.send(text);
      },
    };

    for (const handler of [...handlers]) {
      try {
        const reply = await handler(request, responder);
        if (reply !== undefined && !settled) {
          if ('error' in reply) responder.fail(reply.error);
          else responder.respond(reply.result);
        }
      } catch (error) {
        if (!settled) {
          responder.fail({
            code: 'protocol_error',
            message: error instanceof Error ? error.message : 'mock handler threw',
          });
        }
      }
      if (settled) return;
    }

    if (autoCancel && request.method === 'cancel') {
      responder.respond({ cancelled: true });
      return;
    }
    if (autoPing && request.method === 'system.ping') {
      responder.respond({ pong: true });
      return;
    }
    // Unhandled requests stay pending on purpose: tests exercise timeouts,
    // aborts, and disconnects against exactly this state.
  }

  function send(socket: WebSocket, frame: unknown): void {
    if (socket.readyState === WebSocket.OPEN) socket.send(JSON.stringify(frame));
  }

  const address = server.address();
  const port = typeof address === 'object' && address !== null ? address.port : 0;
  const host = options.host ?? '127.0.0.1';
  const url = `ws://${host}:${port}`;

  return {
    url,
    port,
    requests,
    onCall(handler: MockBskHandler) {
      handlers.push(handler);
      return () => {
        const index = handlers.indexOf(handler);
        if (index >= 0) handlers.splice(index, 1);
      };
    },
    pushEvent(event: EventFrame) {
      for (const socket of sockets) send(socket, event);
    },
    dropConnections() {
      for (const socket of sockets) {
        socket.terminate();
        sockets.delete(socket);
      }
    },
    connectionCount() {
      return sockets.size;
    },
    async close() {
      for (const socket of sockets) socket.terminate();
      sockets.clear();
      await new Promise<void>((resolve) => server.close(() => resolve()));
    },
  };
}
