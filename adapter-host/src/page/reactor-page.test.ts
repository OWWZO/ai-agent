/**
 * ReactorPage — bsk mapping tests (spec 021, Phase 4).
 *
 * `BskSessionHandle` is a recording stub so these tests assert on the exact
 * wire method + params ReactorPage produces, without a daemon or extension.
 */

import { describe, expect, it } from 'vitest';
import { NotImplementedError, ReactorPage, type BskSessionHandle } from './reactor-page.js';

interface RecordedCall {
  method: string;
  params: unknown;
}

/** Recording `BskSessionHandle`: replies come from a per-method fixture map. */
function makeHandle(replies: Record<string, unknown> = {}): {
  handle: BskSessionHandle;
  calls: RecordedCall[];
} {
  const calls: RecordedCall[] = [];
  const handle: BskSessionHandle = {
    sessionId: 'test-session',
    call: async <T>(method: string, params: unknown): Promise<T> => {
      calls.push({ method, params });
      if (Object.prototype.hasOwnProperty.call(replies, method)) return replies[method] as T;
      throw new Error(`unexpected bsk call: ${method}`);
    },
  };
  return { handle, calls };
}

function paramsOf(calls: RecordedCall[]): Record<string, unknown> {
  return calls[0].params as Record<string, unknown>;
}

describe('ReactorPage core method mapping', () => {
  it('goto issues tool.navigate with the session and url', async () => {
    const { handle, calls } = makeHandle({
      'tool.navigate': { tab_id: 1, url: 'https://example.com', reached: 'load' },
    });
    await new ReactorPage(handle).goto('https://example.com');

    expect(calls).toHaveLength(1);
    expect(calls[0].method).toBe('tool.navigate');
    expect(paramsOf(calls)).toMatchObject({ session_id: 'test-session', url: 'https://example.com' });
  });

  it('evaluate issues tool.evaluate and unwraps result.value', async () => {
    const { handle, calls } = makeHandle({
      'tool.evaluate': { ok: true, tab_id: 1, value: { a: 1 } },
    });
    const value = await new ReactorPage(handle).evaluate<{ a: number }>('({ a: 1 })');

    expect(calls[0].method).toBe('tool.evaluate');
    expect(paramsOf(calls)).toMatchObject({ session_id: 'test-session', expression: '({ a: 1 })' });
    expect(value).toEqual({ a: 1 });
  });

  it('evaluate throws with error.text when ok is false', async () => {
    const { handle } = makeHandle({
      'tool.evaluate': { ok: false, tab_id: 1, error: { text: 'boom' } },
    });
    await expect(new ReactorPage(handle).evaluate('throw new Error("x")')).rejects.toThrow(/boom/);
  });

  it('snapshot issues tool.snapshot without a second call', async () => {
    const { handle, calls } = makeHandle({
      'tool.snapshot': { text: 'snapshot', ref_count: 3, tab_id: 1, truncated: false },
    });
    const result = await new ReactorPage(handle).snapshot();

    expect(calls).toHaveLength(1);
    expect(calls[0].method).toBe('tool.snapshot');
    expect(result).toMatchObject({ text: 'snapshot', ref_count: 3 });
  });

  it('click issues tool.click and synthesizes the resolver envelope', async () => {
    const { handle, calls } = makeHandle({
      'tool.click': { tab_id: 1, used_ref: '@e1', x: 10, y: 20 },
    });
    const result = await new ReactorPage(handle).click('@e1');

    expect(calls[0].method).toBe('tool.click');
    expect(paramsOf(calls)).toMatchObject({ session_id: 'test-session', ref: '@e1' });
    // bsk reports only the resolved target; IPage promises resolution metadata.
    expect(result.matches_n).toBe(1);
    expect(result.match_level).toBe('exact');
    expect(result.click_method).toBe('cdp');
  });

  it('fillText issues tool.fill and reports verified', async () => {
    const { handle, calls } = makeHandle({
      'tool.fill': { tab_id: 1, used_ref: '@e2', value_length: 5 },
    });
    const result = await new ReactorPage(handle).fillText('@e2', 'hello');

    expect(calls[0].method).toBe('tool.fill');
    expect(paramsOf(calls)).toMatchObject({ session_id: 'test-session', ref: '@e2', value: 'hello' });
    // clis/midjourney/generate.js gates on `filled && verified`.
    expect(result.filled).toBe(true);
    expect(result.verified).toBe(true);
    expect(result.expected).toBe('hello');
    expect(result.actual).toBe('hello');
    expect(result.length).toBe(5);
    expect(result.matches_n).toBe(1);
    expect(result.match_level).toBe('exact');
  });

  it('typeText appends: never sends a clear flag to tool.fill', async () => {
    const { handle, calls } = makeHandle({
      'tool.fill': { tab_id: 1, used_ref: '@e2', value_length: 3 },
    });
    const result = await new ReactorPage(handle).typeText('@e2', 'abc');

    expect(calls[0].method).toBe('tool.fill');
    const params = paramsOf(calls);
    // bsk fill clears by default; `clear_before: false` is what makes it append.
    expect(params.clear_before).toBe(false);
    expect(result.matches_n).toBe(1);
  });

  it('pressKey issues tool.press with the raw key', async () => {
    const { handle, calls } = makeHandle({ 'tool.press': { tab_id: 1, key: 'Enter', code: 'Enter' } });
    await new ReactorPage(handle).pressKey('Enter');

    expect(calls[0].method).toBe('tool.press');
    expect(paramsOf(calls)).toMatchObject({ session_id: 'test-session', key: 'Enter' });
  });

  it('screenshot returns a base64 string, not the bsk envelope', async () => {
    const { handle, calls } = makeHandle({
      'tool.screenshot': { image_base64: 'aGk=', width: 10, height: 20, format: 'png', tab_id: 1 },
    });
    const result = await new ReactorPage(handle).screenshot();

    expect(calls[0].method).toBe('tool.screenshot');
    expect(typeof result).toBe('string');
    expect(result).toBe('aGk=');
  });
});

