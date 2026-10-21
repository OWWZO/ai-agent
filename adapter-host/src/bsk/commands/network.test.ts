import { describe, expect, it } from 'vitest';

import { METHODS } from '../methods.js';
import { console as readConsole, network } from './network.js';
import { RecordingCaller } from './test-support.js';

describe('network', () => {
  it('maps cursor, limit, and text cap', async () => {
    const client = new RecordingCaller().reply(METHODS.toolNetwork, {});
    await network(client, { sessionId: 's1', tabId: 2, since: 10, limit: 5, maxTextChars: 100 });
    expect(client.last(METHODS.toolNetwork).params).toEqual({
      session_id: 's1',
      tab_id: 2,
      since: 10,
      limit: 5,
      max_text_chars: 100,
    });
  });

  it('omits unset filters', async () => {
    const client = new RecordingCaller().reply(METHODS.toolNetwork, {});
    await network(client, { sessionId: 's1' });
    expect(client.last(METHODS.toolNetwork).params).toEqual({ session_id: 's1' });
  });
});

describe('console', () => {
  it('only forwards include_stack when requested', async () => {
    const client = new RecordingCaller().reply(METHODS.toolConsole, {});
    await readConsole(client, { sessionId: 's1', since: 3 });
    expect(client.last(METHODS.toolConsole).params).toEqual({ session_id: 's1', since: 3 });
    await readConsole(client, { sessionId: 's1', includeStack: true });
    expect(client.last(METHODS.toolConsole).params).toMatchObject({ include_stack: true });
  });
});
