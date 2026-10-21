/**
 * End-to-end over a real (mock) socket: BskClient + command layer must reach
 * the same wire shapes a Rust CLI call would, and library calls must not emit
 * anything to stdout.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { BskClient } from './client.js';
import { evaluateValue } from './commands/evaluate.js';
import { navigate } from './commands/navigate.js';
import { snapshot } from './commands/observe.js';
import { sessionStart, sessionStop } from './commands/session.js';
import { tabList } from './commands/tab.js';
import { METHODS } from './methods.js';
import { startMockBskServer, type MockBskServer } from './mock-server.js';

describe('bsk library over the mock protocol server', () => {
  let server: MockBskServer;
  let client: BskClient;
  let stdout: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    server = await startMockBskServer();
    client = await BskClient.connect({ url: server.url, browserId: 'browser-1' });
    stdout = vi.spyOn(process.stdout, 'write').mockImplementation(() => true);
  });

  afterEach(async () => {
    stdout.mockRestore();
    await client.close();
    await server.close();
  });

  it('drives a session lifecycle and never writes to stdout', async () => {
    server.onCall((request, responder) => {
      switch (request.method) {
        case METHODS.toolSessionStart:
          responder.respond({ agent_window_id: 42 });
          return;
        case METHODS.toolNavigate:
          responder.respond({ tab_id: 7, url: 'https://example.com', reached: 'load' });
          return;
        case METHODS.toolSnapshot:
          responder.respond({ text: '@e1 button "go"', ref_count: 1, tab_id: 7 });
          return;
        case METHODS.toolEvaluate:
          responder.respond({ ok: true, tab_id: 7, value: { title: 'Example' } });
          return;
        case METHODS.toolTabList:
          responder.respond({ tabs: [{ tab_id: 7, scope: 'agent' }] });
          return;
        case METHODS.toolSessionStop:
          responder.respond({});
          return;
        default:
          return;
      }
    });

    const started = await sessionStart(client, { sessionId: 'sess-1' });
    expect(started.agent_window_id).toBe(42);
    expect((await navigate(client, { sessionId: 'sess-1', url: 'https://example.com' })).tab_id).toBe(7);
    expect((await snapshot(client, { sessionId: 'sess-1' })).ref_count).toBe(1);
    expect(await evaluateValue(client, { sessionId: 'sess-1', expression: 'document.title' })).toEqual({
      title: 'Example',
    });
    expect((await tabList(client, { sessionId: 'sess-1' })).tabs).toHaveLength(1);
    await sessionStop(client, { sessionId: 'sess-1' });

    expect(stdout).not.toHaveBeenCalled();
    expect(server.requests.map((request) => request.method)).toEqual([
      METHODS.toolSessionStart,
      METHODS.toolNavigate,
      METHODS.toolSnapshot,
      METHODS.toolEvaluate,
      METHODS.toolTabList,
      METHODS.toolSessionStop,
    ]);
    expect(server.requests[0].params).toEqual({ session_id: 'sess-1' });
    expect(server.requests[1].params).toMatchObject({ wait_until: 'load', timeout_ms: 30_000 });
    expect(server.requests[3].params).toMatchObject({
      await_promise: true,
      return_by_value: true,
    });
  });

  it('passes the browser id as a routing hint on connect', () => {
    expect(client.browserId).toBe('browser-1');
    expect(new URL(client.url).searchParams.get('browser_id')).toBe('browser-1');
  });

  it('propagates remote errors to the command layer', async () => {
    server.onCall((request, responder) => {
      if (request.method === METHODS.toolNavigate) {
        responder.fail({ code: 'no_browser_connected', message: 'none' });
      }
    });
    await expect(navigate(client, { sessionId: 's', url: 'https://x' })).rejects.toMatchObject({
      code: 'no_browser_connected',
    });
  });
});
