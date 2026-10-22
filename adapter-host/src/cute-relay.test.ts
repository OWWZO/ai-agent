import { afterEach, describe, expect, it, vi } from 'vitest';
import http from 'node:http';
import { AddressInfo } from 'node:net';

import { isRelayMode, relayRequest } from './cute-relay.js';
import { requestDaemon } from './browser/daemon-transport.js';
import * as daemonLifecycle from './browser/daemon-lifecycle.js';

describe('cute relay overlay', () => {
  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('is inactive without OPENCLI_RELAY_URL', () => {
    vi.stubEnv('OPENCLI_RELAY_URL', '');
    expect(isRelayMode()).toBe(false);
  });

  it('returns synthetic ready status without touching 19825', async () => {
    vi.stubEnv('OPENCLI_RELAY_URL', 'http://127.0.0.1:9/internal/browser/rpc');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const res = await requestDaemon('/status');
    const body = await res.json() as { extensionConnected: boolean; daemonVersion: string };
    expect(body.extensionConnected).toBe(true);
    expect(body.daemonVersion).toBe('relay');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('forwards /command with userId and secret', async () => {
    const seen: Array<{ url: string; headers: http.IncomingHttpHeaders; body: Record<string, unknown> }> = [];
    const server = await listen(async (req, res) => {
      const chunks: Buffer[] = [];
      for await (const chunk of req) chunks.push(chunk as Buffer);
      const body = JSON.parse(Buffer.concat(chunks).toString('utf8'));
      seen.push({ url: req.url ?? '', headers: req.headers, body });
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ ok: true, id: body.id, data: { url: 'https://example.com' } }));
    });
    try {
      const { port } = server.address() as AddressInfo;
      vi.stubEnv('OPENCLI_RELAY_URL', `http://127.0.0.1:${port}/internal/browser/rpc`);
      vi.stubEnv('OPENCLI_RELAY_VISITOR_ID', 'vis_zhangsan');
      vi.stubEnv('OPENCLI_RELAY_SECRET', 's3cret');

      const res = await requestDaemon('/command', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id: 'cmd_1', action: 'navigate', url: 'https://example.com' }),
        timeout: 2000,
      });
      const json = await res.json() as { ok: boolean };
      expect(json.ok).toBe(true);
      expect(seen).toHaveLength(1);
      expect(seen[0].body.userId).toBe('vis_zhangsan');
      expect(seen[0].body.action).toBe('navigate');
      expect(seen[0].headers['x-adapter-secret']).toBe('s3cret');
      expect(seen[0].headers['x-opencli']).toBe('1');
    } finally {
      await close(server);
    }
  });

  it('answers lease-release locally', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    vi.stubEnv('OPENCLI_RELAY_URL', 'http://127.0.0.1:9/internal/browser/rpc');

    const res = await relayRequest('/command', {
      method: 'POST',
      body: JSON.stringify({ id: 'cmd_lease', action: 'lease-release', session: 'site:bilibili' }),
    });
    const json = await res.json() as { ok: boolean };
    expect(json.ok).toBe(true);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('does not spawn a daemon in relay mode', async () => {
    vi.stubEnv('OPENCLI_RELAY_URL', 'http://127.0.0.1:9/internal/browser/rpc');
    const spawn = vi.spyOn(daemonLifecycle.daemonLifecycleHooks, 'spawnDaemonProcess');
    const health = await daemonLifecycle.ensureBrowserBridgeReady({ verbose: false, timeoutSeconds: 1 });
    expect(health.state).toBe('ready');
    expect(spawn).not.toHaveBeenCalled();
  });

  it('keeps the 19825 path when relay env is unset', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ ok: true, pid: 1, uptime: 0, extensionConnected: true, pending: 0, memoryMB: 1, port: 19825 }),
    });
    vi.stubGlobal('fetch', fetchMock);
    await requestDaemon('/status');
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringMatching(/127\.0\.0\.1:19825\/status$/),
      expect.objectContaining({
        headers: expect.objectContaining({ 'X-OpenCLI': '1' }),
      }),
    );
  });
});

function listen(handler: http.RequestListener): Promise<http.Server> {
  return new Promise((resolve) => {
    const server = http.createServer(handler);
    server.listen(0, '127.0.0.1', () => resolve(server));
  });
}

function close(server: http.Server): Promise<void> {
  return new Promise((resolve, reject) => server.close((err) => err ? reject(err) : resolve()));
}
