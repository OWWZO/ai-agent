import { beforeEach, describe, expect, it, vi } from 'vitest';

const { MockWebSocket } = vi.hoisted(() => {
  class MockWebSocket {
    static CONNECTING = 0;
    static OPEN = 1;
    static CLOSING = 2;
    static CLOSED = 3;
    static lastInstance: MockWebSocket | undefined;
    static instances: MockWebSocket[] = [];
    static responder: ((msg: { id?: number; method?: string; params?: Record<string, unknown>; sessionId?: string }) => unknown) | undefined;
    static closeResponds = true;
    readyState = 1;
    terminateCalls = 0;
    sent: string[] = [];
    private handlers = new Map<string, Array<(...args: unknown[]) => void>>();

    constructor(_url: string) {
      MockWebSocket.lastInstance = this;
      MockWebSocket.instances.push(this);
      queueMicrotask(() => this.emit('open'));
    }

    on(event: string, handler: (...args: unknown[]) => void): void {
      const handlers = this.handlers.get(event) ?? [];
      handlers.push(handler);
      this.handlers.set(event, handlers);
    }

    once(event: string, handler: (...args: unknown[]) => void): void {
      const onceHandler = (...args: unknown[]) => {
        this.off(event, onceHandler);
        handler(...args);
      };
      this.on(event, onceHandler);
    }

    off(event: string, handler: (...args: unknown[]) => void): void {
      this.handlers.set(event, (this.handlers.get(event) ?? []).filter((candidate) => candidate !== handler));
    }

    send(message: string): void {
      this.sent.push(message);
      const msg = JSON.parse(message) as { id?: number; method?: string; params?: Record<string, unknown>; sessionId?: string };
      queueMicrotask(() => {
        if (msg.id == null) return;
        const raw = MockWebSocket.responder?.(msg);
        if (raw && typeof raw === 'object' && 'error' in (raw as Record<string, unknown>)) {
          this.emit('message', Buffer.from(JSON.stringify({ id: msg.id, error: (raw as { error: unknown }).error })));
          return;
        }
        this.emit('message', Buffer.from(JSON.stringify({ id: msg.id, result: raw ?? {} })));
      });
    }

    close(): void {
      this.readyState = MockWebSocket.CLOSING;
      if (!MockWebSocket.closeResponds) return;
      queueMicrotask(() => {
        this.readyState = MockWebSocket.CLOSED;
        this.emit('close', 1000, Buffer.alloc(0));
      });
    }

    terminate(): void {
      this.terminateCalls += 1;
      this.readyState = MockWebSocket.CLOSED;
      queueMicrotask(() => this.emit('close', 1006, Buffer.alloc(0)));
    }

    emit(event: string, ...args: unknown[]): void {
      for (const handler of this.handlers.get(event) ?? []) {
        handler(...args);
      }
    }
  }

  return { MockWebSocket };
});

vi.mock('ws', () => ({
  WebSocket: MockWebSocket,
}));

import { CDPBridge, CDP_REQUEST_BODY_CAPTURE_LIMIT } from './cdp.js';

