import { describe, expect, it } from 'vitest';

import { METHODS } from '../methods.js';
import { navigate, navigateBack, navigateForward, reload } from './navigate.js';
import { RecordingCaller } from './test-support.js';

describe('navigate', () => {
  it('defaults to wait_until=load and a 30s budget', async () => {
    const client = new RecordingCaller().reply(METHODS.toolNavigate, { tab_id: 1, reached: 'load' });
    await navigate(client, { sessionId: 's1', url: 'https://example.com' });
    expect(client.last(METHODS.toolNavigate).params).toEqual({
      session_id: 's1',
      url: 'https://example.com',
      wait_until: 'load',
      timeout_ms: 30_000,
    });
  });

  it('passes tab, wait phase, and timeout through', async () => {
    const client = new RecordingCaller().reply(METHODS.toolNavigate, {});
    await navigate(client, {
      sessionId: 's1',
      url: 'https://example.com',
      tabId: 9,
      waitUntil: 'networkidle',
      timeoutMs: 60_000,
    });
    expect(client.last(METHODS.toolNavigate).params).toEqual({
      session_id: 's1',
      url: 'https://example.com',
      tab_id: 9,
      wait_until: 'networkidle',
      timeout_ms: 60_000,
    });
  });

  it('rejects a missing url or session before calling', async () => {
    const client = new RecordingCaller();
    await expect(navigate(client, { sessionId: 's1', url: '' })).rejects.toThrow(/requires a url/);
    await expect(navigate(client, { sessionId: '', url: 'https://x' })).rejects.toThrow(
      /--session/,
    );
    expect(client.calls).toHaveLength(0);
  });
});

describe('history navigation', () => {
  it('uses the 15s default and load phase', async () => {
    const client = new RecordingCaller()
      .reply(METHODS.toolNavigateBack, {})
      .reply(METHODS.toolNavigateForward, {})
      .reply(METHODS.toolReload, {});
    await navigateBack(client, { sessionId: 's1', tabId: 2 });
    await navigateForward(client, { sessionId: 's1' });
    await reload(client, { sessionId: 's1', hard: true });
    expect(client.last(METHODS.toolNavigateBack).params).toEqual({
      session_id: 's1',
      tab_id: 2,
      wait_until: 'load',
      timeout_ms: 15_000,
    });
    expect(client.last(METHODS.toolNavigateForward).params).toEqual({
      session_id: 's1',
      wait_until: 'load',
      timeout_ms: 15_000,
    });
    expect(client.last(METHODS.toolReload).params).toEqual({
      session_id: 's1',
      wait_until: 'load',
      timeout_ms: 15_000,
      hard: true,
    });
  });

  it('omits hard when false', async () => {
    const client = new RecordingCaller().reply(METHODS.toolReload, {});
    await reload(client, { sessionId: 's1', hard: false });
    expect(client.last(METHODS.toolReload).params).not.toHaveProperty('hard');
  });
});