describe('ReactorPage waits stay client-side', () => {
  it('wait(0.5) sends no RPC and completes after roughly 500ms', async () => {
    const { handle, calls } = makeHandle();
    const started = Date.now();
    await new ReactorPage(handle).wait(0.5);
    const elapsed = Date.now() - started;

    // `tool.wait_ms` is daemon-only; the extension answers unknown_method.
    expect(calls).toEqual([]);
    expect(elapsed).toBeGreaterThanOrEqual(450);
  });

  it('sleep(0.2) sends no RPC', async () => {
    const { handle, calls } = makeHandle();
    await new ReactorPage(handle).sleep(0.2);
    expect(calls).toEqual([]);
  });

  it('wait({text}) polls through tool.evaluate only', async () => {
    const { handle, calls } = makeHandle({
      'tool.evaluate': { ok: true, tab_id: 1, value: true },
    });
    await new ReactorPage(handle).wait({ text: 'Ready' });

    expect(calls.length).toBeGreaterThan(0);
    expect(calls.every((call) => call.method === 'tool.evaluate')).toBe(true);
    const expression = String((calls[0].params as Record<string, unknown>).expression);
    expect(expression).toContain('Ready');
    expect(expression).toContain('innerText');
  });

  it('wait({selector}) stops polling once the selector appears', async () => {
    let attempts = 0;
    const calls: RecordedCall[] = [];
    const handle: BskSessionHandle = {
      sessionId: 'test-session',
      call: async <T>(method: string, params: unknown): Promise<T> => {
        calls.push({ method, params });
        attempts += 1;
        return { ok: true, tab_id: 1, value: attempts >= 3, error: undefined } as unknown as T;
      },
    };
    await new ReactorPage(handle).wait({ selector: '#ready', timeout: 5 });

    expect(attempts).toBe(3);
    expect(calls[0].method).toBe('tool.evaluate');
    expect(String((calls[0].params as Record<string, unknown>).expression)).toContain('#ready');
  });
});