describe('CDPBridge cookies', () => {
  beforeEach(() => {
    vi.unstubAllEnvs();
    MockWebSocket.responder = undefined;
    MockWebSocket.closeResponds = true;
    MockWebSocket.instances = [];
  });

  it('filters cookies by actual domain match instead of substring match', async () => {
    vi.stubEnv('OPENCLI_CDP_ENDPOINT', 'ws://127.0.0.1:9222/devtools/page/1');

    const bridge = new CDPBridge();
    vi.spyOn(bridge, 'send').mockResolvedValue({
      cookies: [
        { name: 'good', value: '1', domain: '.example.com' },
        { name: 'exact', value: '2', domain: 'example.com' },
        { name: 'bad', value: '3', domain: 'notexample.com' },
      ],
    });

    const page = await bridge.connect();
    const cookies = await page.getCookies({ domain: 'example.com' });

    expect(cookies).toEqual([
      { name: 'good', value: '1', domain: '.example.com' },
      { name: 'exact', value: '2', domain: 'example.com' },
    ]);
  });

  it('exposes native input helpers on direct CDP pages', async () => {
    vi.stubEnv('OPENCLI_CDP_ENDPOINT', 'ws://127.0.0.1:9222/devtools/page/1');

    const bridge = new CDPBridge();
    const send = vi.spyOn(bridge, 'send').mockResolvedValue({});

    const page = await bridge.connect();
    send.mockClear();

    expect(page.nativeType).toBeTypeOf('function');
    expect(page.nativeKeyPress).toBeTypeOf('function');
    expect(page.nativeClick).toBeTypeOf('function');
    expect(page.handleJavaScriptDialog).toBeTypeOf('function');
    expect(page.cdp).toBeTypeOf('function');

    await page.nativeType!('hello');
    await page.nativeKeyPress!('a', ['Ctrl']);
    await page.nativeClick!(10, 20);
    await page.handleJavaScriptDialog!(true, 'ok');
    await page.cdp!('Page.getLayoutMetrics', {});

    expect(send.mock.calls).toEqual([
      ['Input.insertText', { text: 'hello' }],
      ['Input.dispatchKeyEvent', { type: 'keyDown', key: 'a', modifiers: 2 }],
      ['Input.dispatchKeyEvent', { type: 'keyUp', key: 'a', modifiers: 2 }],
      ['Input.dispatchMouseEvent', { type: 'mouseMoved', x: 10, y: 20 }],
      ['Input.dispatchMouseEvent', { type: 'mousePressed', x: 10, y: 20, button: 'left', clickCount: 1 }],
      ['Input.dispatchMouseEvent', { type: 'mouseReleased', x: 10, y: 20, button: 'left', clickCount: 1 }],
      ['Page.handleJavaScriptDialog', { accept: true, promptText: 'ok' }],
      ['Page.getLayoutMetrics', {}],
    ]);
  });

  it('captures request headers and bounded post data on direct CDP pages', async () => {
    vi.stubEnv('OPENCLI_CDP_ENDPOINT', 'ws://127.0.0.1:9222/devtools/page/1');

    const bridge = new CDPBridge();
    const fullBody = 'x'.repeat(CDP_REQUEST_BODY_CAPTURE_LIMIT + 5);
    vi.spyOn(bridge, 'send').mockImplementation(async (method: string) => {
      if (method === 'Network.getRequestPostData') return { postData: fullBody };
      return {};
    });

    const page = await bridge.connect();
    await page.startNetworkCapture?.();
    MockWebSocket.lastInstance?.emit('message', Buffer.from(JSON.stringify({
      method: 'Network.requestWillBeSent',
      params: {
        requestId: 'request-1',
        request: {
          method: 'POST',
          url: 'https://example.test/rsc-action/actions/pagination',
          headers: { Authorization: 'Bearer secret', 'Content-Type': 'application/json' },
          hasPostData: true,
        },
      },
    })));

    const entries = await page.readNetworkCapture?.() as Array<Record<string, unknown>>;
    expect(entries).toHaveLength(1);
    expect(entries[0]).toMatchObject({
      method: 'POST',
      requestHeaders: { Authorization: 'Bearer secret', 'Content-Type': 'application/json' },
      requestBodyKind: 'string',
      requestBodyFullSize: fullBody.length,
      requestBodyTruncated: true,
    });
    expect(String(entries[0].requestBodyPreview)).toHaveLength(CDP_REQUEST_BODY_CAPTURE_LIMIT);
  });
});

