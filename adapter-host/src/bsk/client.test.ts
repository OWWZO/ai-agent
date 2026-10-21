import { describe, expect, it } from 'vitest';

import {
  BskClient,
  CANCEL_FRAME_TIMEOUT,
  CANCEL_RESPONSE_GRACE,
  DEFAULT_CALL_TIMEOUT_MS,
  parseFrame,
  serializeRequest,
} from './client.js';
import { BskRpcError } from './errors.js';
import { METHODS } from './methods.js';
import { startMockBskServer, type MockBskServer } from './mock-server.js';

async function withServer(
  fn: (server: MockBskServer, client: BskClient) => Promise<void>,
  options: { callTimeoutMs?: number; cancelGraceMs?: number } = {},
): Promise<void> {
  const server = await startMockBskServer();
  const client = await BskClient.connect({ url: server.url, ...options });
  try {
    await fn(server, client);
  } finally {
    await client.close();
    await server.close();
  }
}

describe('frame encoding', () => {
  it('omits params when undefined and keeps explicit null', () => {
    expect(serializeRequest({ id: 'a', method: 'system.ping' })).toBe(
      '{"id":"a","method":"system.ping"}',
    );
    expect(serializeRequest({ id: 'b', method: 'x', params: null })).toBe(
      '{"id":"b","method":"x","params":null}',
    );
    expect(serializeRequest({ id: 'c', method: 'x', params: { a: 1 } })).toBe(
      '{"id":"c","method":"x","params":{"a":1}}',
    );
  });

  it('decodes flat responses and treats result:null as success', () => {
    expect(parseFrame('{"id":"1","result":null}')).toEqual({
      type: 'response',
      id: '1',
      result: null,
    });
    expect(parseFrame('{"id":"2","error":{"code":"timeout","message":"slow"}}')).toEqual({
      type: 'response',
      id: '2',
      error: { code: 'timeout', message: 'slow' },
    });
  });

  it('rejects ambiguous, malformed, and null-error frames', () => {
    expect(parseFrame('{"id":"1"}')).toMatchObject({ type: 'invalid' });
    expect(parseFrame('{"id":"1","result":1,"error":{"code":"x","message":"y"}}')).toMatchObject({
      type: 'invalid',
    });
    expect(parseFrame('{"id":"1","error":null}')).toMatchObject({ type: 'invalid' });
    expect(parseFrame('not json')).toMatchObject({ type: 'invalid' });
    expect(parseFrame('[]')).toMatchObject({ type: 'invalid' });
  });

  it('decodes event frames', () => {
    expect(parseFrame('{"event":"system.heartbeat","payload":{}}')).toEqual({
      type: 'event',
      frame: { event: 'system.heartbeat', payload: {} },
    });
  });
});

