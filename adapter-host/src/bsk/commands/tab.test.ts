import { describe, expect, it } from 'vitest';

import { METHODS } from '../methods.js';
import { tabBorrow, tabClose, tabCreate, tabList, tabReturn, tabSelect } from './tab.js';
import { RecordingCaller } from './test-support.js';

describe('tab commands', () => {
  it('tabList defaults to scope=all', async () => {
    const client = new RecordingCaller().reply(METHODS.toolTabList, { tabs: [] });
    await tabList(client, { sessionId: 's1' });
    expect(client.last(METHODS.toolTabList).params).toEqual({ session_id: 's1', scope: 'all' });
    await tabList(client, { sessionId: 's1', scope: 'agent' });
    expect(client.last(METHODS.toolTabList).params).toEqual({ session_id: 's1', scope: 'agent' });
  });

  it('tabCreate omits active by default and honours background tabs', async () => {
    const client = new RecordingCaller().reply(METHODS.toolTabCreate, {});
    await tabCreate(client, { sessionId: 's1', url: 'https://x', index: 2 });
    expect(client.last(METHODS.toolTabCreate).params).toEqual({
      session_id: 's1',
      url: 'https://x',
      index: 2,
    });
    await tabCreate(client, { sessionId: 's1', active: false });
    expect(client.last(METHODS.toolTabCreate).params).toEqual({
      session_id: 's1',
      active: false,
    });
  });

  it('close/select/return map tab ids', async () => {
    const client = new RecordingCaller()
      .reply(METHODS.toolTabClose, { tab_id: 1 })
      .reply(METHODS.toolTabSelect, { tab_id: 1, window_id: 2 })
      .reply(METHODS.toolTabReturn, { tab_id: 1 });
    await tabClose(client, { sessionId: 's1', tabId: 1 });
    await tabSelect(client, { sessionId: 's1', tabId: 1 });
    await tabReturn(client, { sessionId: 's1', tabId: 1 });
    for (const method of [METHODS.toolTabClose, METHODS.toolTabSelect, METHODS.toolTabReturn]) {
      expect(client.last(method).params).toEqual({ session_id: 's1', tab_id: 1 });
    }
  });

  it('tabBorrow budgets the confirmation wait', async () => {
    const client = new RecordingCaller().reply(METHODS.toolTabBorrow, {});
    await tabBorrow(client, { sessionId: 's1', tabId: 5, confirmationTimeoutMs: 1_000 });
    expect(client.last(METHODS.toolTabBorrow).params).toEqual({
      session_id: 's1',
      tab_id: 5,
      confirmation_timeout_ms: 1_000,
    });
    expect(client.last(METHODS.toolTabBorrow).options?.timeoutMs).toBe(23_000);
  });
});