describe('CDPBridge browser-level session routing', () => {
  beforeEach(() => {
    vi.unstubAllEnvs();
    MockWebSocket.responder = undefined;
    MockWebSocket.instances = [];
  });

  it('attaches to the preferred page target and routes commands with sessionId', async () => {
    vi.stubEnv('OPENCLI_CDP_ENDPOINT', 'wss://proxy.example.onkernel.com:8443/browser/cdp?jwt=secret-token');
    MockWebSocket.responder = (msg) => {
      if (msg.method === 'Target.getTargets') {
        return {
          targetInfos: [
            { targetId: 'iframe-1', type: 'iframe', url: 'https://ads.example', title: '' },
            { targetId: 'page-blank', type: 'page', url: 'about:blank', title: '' },
            { targetId: 'page-main', type: 'page', url: 'https://example.com', title: 'Example' },
          ],
        };
      }
      if (msg.method === 'Target.attachToTarget') {
        expect(msg.params?.targetId).toBe('page-main');
        expect(msg.params?.flatten).toBe(true);
        expect(msg.sessionId).toBeUndefined();
        return { sessionId: 'sess-main' };
      }
      return {};
    };

    const bridge = new CDPBridge();
    const page = await bridge.connect();
    await page.cdp!('Runtime.evaluate', { expression: '1' });
    await bridge.close();

    const sent = (MockWebSocket.lastInstance?.sent ?? []).map((raw) => JSON.parse(raw) as Record<string, unknown>);
    expect(sent.some((msg) => msg.method === 'Target.attachToTarget')).toBe(true);
    const pageEnable = sent.find((msg) => msg.method === 'Page.enable');
    expect(pageEnable?.sessionId).toBe('sess-main');
    const evaluate = sent.find((msg) => msg.method === 'Runtime.evaluate');
    expect(evaluate?.sessionId).toBe('sess-main');
    expect(sent.some((msg) => msg.method === 'Browser.close' || msg.method === 'Target.closeTarget')).toBe(false);
  });

  it('creates a new isolated target for adapter connections instead of attaching to an existing page', async () => {
    vi.stubEnv('OPENCLI_CDP_ENDPOINT', 'wss://proxy.example.onkernel.com:8443/browser/cdp?jwt=secret-token');
    MockWebSocket.responder = (msg) => {
      if (msg.method === 'Target.getTargets') {
        return {
          targetInfos: [
            { targetId: 'page-existing', type: 'page', url: 'https://example.com', title: 'Example' },
          ],
        };
      }
      if (msg.method === 'Target.createTarget') return { targetId: 'page-isolated' };
      if (msg.method === 'Target.attachToTarget') return { sessionId: 'sess-isolated' };
      return {};
    };

    const bridge = new CDPBridge();
    const page = await bridge.connect({ surface: 'adapter', siteSession: 'persistent' });

    expect(page.getActivePage?.()).toBe('page-isolated');
    await bridge.close();

    const sent = (MockWebSocket.lastInstance?.sent ?? []).map((raw) => JSON.parse(raw) as Record<string, unknown>);
    expect(sent.some((msg) => msg.method === 'Target.createTarget')).toBe(true);
    expect(sent.filter((msg) => msg.method === 'Target.attachToTarget').map((msg) => (msg.params as Record<string, unknown>)?.targetId))
      .toEqual(['page-isolated']);
    expect(sent.some((msg) => msg.method === 'Target.attachToTarget' && (msg.params as Record<string, unknown>)?.targetId === 'page-existing')).toBe(false);
    expect(sent.filter((msg) => msg.method === 'Target.closeTarget').map((msg) => (msg.params as Record<string, unknown>)?.targetId))
      .toEqual(['page-isolated']);
  });

  it('closes an isolated target through closeWindow and does not close it twice in bridge.close', async () => {
    vi.stubEnv('OPENCLI_CDP_ENDPOINT', 'wss://proxy.example.onkernel.com:8443/browser/cdp?jwt=secret-token');
    MockWebSocket.responder = (msg) => {
      if (msg.method === 'Target.getTargets') return { targetInfos: [] };
      if (msg.method === 'Target.createTarget') return { targetId: 'page-isolated' };
      if (msg.method === 'Target.attachToTarget') return { sessionId: 'sess-isolated' };
      return {};
    };

    const bridge = new CDPBridge();
    const page = await bridge.connect({ surface: 'adapter', siteSession: 'ephemeral' });
    await page.closeWindow?.();
    await bridge.close();

    const sent = (MockWebSocket.lastInstance?.sent ?? []).map((raw) => JSON.parse(raw) as Record<string, unknown>);
    expect(sent.filter((msg) => msg.method === 'Target.closeTarget')).toHaveLength(1);
    expect(sent.some((msg) => msg.method === 'Browser.close')).toBe(false);
    expect(page.getActivePage?.()).toBeUndefined();
  });

  it('creates different isolated targets for different adapter bridge instances', async () => {
    vi.stubEnv('OPENCLI_CDP_ENDPOINT', 'wss://proxy.example.onkernel.com:8443/browser/cdp?jwt=secret-token');
    let targetNumber = 0;
    MockWebSocket.responder = (msg) => {
      if (msg.method === 'Target.getTargets') return { targetInfos: [{ targetId: 'page-existing', type: 'page' }] };
      if (msg.method === 'Target.createTarget') return { targetId: `page-isolated-${++targetNumber}` };
      if (msg.method === 'Target.attachToTarget') return { sessionId: `sess-${msg.params?.targetId}` };
      return {};
    };

    const first = new CDPBridge();
    await first.connect({ surface: 'adapter' });
    await first.close();
    const second = new CDPBridge();
    await second.connect({ surface: 'adapter' });
    await second.close();

    const creates = MockWebSocket.instances.flatMap((instance) => instance.sent)
      .map((raw) => JSON.parse(raw) as Record<string, unknown>)
      .filter((msg) => msg.method === 'Target.createTarget');
    const closes = MockWebSocket.instances.flatMap((instance) => instance.sent)
      .map((raw) => JSON.parse(raw) as Record<string, unknown>)
      .filter((msg) => msg.method === 'Target.closeTarget');
    expect(creates).toHaveLength(2);
    expect(closes.map((msg) => (msg.params as Record<string, unknown>)?.targetId)).toEqual(['page-isolated-1', 'page-isolated-2']);
  });

  it('fails explicitly when an adapter endpoint cannot provide browser target APIs', async () => {
    vi.stubEnv('OPENCLI_CDP_ENDPOINT', 'wss://proxy.example.onkernel.com:8443/browser/cdp?jwt=secret-token');
    MockWebSocket.responder = (msg) => {
      if (msg.method === 'Target.getTargets') return { error: { message: "'Target.getTargets' wasn't found" } };
      return {};
    };

    const bridge = new CDPBridge();
    await expect(bridge.connect({ surface: 'adapter' })).rejects.toThrow(
      'Direct CDP endpoint cannot create an isolated browser tab.',
    );

    const sent = (MockWebSocket.lastInstance?.sent ?? []).map((raw) => JSON.parse(raw) as Record<string, unknown>);
    expect(sent.some((msg) => msg.method === 'Target.createTarget')).toBe(false);
    expect(sent.some((msg) => msg.method === 'Target.attachToTarget')).toBe(false);
  });

  it('falls back to page-level commands when Target.getTargets is unavailable', async () => {
    vi.stubEnv('OPENCLI_CDP_ENDPOINT', 'ws://127.0.0.1:9222/devtools/page/1');
    MockWebSocket.responder = (msg) => {
      if (msg.method === 'Target.getTargets') {
        return { error: { message: "'Target.getTargets' wasn't found" } };
      }
      return {};
    };

    const bridge = new CDPBridge();
    await bridge.connect();
    await bridge.close();

    const sent = (MockWebSocket.lastInstance?.sent ?? []).map((raw) => JSON.parse(raw) as Record<string, unknown>);
    const pageEnable = sent.find((msg) => msg.method === 'Page.enable');
    expect(pageEnable).toBeTruthy();
    expect(pageEnable?.sessionId).toBeUndefined();
    expect(sent.some((msg) => msg.method === 'Target.attachToTarget')).toBe(false);
  });

  it('waits for the CDP WebSocket to close before returning', async () => {
    vi.stubEnv('OPENCLI_CDP_ENDPOINT', 'ws://127.0.0.1:9222/devtools/page/1');

    const bridge = new CDPBridge();
    await bridge.connect();
    await bridge.close();

    expect(MockWebSocket.lastInstance?.readyState).toBe(MockWebSocket.CLOSED);
    expect(MockWebSocket.lastInstance?.terminateCalls).toBe(0);
  });

  it('force-terminates a CDP WebSocket that does not finish its close handshake', async () => {
    vi.stubEnv('OPENCLI_CDP_ENDPOINT', 'ws://127.0.0.1:9222/devtools/page/1');
    MockWebSocket.closeResponds = false;

    const bridge = new CDPBridge();
    await bridge.connect();
    await bridge.close();

    expect(MockWebSocket.lastInstance?.readyState).toBe(MockWebSocket.CLOSED);
    expect(MockWebSocket.lastInstance?.terminateCalls).toBe(1);
  });

  it('ignores CDP events from other flattened sessions', async () => {
    vi.stubEnv('OPENCLI_CDP_ENDPOINT', 'wss://proxy.example.onkernel.com:8443/browser/cdp?jwt=secret-token');
    MockWebSocket.responder = (msg) => {
      if (msg.method === 'Target.getTargets') {
        return {
          targetInfos: [{ targetId: 'page-main', type: 'page', url: 'https://example.com', title: 'Example' }],
        };
      }
      if (msg.method === 'Target.attachToTarget') {
        return { sessionId: 'sess-main' };
      }
      return {};
    };

    const bridge = new CDPBridge();
    await bridge.connect();
    const loadPromise = bridge.waitForEvent('Page.loadEventFired', 200);
    MockWebSocket.lastInstance?.emit('message', Buffer.from(JSON.stringify({
      method: 'Page.loadEventFired',
      sessionId: 'sess-other',
      params: { ignored: true },
    })));
    MockWebSocket.lastInstance?.emit('message', Buffer.from(JSON.stringify({
      method: 'Page.loadEventFired',
      sessionId: 'sess-main',
      params: { ok: true },
    })));
    await expect(loadPromise).resolves.toEqual({ ok: true });
    await bridge.close();
  });

  it('creates, selects, lists, and closes browser-level page targets', async () => {
    vi.stubEnv('OPENCLI_CDP_ENDPOINT', 'wss://proxy.example.onkernel.com:8443/browser/cdp?jwt=secret-token');
    MockWebSocket.responder = (msg) => {
      if (msg.method === 'Target.getTargets') {
        return {
          targetInfos: [
            { targetId: 'page-main', type: 'page', url: 'https://example.com', title: 'Example' },
            { targetId: 'iframe-1', type: 'iframe', url: 'https://ads.example', title: '' },
          ],
        };
      }
      if (msg.method === 'Target.createTarget') return { targetId: 'page-new' };
      if (msg.method === 'Target.attachToTarget') return { sessionId: `sess-${msg.params?.targetId}` };
      return {};
    };

    const bridge = new CDPBridge();
    const page = await bridge.connect({
      session: 'work',
      preferredContextId: 'profile-1',
    });
    expect(page.getActivePage?.()).toBe('page-main');
    expect((page as unknown as { session?: string }).session).toBe('work');
    expect((page as unknown as { preferredContextId?: string }).preferredContextId).toBe('profile-1');
    expect(await page.tabs()).toEqual([
      { page: 'page-main', url: 'https://example.com', title: 'Example' },
    ]);

    const created = await page.newTab?.();
    expect(created).toBe('page-new');
    expect(page.getActivePage?.()).toBe('page-new');

    await page.selectTab('page-main');
    expect(page.getActivePage?.()).toBe('page-main');
    await page.closeTab?.('page-new');

    const sent = (MockWebSocket.lastInstance?.sent ?? []).map((raw) => JSON.parse(raw) as Record<string, unknown>);
    expect(sent.filter((msg) => msg.method === 'Target.attachToTarget').map((msg) => (msg.params as Record<string, unknown>)?.targetId))
      .toEqual(['page-main', 'page-new', 'page-main']);
    expect(sent.filter((msg) => msg.method === 'Target.detachFromTarget')).toHaveLength(2);
    expect(sent.some((msg) => msg.method === 'Target.closeTarget' && (msg.params as Record<string, unknown>)?.targetId === 'page-new')).toBe(true);
    await bridge.close();
  });
});
