import { describe, expect, it } from 'vitest';

import { BskUsageError } from '../errors.js';
import { METHODS } from '../methods.js';
import {
  sessionList,
  sessionRequest,
  sessionStart,
  sessionStartDaemon,
  sessionStop,
  sessionStopAll,
  sessionStopDaemon,
} from './session.js';
import { RecordingCaller } from './test-support.js';

describe('sessionStart', () => {
  it('uses tool.session_start with the corrected param shape', async () => {
    const client = new RecordingCaller().reply(METHODS.toolSessionStart, {
      agent_window_id: 12,
      interaction: { borrow_confirmation: 'always', request_help: 'enabled' },
    });
    const result = await sessionStart(client, {
      sessionId: 'sess-1',
      browserInstanceId: 'browser-1',
      width: 1280,
      height: 800,
      focused: false,
    });
    expect(result.agent_window_id).toBe(12);
    expect(client.last(METHODS.toolSessionStart).params).toEqual({
      session_id: 'sess-1',
      browser_instance_id: 'browser-1',
      width: 1280,
      height: 800,
      focused: false,
    });
  });

  it('omits optional fields and never sends legacy unattended', async () => {
    const client = new RecordingCaller().reply(METHODS.toolSessionStart, {});
    await sessionStart(client, { sessionId: 'sess-1' });
    expect(client.last(METHODS.toolSessionStart).params).toEqual({ session_id: 'sess-1' });
  });

  it('validates dimensions and pairing', async () => {
    const client = new RecordingCaller();
    await expect(sessionStart(client, { sessionId: 's1', width: 800 })).rejects.toThrow(
      /given together/,
    );
    await expect(sessionStart(client, { sessionId: 's1', width: 50, height: 800 })).rejects.toThrow(
      /out of range/,
    );
    await expect(
      sessionStart(client, { sessionId: 's1', width: 800, height: 9000 }),
    ).rejects.toThrow(/out of range/);
    expect(client.calls).toHaveLength(0);
  });
});

describe('session stop/list', () => {
  it('sessionStop targets tool.session_stop', async () => {
    const client = new RecordingCaller().reply(METHODS.toolSessionStop, {
      returned_tab_ids: [7],
    });
    const result = await sessionStop(client, { sessionId: 's1' });
    expect(result.returned_tab_ids).toEqual([7]);
    expect(client.last(METHODS.toolSessionStop).params).toEqual({ session_id: 's1' });
  });

  it('daemon-level stop and stop_all keep the session.* methods', async () => {
    const client = new RecordingCaller()
      .reply(METHODS.sessionStop, { stopped: ['s1'] })
      .reply(METHODS.sessionStopAll, { stopped: ['s1', 's2'] });
    await sessionStopDaemon(client, { sessionId: 's1' });
    await sessionStopAll(client);
    expect(client.last(METHODS.sessionStop).params).toEqual({ session_id: 's1', all: false });
    expect(client.last(METHODS.sessionStopAll).params).toBeUndefined();
  });

  it('sessionList sends no params', async () => {
    const client = new RecordingCaller().reply(METHODS.sessionList, { sessions: [] });
    await sessionList(client);
    expect(client.last(METHODS.sessionList).params).toBeUndefined();
  });

  it('sessionStartDaemon uses session.start for CLI parity', async () => {
    const client = new RecordingCaller().reply(METHODS.sessionStart, {
      session_id: 's1',
      browser_instance_id: 'b1',
    });
    await sessionStartDaemon(client, { sessionId: 's1', width: 800, height: 600 });
    expect(client.last(METHODS.sessionStart).params).toEqual({ width: 800, height: 600 });
  });

  it('sessionRequest forwards the action', async () => {
    const client = new RecordingCaller().reply(METHODS.sessionRequest, { status: 'pending' });
    await sessionRequest(client, { requestId: 'req-1', action: 'claim' });
    expect(client.last(METHODS.sessionRequest).params).toEqual({
      request_id: 'req-1',
      action: 'claim',
    });
  });

  it('rejects a blank session id', async () => {
    await expect(sessionStop(new RecordingCaller(), { sessionId: '' })).rejects.toThrow(
      BskUsageError,
    );
  });
});
