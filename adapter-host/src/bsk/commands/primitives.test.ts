import { describe, expect, it } from 'vitest';

import { EXTENSION_METHODS } from '../methods.js';
import { cdp, cookies, frames } from './primitives.js';
import { RecordingCaller } from './test-support.js';

describe('cookies', () => {
  it('forwards the cookie scope', async () => {
    const client = new RecordingCaller().reply(EXTENSION_METHODS.toolCookies, {});
    await cookies(client, { sessionId: 's1', tabId: 2, domain: 'example.test' });
    expect(client.last(EXTENSION_METHODS.toolCookies).params).toEqual({
      session_id: 's1',
      tab_id: 2,
      domain: 'example.test',
    });
  });

  it('omits an unset scope so the extension can fall back to the tab origin', async () => {
    const client = new RecordingCaller().reply(EXTENSION_METHODS.toolCookies, {});
    await cookies(client, { sessionId: 's1' });
    expect(client.last(EXTENSION_METHODS.toolCookies).params).toEqual({ session_id: 's1' });
  });
});

describe('frames', () => {
  it('forwards the session and optional tab', async () => {
    const client = new RecordingCaller().reply(EXTENSION_METHODS.toolFrames, {});
    await frames(client, { sessionId: 's1' });
    expect(client.last(EXTENSION_METHODS.toolFrames).params).toEqual({ session_id: 's1' });
    await frames(client, { sessionId: 's1', tabId: 4 });
    expect(client.last(EXTENSION_METHODS.toolFrames).params).toEqual({ session_id: 's1', tab_id: 4 });
  });
});

describe('cdp', () => {
  it('forwards the method and opaque params', async () => {
    const client = new RecordingCaller().reply(EXTENSION_METHODS.toolCdp, {});
    await cdp(client, {
      sessionId: 's1',
      method: 'Page.getLayoutMetrics',
      params: { nested: { deep: true } },
    });
    expect(client.last(EXTENSION_METHODS.toolCdp).params).toEqual({
      session_id: 's1',
      method: 'Page.getLayoutMetrics',
      params: { nested: { deep: true } },
    });
  });

  it('omits params when the caller gave none', async () => {
    const client = new RecordingCaller().reply(EXTENSION_METHODS.toolCdp, {});
    await cdp(client, { sessionId: 's1', method: 'DOM.getDocument' });
    expect(client.last(EXTENSION_METHODS.toolCdp).params).toEqual({
      session_id: 's1',
      method: 'DOM.getDocument',
    });
  });
});
