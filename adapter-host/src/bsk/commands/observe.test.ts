import { describe, expect, it } from 'vitest';

import { METHODS } from '../methods.js';
import { evaluate, evaluateValue } from './evaluate.js';
import { getHtml, observe, snapshot } from './observe.js';
import { RecordingCaller } from './test-support.js';

describe('snapshot / observe / getHtml', () => {
  it('snapshot forwards caps and tab', async () => {
    const client = new RecordingCaller().reply(METHODS.toolSnapshot, {});
    await snapshot(client, { sessionId: 's1', tabId: 4, maxDepth: 6, maxTokens: 900 });
    expect(client.last(METHODS.toolSnapshot).params).toEqual({
      session_id: 's1',
      tab_id: 4,
      max_depth: 6,
      max_tokens: 900,
    });
  });

  it('observe defaults to no probes and omits falsy flags', async () => {
    const client = new RecordingCaller().reply(METHODS.toolObserve, {});
    await observe(client, { sessionId: 's1' });
    expect(client.last(METHODS.toolObserve).params).toEqual({ session_id: 's1' });
  });

  it('observe forwards probe flags and cursors', async () => {
    const client = new RecordingCaller().reply(METHODS.toolObserve, {});
    await observe(client, { sessionId: 's1', probeHover: true, debugSurfaces: true, maxTokens: 10 });
    expect(client.last(METHODS.toolObserve).params).toEqual({
      session_id: 's1',
      max_tokens: 10,
      debug_surfaces: true,
      probe_hover: true,
    });
    await observe(client, { sessionId: 's1', cursor: 'page-2' });
    expect(client.last(METHODS.toolObserve).params).toEqual({
      cursor: 'page-2',
      session_id: 's1',
    });
  });

  it('observe rejects cursor combined with capture options', async () => {
    const client = new RecordingCaller();
    for (const extra of [{ maxDepth: 2 }, { debugSurfaces: true }, { probeHover: true }]) {
      await expect(
        observe(client, { sessionId: 's1', cursor: 'page-2', ...extra }),
      ).rejects.toThrow(/--cursor cannot be combined/);
    }
    // The Rust CLI allows a shrinking token budget on a continuation.
    client.reply(METHODS.toolObserve, {});
    await expect(
      observe(client, { sessionId: 's1', cursor: 'page-2', maxTokens: 100 }),
    ).resolves.toBeDefined();
  });

  it('getHtml maps ref and max bytes', async () => {
    const client = new RecordingCaller().reply(METHODS.toolGetHtml, {});
    await getHtml(client, { sessionId: 's1', ref: '@e3', maxBytes: 1024, tabId: 1 });
    expect(client.last(METHODS.toolGetHtml).params).toEqual({
      session_id: 's1',
      tab_id: 1,
      ref: '@e3',
      max_bytes: 1024,
    });
  });
});

describe('evaluate', () => {
  it('defaults await_promise and return_by_value to true', async () => {
    const client = new RecordingCaller().reply(METHODS.toolEvaluate, { ok: true, value: 2 });
    await evaluate(client, { sessionId: 's1', expression: '1+1' });
    expect(client.last(METHODS.toolEvaluate).params).toEqual({
      session_id: 's1',
      expression: '1+1',
      await_promise: true,
      return_by_value: true,
      timeout_ms: 30_000,
    });
  });

  it('honours explicit false values', async () => {
    const client = new RecordingCaller().reply(METHODS.toolEvaluate, { ok: true });
    await evaluate(client, {
      sessionId: 's1',
      expression: 'x',
      awaitPromise: false,
      returnByValue: false,
      timeoutMs: 1_000,
    });
    expect(client.last(METHODS.toolEvaluate).params).toMatchObject({
      await_promise: false,
      return_by_value: false,
      timeout_ms: 1_000,
    });
  });

  it('evaluateValue unwraps value and throws on ok:false', async () => {
    const okClient = new RecordingCaller().reply(METHODS.toolEvaluate, {
      ok: true,
      tab_id: 1,
      value: { title: 'x' },
    });
    await expect(evaluateValue(okClient, { sessionId: 's1', expression: 'x' })).resolves.toEqual({
      title: 'x',
    });

    const throwClient = new RecordingCaller().reply(METHODS.toolEvaluate, {
      ok: false,
      tab_id: 1,
      error: { text: 'Uncaught Error: boom' },
    });
    await expect(
      evaluateValue(throwClient, { sessionId: 's1', expression: 'x' }),
    ).rejects.toThrow('Uncaught Error: boom');
  });
});