describe('BskClient over a live mock server', () => {
  it('correlates concurrent calls by id on one persistent connection', async () => {
    await withServer(async (server, client) => {
      server.onCall((request, responder) => {
        if (request.method === 'tool.wait_ms') {
          const params = request.params as { duration_ms: number };
          setTimeout(() => responder.respond({ waited_ms: params.duration_ms }), 5);
          return;
        }
      });
      const [slow, fast] = await Promise.all([
        client.call<{ waited_ms: number }>(METHODS.toolWaitMs, { duration_ms: 20 }),
        client.call<{ waited_ms: number }>(METHODS.toolWaitMs, { duration_ms: 1 }),
      ]);
      expect(slow.waited_ms).toBe(20);
      expect(fast.waited_ms).toBe(1);
      expect(server.connectionCount()).toBe(1);
      // id 带进程标签：同一网关可以同时挂着多个 CLI 进程，纯计数器会撞 id。
      const ids = server.requests.map((r) => r.id);
      expect(ids).toHaveLength(2);
      expect(new Set(ids).size).toBe(2);
      expect(ids[0]).toMatch(/^bsk-[a-z0-9]+-1$/);
      expect(ids[1]).toMatch(/^bsk-[a-z0-9]+-2$/);
    });
  });

  it('surfaces error frames as BskRpcError with data', async () => {
    await withServer(async (server, client) => {
      server.onCall((request, responder) => {
        if (request.method === 'tool.navigate') {
          responder.fail({ code: 'not_found', message: 'no tab', data: { tab_id: 9 } });
        }
      });
      const error = await client.call(METHODS.toolNavigate, {}).catch((e: unknown) => e);
      expect(error).toBeInstanceOf(BskRpcError);
      expect((error as BskRpcError).code).toBe('not_found');
      expect((error as BskRpcError).data).toEqual({ tab_id: 9 });
      expect((error as BskRpcError).rpcId).toMatch(/^bsk-[a-z0-9]+-1$/);
    });
  });

  it('times out a silent call', async () => {
    await withServer(
      async (_server, client) => {
        const started = Date.now();
        const error = await client.call(METHODS.toolSnapshot, {}).catch((e: unknown) => e);
        expect(error).toBeInstanceOf(BskRpcError);
        expect((error as BskRpcError).code).toBe('timeout');
        expect(Date.now() - started).toBeGreaterThanOrEqual(40);
      },
      { callTimeoutMs: 40 },
    );
  });

  it('rejects every in-flight call immediately on disconnect', async () => {
    await withServer(async (server, client) => {
      const pending = client.call(METHODS.toolClick, {});
      await new Promise((resolve) => setTimeout(resolve, 20));
      expect(server.requests).toHaveLength(1);
      const started = Date.now();
      server.dropConnections();
      const error = await pending.catch((e: unknown) => e);
      expect(error).toBeInstanceOf(BskRpcError);
      expect((error as BskRpcError).code).toBe('extension_disconnected');
      expect(Date.now() - started).toBeLessThan(500);
    });
  });

  it('emits event frames separately from responses', async () => {
    await withServer(async (server, client) => {
      const seen: string[] = [];
      client.on('event', (event) => seen.push(event.event));
      server.pushEvent({ event: 'session.user_interrupt', payload: { session_id: 's1' } });
      await new Promise((resolve) => setTimeout(resolve, 30));
      expect(seen).toEqual(['session.user_interrupt']);
    });
  });

  it('reports invalid frames without breaking the connection', async () => {
    await withServer(async (server, client) => {
      const errors: Error[] = [];
      client.on('protocol_error', (error) => errors.push(error));
      server.onCall((_request, responder) => {
        responder.raw('{"id":"x"}');
      });
      const pending = client.call(METHODS.toolSnapshot, {});
      const settled = pending.catch((error: unknown) => error);
      await new Promise((resolve) => setTimeout(resolve, 20));
      expect(errors.some((error) => error.message.includes('exactly one of'))).toBe(true);
      expect(client.isConnected).toBe(true);
      // The malformed frame was not correlated, so the real call is still
      // pending until the test closes the client.
      await client.close();
      expect(await settled).toBeInstanceOf(BskRpcError);
    });
  });
});

describe('cancellation', () => {
  it('exposes the Rust constants', () => {
    expect(CANCEL_RESPONSE_GRACE).toBe(2_000);
    expect(CANCEL_FRAME_TIMEOUT).toBe(2_000);
    expect(DEFAULT_CALL_TIMEOUT_MS).toBe(30_000);
  });

  it('sends cancel and settles within the grace window when the peer is silent', async () => {
    await withServer(
      async (server, client) => {
        const controller = new AbortController();
        const started = Date.now();
        const pending = client.call(METHODS.toolClick, {}, { signal: controller.signal });
        await new Promise((resolve) => setTimeout(resolve, 10));
        controller.abort();
        const error = await pending.catch((e: unknown) => e);
        const elapsed = Date.now() - started;
        expect(error).toBeInstanceOf(BskRpcError);
        expect((error as BskRpcError).code).toBe('cancelled');
        expect(elapsed).toBeLessThan(2_000);
        expect(server.requests.map((r) => r.method)).toEqual(['tool.click', 'cancel']);
        expect(server.requests[1].params).toEqual({ rpc_id: server.requests[0].id });
      },
      { callTimeoutMs: 30_000, cancelGraceMs: 150 },
    );
  });

  it('lets the peer cancelled reply win over the local grace timer', async () => {
    await withServer(
      async (server, client) => {
        let held: { fail(error: { code: string; message: string }): void } | undefined;
        server.onCall((request, responder) => {
          if (request.method === 'tool.click') {
            // Hold the original call until the cancel frame arrives.
            held = responder;
            return;
          }
          if (request.method === 'cancel') {
            responder.respond({ cancelled: true });
            held?.fail({ code: 'cancelled', message: 'aborted' });
          }
        });
        const controller = new AbortController();
        const pending = client.call(METHODS.toolClick, {}, { signal: controller.signal });
        await new Promise((resolve) => setTimeout(resolve, 20));
        controller.abort();
        const error = await pending.catch((e: unknown) => e);
        expect((error as BskRpcError).code).toBe('cancelled');
        expect((error as BskRpcError).message).toBe('aborted');
      },
      { cancelGraceMs: 1_000 },
    );
  });

  it('rejects an already-aborted signal before dispatch', async () => {
    await withServer(async (server, client) => {
      const controller = new AbortController();
      controller.abort();
      const error = await client
        .call(METHODS.toolClick, {}, { signal: controller.signal })
        .catch((e: unknown) => e);
      expect((error as BskRpcError).code).toBe('cancelled');
      expect(server.requests).toHaveLength(0);
    });
  });
});