describe('ReactorPage tab and diagnostic methods', () => {
  it('tabs issues tool.tab_list and returns the tab array', async () => {
    const { handle, calls } = makeHandle({
      'tool.tab_list': { tabs: [{ tab_id: 1, active: true, url: 'https://example.com' }] },
    });
    const tabs = await new ReactorPage(handle).tabs();

    expect(calls[0].method).toBe('tool.tab_list');
    expect(Array.isArray(tabs)).toBe(true);
    expect(tabs).toHaveLength(1);
  });

  it('newTab issues tool.tab_create and returns a string tab id', async () => {
    const { handle, calls } = makeHandle({
      'tool.tab_create': { tab_id: 7, window_id: 1, url: 'https://example.com' },
    });
    const id = await new ReactorPage(handle).newTab('https://example.com');

    expect(calls[0].method).toBe('tool.tab_create');
    expect(paramsOf(calls)).toMatchObject({ url: 'https://example.com' });
    expect(typeof id).toBe('string');
    expect(id).toBe('7');
  });

  it('selectTab issues tool.tab_select with a numeric tab id', async () => {
    const { handle, calls } = makeHandle({ 'tool.tab_select': { tab_id: 3, window_id: 1 } });
    await new ReactorPage(handle).selectTab(3);

    expect(calls[0].method).toBe('tool.tab_select');
    expect(paramsOf(calls)).toMatchObject({ tab_id: 3 });
  });

  it('closeTab resolves the active tab before issuing tool.tab_close', async () => {
    const { handle, calls } = makeHandle({
      'tool.tab_list': { tabs: [{ tab_id: 4, active: true }] },
      'tool.tab_close': { tab_id: 4 },
    });
    await new ReactorPage(handle).closeTab();

    expect(calls.map((c) => c.method)).toEqual(['tool.tab_list', 'tool.tab_close']);
    expect(calls[1].params).toMatchObject({ tab_id: 4 });
  });

  it('networkRequests issues tool.network and filters static entries', async () => {
    const { handle, calls } = makeHandle({
      'tool.network': {
        tab_id: 1,
        next_since: 0,
        truncated: false,
        entries: [
          { sequence: 1, kind: 'response', resource_type: 'xhr', truncated: false },
          { sequence: 2, kind: 'response', resource_type: 'script', truncated: false },
        ],
      },
    });
    const entries = await new ReactorPage(handle).networkRequests(false);

    expect(calls[0].method).toBe('tool.network');
    expect(entries).toHaveLength(1);
  });

  it('consoleMessages issues tool.console', async () => {
    const { handle, calls } = makeHandle({
      'tool.console': { tab_id: 1, entries: [{ sequence: 1, level: 'error', text: 'x', truncated: false }], next_since: 0, truncated: false },
    });
    const messages = await new ReactorPage(handle).consoleMessages('error');

    expect(calls[0].method).toBe('tool.console');
    expect(messages).toHaveLength(1);
  });

  it('scroll issues tool.wheel with a direction delta', async () => {
    const { handle, calls } = makeHandle({ 'tool.wheel': { tab_id: 1, delta_x: 0, delta_y: 100 } });
    await new ReactorPage(handle).scroll('down', 100);

    expect(calls[0].method).toBe('tool.wheel');
    expect(paramsOf(calls)).toMatchObject({ delta_y: 100 });
  });

  it('scrollTo issues tool.scroll_to and preserves the resolution envelope', async () => {
    const { handle, calls } = makeHandle({
      'tool.scroll_to': { tab_id: 1, used_ref: '@e1', x: 0, y: 10, width: 1, height: 2 },
    });
    const result = await new ReactorPage(handle).scrollTo('@e1');

    expect(calls[0].method).toBe('tool.scroll_to');
    expect(result).toMatchObject({ y: 10, matches_n: 1, match_level: 'exact' });
  });

  it('hover, focus and dblClick map to their bsk commands', async () => {
    const { handle, calls } = makeHandle({
      'tool.hover': { tab_id: 1, used_ref: '@e1', x: 1, y: 2 },
      'tool.focus': { tab_id: 1, used_ref: '@e1', focused: true },
      'tool.click': { tab_id: 1, used_ref: '@e1', x: 1, y: 2 },
    });
    const page = new ReactorPage(handle);
    const hover = await page.hover('@e1');
    const focus = await page.focus('@e1');
    const dblClick = await page.dblClick('@e1');

    expect(calls.map((c) => c.method)).toEqual(['tool.hover', 'tool.focus', 'tool.click']);
    expect((calls[2].params as Record<string, unknown>).click_count).toBe(2);
    expect(hover.matches_n).toBe(1);
    expect(focus.focused).toBe(true);
    expect(dblClick.match_level).toBe('exact');
  });

  it('getCurrentUrl reads location.href through evaluate', async () => {
    const { handle, calls } = makeHandle({
      'tool.evaluate': { ok: true, tab_id: 1, value: 'https://example.com/now' },
    });
    const url = await new ReactorPage(handle).getCurrentUrl();

    expect(calls[0].method).toBe('tool.evaluate');
    expect(url).toBe('https://example.com/now');
  });

  it('fetchJson performs an in-page fetch with page credentials', async () => {
    const { handle, calls } = makeHandle({
      'tool.evaluate': { ok: true, tab_id: 1, value: { ok: true, status: 200, statusText: 'OK', text: '{"a":1}', url: 'https://example.com/api' } },
    });
    const data = await new ReactorPage(handle).fetchJson('https://example.com/api');

    expect(calls[0].method).toBe('tool.evaluate');
    expect(String((calls[0].params as Record<string, unknown>).expression)).toContain("credentials: 'include'");
    expect(data).toEqual({ a: 1 });
  });

  it('getCookies maps the bsk cookie wire shape onto BrowserCookie', async () => {
    const { handle, calls } = makeHandle({
      'tool.cookies': {
        tab_id: 1,
        scope: 'example.test',
        cookies: [
          {
            name: 'sid',
            value: 'abc',
            domain: '.example.test',
            path: '/',
            secure: true,
            http_only: true,
            expiration_date: 1_700_000_000,
          },
          // Session cookies carry no expiration_date; it must stay absent
          // rather than become `undefined` on the wire shape.
          { name: 'temp', value: '1', domain: '.example.test', path: '/', secure: false, http_only: false },
        ],
      },
    });

    const cookies = await new ReactorPage(handle).getCookies({ domain: 'example.test' });

    expect(calls[0].method).toBe('tool.cookies');
    expect(calls[0].params).toMatchObject({ domain: 'example.test' });
    expect(cookies).toEqual([
      {
        name: 'sid',
        value: 'abc',
        domain: '.example.test',
        path: '/',
        secure: true,
        httpOnly: true,
        expirationDate: 1_700_000_000,
      },
      {
        name: 'temp',
        value: '1',
        domain: '.example.test',
        path: '/',
        secure: false,
        httpOnly: false,
      },
    ]);
  });

  it('getCookies rejects a malformed result instead of returning an empty list', async () => {
    const { handle } = makeHandle({ 'tool.cookies': { tab_id: 1 } });
    await expect(new ReactorPage(handle).getCookies()).rejects.toThrow(/invalid result/);
  });
});

