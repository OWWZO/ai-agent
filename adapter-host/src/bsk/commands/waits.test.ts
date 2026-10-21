import { describe, expect, it } from 'vitest';

import { METHODS } from '../methods.js';
import { waitForNavigation, waitMs } from './waits.js';
import { RecordingCaller } from './test-support.js';

describe('waitForNavigation', () => {
  it('defaults to load and 30s', async () => {
    const client = new RecordingCaller().reply(METHODS.toolWaitForNavigation, {});
    await waitForNavigation(client, { sessionId: 's1', tabId: 4 });
    expect(client.last(METHODS.toolWaitForNavigation).params).toEqual({
      session_id: 's1',
      tab_id: 4,
      wait_until: 'load',
      timeout_ms: 30_000,
    });
  });

  it('returns the reached phase verbatim, including timeout', async () => {
    const client = new RecordingCaller().reply(METHODS.toolWaitForNavigation, {
      tab_id: 1,
      reached: 'timeout',
      error_text: 'timed out',
    });
    const result = await waitForNavigation(client, { sessionId: 's1', waitUntil: 'commit' });
    expect(result.reached).toBe('timeout');
    expect(client.last(METHODS.toolWaitForNavigation).params).toMatchObject({
      wait_until: 'commit',
    });
  });
});

describe('waitMs', () => {
  it('maps duration_ms and budgets the transport around it', async () => {
    const client = new RecordingCaller().reply(METHODS.toolWaitMs, { waited_ms: 250 });
    const result = await waitMs(client, { durationMs: 250 });
    expect(result.waited_ms).toBe(250);
    expect(client.last(METHODS.toolWaitMs).params).toEqual({ duration_ms: 250 });
    expect(client.last(METHODS.toolWaitMs).options?.timeoutMs).toBe(15_250);
  });

  it('allows a zero wait', async () => {
    const client = new RecordingCaller().reply(METHODS.toolWaitMs, { waited_ms: 0 });
    await waitMs(client, { durationMs: 0 });
    expect(client.last(METHODS.toolWaitMs).params).toEqual({ duration_ms: 0 });
  });
});
