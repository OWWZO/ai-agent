import { describe, expect, it } from 'vitest';

import { BskUsageError } from '../errors.js';
import { METHODS } from '../methods.js';
import { scrollTo, wheel } from './scroll.js';
import { RecordingCaller } from './test-support.js';

describe('scrollTo', () => {
  it('maps target and timeout', async () => {
    const client = new RecordingCaller().reply(METHODS.toolScrollTo, {});
    await scrollTo(client, { sessionId: 's1', target: '#target', tabId: 2 });
    expect(client.last(METHODS.toolScrollTo).params).toEqual({
      session_id: 's1',
      selector: '#target',
      tab_id: 2,
      timeout_ms: 30_000,
    });
  });

  it('requires a target', async () => {
    await expect(scrollTo(new RecordingCaller(), { sessionId: 's1' })).rejects.toThrow(
      BskUsageError,
    );
  });
});

describe('wheel', () => {
  it('dispatches at the viewport centre when no target is given', async () => {
    const client = new RecordingCaller().reply(METHODS.toolWheel, {});
    await wheel(client, { sessionId: 's1', deltaY: 600 });
    expect(client.last(METHODS.toolWheel).params).toEqual({
      session_id: 's1',
      delta_x: 0,
      delta_y: 600,
      timeout_ms: 30_000,
    });
  });

  it('maps ref/selector and modifiers', async () => {
    const client = new RecordingCaller().reply(METHODS.toolWheel, {});
    await wheel(client, {
      sessionId: 's1',
      target: '@e3',
      deltaX: -10,
      deltaY: 20,
      modifiers: 'shift',
      tabId: 5,
    });
    expect(client.last(METHODS.toolWheel).params).toEqual({
      session_id: 's1',
      ref: '@e3',
      tab_id: 5,
      delta_x: -10,
      delta_y: 20,
      modifiers: ['shift'],
      timeout_ms: 30_000,
    });
  });

  it('rejects zero and non-finite deltas before calling', async () => {
    const client = new RecordingCaller();
    await expect(wheel(client, { sessionId: 's1' })).rejects.toThrow(/at least one/);
    await expect(wheel(client, { sessionId: 's1', deltaY: Number.NaN })).rejects.toThrow(/finite/);
    await expect(wheel(client, { sessionId: 's1', deltaY: 1, ref: '' })).rejects.toThrow(
      /must not be empty/,
    );
    expect(client.calls).toHaveLength(0);
  });
});