const NOT_IMPLEMENTED = [
  'uploadFiles', 'setFileInput', 'waitForDownload', 'setChecked',
  'drag', 'getFormState', 'autoScroll', 'installInterceptor', 'getInterceptedRequests',
  'waitForCapture', 'annotatedScreenshot', 'startNetworkCapture', 'readNetworkCapture',
  'insertText', 'closeWindow', 'cdp', 'handleJavaScriptDialog', 'frames',
  'evaluateInFrame', 'nativeClick', 'nativeType', 'nativeKeyPress',
] as const;

describe('ReactorPage unimplemented surface', () => {
  it.each(NOT_IMPLEMENTED)('%s throws NotImplementedError naming the method', async (method) => {
    const { handle, calls } = makeHandle();
    const page = new ReactorPage(handle) as unknown as Record<string, (...args: unknown[]) => Promise<unknown>>;

    await expect(page[method]()).rejects.toBeInstanceOf(NotImplementedError);
    await expect(page[method]()).rejects.toThrow(new RegExp(`IPage\\.${method}`));
    // A stub must never issue a doomed RPC.
    expect(calls).toEqual([]);
  });

  it.each([
    ['getActivePage'],
    ['setActivePage'],
  ] as const)('%s throws NotImplementedError synchronously', (method) => {
    const { handle } = makeHandle();
    const page = new ReactorPage(handle) as unknown as Record<string, () => unknown>;
    expect(() => page[method]()).toThrow(new RegExp(`IPage\\.${method}`));
  });

  it('never resolves an unimplemented method to an empty value', async () => {
    const { handle } = makeHandle();
    const page = new ReactorPage(handle) as unknown as Record<string, () => Promise<unknown>>;
    for (const method of NOT_IMPLEMENTED) {
      await expect(page[method]()).rejects.toBeInstanceOf(Error);
    }
  });
});
