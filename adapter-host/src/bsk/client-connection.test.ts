/**
 * Connection-level contract of `BskClient` (spec 021, P0-3):
 *
 *   - the visitor id travels as the `user_id` query parameter,
 *   - the shared secret travels as the `X-Adapter-Secret` request header,
 *   - both default to the environment when the caller does not pass them,
 *     so `src/bsk-cli.ts` and `src/page/bsk-browser-factory.ts` stay
 *     untouched.
 *
 * The assertions run against a real WebSocket server so the query string and
 * the request headers are checked as they arrive on the wire.
 */

import { EventEmitter } from 'node:events';

import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { WebSocket, WebSocketServer } from 'ws';

import { BskClient } from './client.js';

const ENV_KEYS = ['OPENCLI_BSK_VISITOR_ID', 'OPENCLI_RELAY_SECRET'] as const;

interface CapturedConnection {
  url: string;
  headers: Record<string, string | string[] | undefined>;
}

async function startCapturingServer(): Promise<{
  url: string;
  connections: CapturedConnection[];
  close: () => Promise<void>;
}> {
  const server = new WebSocketServer({ port: 0, host: '127.0.0.1' });
  const connections: CapturedConnection[] = [];
  server.on('connection', (socket, request) => {
    connections.push({ url: request.url ?? '', headers: { ...request.headers } });
  });
  await new Promise<void>((resolve) => server.once('listening', () => resolve()));
  const address = server.address();
  const port = typeof address === 'object' && address !== null ? address.port : 0;
  return {
    url: `ws://127.0.0.1:${port}`,
    connections,
    close: () => new Promise<void>((resolve) => server.close(() => resolve())),
  };
}

describe('BskClient connection parameters', () => {
  let savedEnv: Record<string, string | undefined>;

  beforeEach(() => {
    savedEnv = {};
    for (const key of ENV_KEYS) {
      savedEnv[key] = process.env[key];
      delete process.env[key];
    }
  });

  afterEach(() => {
    for (const key of ENV_KEYS) {
      const value = savedEnv[key];
      if (value === undefined) delete process.env[key];
      else process.env[key] = value;
    }
  });

  it('sends the visitor id as user_id and the browser id as browser_id', async () => {
    const server = await startCapturingServer();
    const client = await BskClient.connect({
      url: server.url,
      browserId: 'browser-3',
      visitorId: 'visitor-7',
    });
    try {
      expect(server.connections).toHaveLength(1);
      const params = new URL(server.connections[0].url, 'ws://127.0.0.1').searchParams;
      expect(params.get('user_id')).toBe('visitor-7');
      expect(params.get('browser_id')).toBe('browser-3');
      expect(new URL(client.url).searchParams.get('user_id')).toBe('visitor-7');
    } finally {
      await client.close();
      await server.close();
    }
  });

  it('sends the secret as the X-Adapter-Secret header', async () => {
    const server = await startCapturingServer();
    const client = await BskClient.connect({ url: server.url, secret: 's3cret' });
    try {
      expect(server.connections).toHaveLength(1);
      expect(server.connections[0].headers['x-adapter-secret']).toBe('s3cret');
    } finally {
      await client.close();
      await server.close();
    }
  });

  it('omits the secret header when no secret is configured', async () => {
    const server = await startCapturingServer();
    const client = await BskClient.connect({ url: server.url });
    try {
      expect(server.connections[0].headers['x-adapter-secret']).toBeUndefined();
    } finally {
      await client.close();
      await server.close();
    }
  });

  it('reads the visitor id and the secret from the environment when not passed explicitly', async () => {
    process.env.OPENCLI_BSK_VISITOR_ID = 'visitor-env';
    process.env.OPENCLI_RELAY_SECRET = 'secret-env';
    const server = await startCapturingServer();
    const client = await BskClient.connect({ url: server.url });
    try {
      const params = new URL(server.connections[0].url, 'ws://127.0.0.1').searchParams;
      expect(params.get('user_id')).toBe('visitor-env');
      expect(server.connections[0].headers['x-adapter-secret']).toBe('secret-env');
    } finally {
      await client.close();
      await server.close();
    }
  });

  it('prefers explicit options over the environment', async () => {
    process.env.OPENCLI_BSK_VISITOR_ID = 'visitor-env';
    process.env.OPENCLI_RELAY_SECRET = 'secret-env';
    const server = await startCapturingServer();
    const client = await BskClient.connect({
      url: server.url,
      visitorId: 'visitor-explicit',
      secret: 'secret-explicit',
    });
    try {
      const params = new URL(server.connections[0].url, 'ws://127.0.0.1').searchParams;
      expect(params.get('user_id')).toBe('visitor-explicit');
      expect(server.connections[0].headers['x-adapter-secret']).toBe('secret-explicit');
    } finally {
      await client.close();
      await server.close();
    }
  });

  it('never overrides a query parameter that is already present in the URL', async () => {
    const server = await startCapturingServer();
    const client = await BskClient.connect({
      url: `${server.url}/bsk?user_id=preset`,
      visitorId: 'visitor-7',
    });
    try {
      const params = new URL(server.connections[0].url, 'ws://127.0.0.1').searchParams;
      expect(params.get('user_id')).toBe('preset');
    } finally {
      await client.close();
      await server.close();
    }
  });

  it('passes the assembled headers to an injected socket factory', async () => {
    const seen: { url: string; headers?: Record<string, string> }[] = [];
    const client = await BskClient.connect({
      url: 'ws://relay.example/bsk',
      browserId: 'browser-3',
      visitorId: 'visitor-7',
      secret: 's3cret',
      webSocketFactory: (url, options) => {
        seen.push({ url, headers: options?.headers });
        const socket = new FakeSocket();
        process.nextTick(() => socket.emit('open'));
        return socket as unknown as WebSocket;
      },
    });
    try {
      expect(seen).toHaveLength(1);
      expect(seen[0].headers).toEqual({ 'X-Adapter-Secret': 's3cret' });
      const params = new URL(seen[0].url).searchParams;
      expect(params.get('browser_id')).toBe('browser-3');
      expect(params.get('user_id')).toBe('visitor-7');
    } finally {
      await client.close();
    }
  });
});

/** Minimal socket double for the factory seam. */
class FakeSocket extends EventEmitter {
  readyState: number = WebSocket.CONNECTING;

  send(): void {
    // No peer in this test.
  }

  close(): void {
    this.readyState = WebSocket.CLOSED;
    this.emit('close');
  }

  terminate(): void {
    this.readyState = WebSocket.CLOSED;
  }
}
