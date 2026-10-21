import { describe, expect, it, vi } from 'vitest';
import { OpenCliPage } from './opencli-page.js';
import { NotImplementedError, ReactorPage, type BskSessionHandle } from './reactor-page.js';
import type { Page } from '../browser/page.js';
import { getBrowserFactory } from '../runtime.js';

describe('getBrowserFactory with BROWSER_BACKEND', () => {
  it('does not change the default path', () => {
    const previous = process.env.BROWSER_BACKEND;
    delete process.env.BROWSER_BACKEND;
    try {
      expect(getBrowserFactory('xianyu').name).toBe('BrowserBridge');
    } finally {
      if (previous !== undefined) process.env.BROWSER_BACKEND = previous;
    }
  });

  it('selects BskBrowserFactory when BROWSER_BACKEND=bsk', () => {
    const previous = process.env.BROWSER_BACKEND;
    process.env.BROWSER_BACKEND = 'bsk';
    try {
      expect(getBrowserFactory('xianyu').name).toBe('BskBrowserFactory');
    } finally {
      if (previous === undefined) delete process.env.BROWSER_BACKEND;
      else process.env.BROWSER_BACKEND = previous;
    }
  });
});

describe('OpenCliPage', () => {
  function makeFakeInner() {
    const calls: Array<{ method: string; args: unknown[] }> = [];
    const record = (method: string) => (...args: unknown[]) => {
      calls.push({ method, args });
      return Promise.resolve(`${method}:ok`);
    };
    const inner = {
      goto: record('goto'),
      evaluate: record('evaluate'),
      evaluateWithArgs: record('evaluateWithArgs'),
      fetchJson: record('fetchJson'),
      getCookies: record('getCookies'),
      snapshot: record('snapshot'),
      click: record('click'),
      dblClick: record('dblClick'),
      hover: record('hover'),
      focus: record('focus'),
      setChecked: record('setChecked'),
      uploadFiles: record('uploadFiles'),
      drag: record('drag'),
      typeText: record('typeText'),
      fillText: record('fillText'),
      pressKey: record('pressKey'),
      scrollTo: record('scrollTo'),
      getFormState: record('getFormState'),
      wait: record('wait'),
      sleep: record('sleep'),
      waitForDownload: record('waitForDownload'),
      tabs: record('tabs'),
      closeTab: record('closeTab'),
      newTab: record('newTab'),
      selectTab: record('selectTab'),
      networkRequests: record('networkRequests'),
      consoleMessages: record('consoleMessages'),
      scroll: record('scroll'),
      autoScroll: record('autoScroll'),
      installInterceptor: record('installInterceptor'),
      getInterceptedRequests: record('getInterceptedRequests'),
      waitForCapture: record('waitForCapture'),
      screenshot: record('screenshot'),
      annotatedScreenshot: record('annotatedScreenshot'),
      startNetworkCapture: record('startNetworkCapture'),
      readNetworkCapture: record('readNetworkCapture'),
      setFileInput: record('setFileInput'),
      insertText: record('insertText'),
      closeWindow: record('closeWindow'),
      getCurrentUrl: record('getCurrentUrl'),
      getActivePage: record('getActivePage'),
      setActivePage: record('setActivePage'),
      cdp: record('cdp'),
      handleJavaScriptDialog: record('handleJavaScriptDialog'),
      frames: record('frames'),
      evaluateInFrame: record('evaluateInFrame'),
      nativeClick: record('nativeClick'),
      nativeType: record('nativeType'),
      nativeKeyPress: record('nativeKeyPress'),
    };
    return { inner, calls };
  }

  it('forwards required methods with their arguments', async () => {
    const { inner, calls } = makeFakeInner();
    const page = new OpenCliPage(inner as unknown as Page);

    await page.goto('https://example.com', { waitUntil: 'none' });
    await page.evaluate('1 + 1');
    await page.fetchJson('https://example.com/api', { method: 'POST' });
    await page.getCookies({ domain: 'example.com' });
    await page.snapshot({ interactive: true });
    await page.click('e1', { nth: 0 });
    await page.typeText('e2', 'hi');
    await page.fillText('e3', 'text');
    await page.pressKey('Enter');
    await page.scrollTo('e4');
    await page.getFormState();
    await page.wait(1);
    await page.sleep(0.1);
    await page.tabs();
    await page.selectTab(0);
    await page.networkRequests(true);
    await page.consoleMessages('warn');
    await page.scroll('down', 100);
    await page.autoScroll({ times: 1, delayMs: 10 });
    await page.installInterceptor('/api');
    await page.getInterceptedRequests();
    await page.waitForCapture(1);
    await page.screenshot({ format: 'png' });

    expect(calls.map((c) => c.method)).toEqual([
      'goto', 'evaluate', 'fetchJson', 'getCookies', 'snapshot', 'click',
      'typeText', 'fillText', 'pressKey', 'scrollTo', 'getFormState', 'wait',
      'sleep', 'tabs', 'selectTab', 'networkRequests', 'consoleMessages',
      'scroll', 'autoScroll', 'installInterceptor', 'getInterceptedRequests',
      'waitForCapture', 'screenshot',
    ]);
    expect(calls[0].args).toEqual(['https://example.com', { waitUntil: 'none' }]);
    expect(calls[1].args).toEqual(['1 + 1']);
    expect(calls[5].args).toEqual(['e1', { nth: 0 }]);
  });

  it('forwards optional methods and preserves undefined when the inner lacks them', async () => {
    const { inner, calls } = makeFakeInner();
    const page = new OpenCliPage(inner as unknown as Page);

    expect(page.dblClick).toBeTypeOf('function');
    expect(page.nativeKeyPress).toBeTypeOf('function');
    await page.dblClick?.('e1');
    await page.nativeKeyPress?.('Enter', ['Shift']);

    expect(calls).toEqual([
      { method: 'dblClick', args: ['e1'] },
      { method: 'nativeKeyPress', args: ['Enter', ['Shift']] },
    ]);

    const bare = new OpenCliPage({} as unknown as Page);
    expect(bare.dblClick).toBeUndefined();
    expect(bare.cdp).toBeUndefined();
    expect(bare.annotatedScreenshot).toBeUndefined();
  });
});

/**
 * Methods ReactorPage still refuses to fake (no bsk RPC, or remote-session
 * rejection). Everything else is covered by reactor-page.test.ts.
 */
// evaluateWithArgs IS implemented (safe-arg evaluate, see reactor-page.ts).
const NOT_IMPLEMENTED_METHODS = [
  'setChecked', 'uploadFiles', 'drag',
  'getFormState', 'waitForDownload', 'autoScroll', 'installInterceptor',
  'getInterceptedRequests', 'waitForCapture', 'annotatedScreenshot',
  'startNetworkCapture', 'readNetworkCapture', 'setFileInput', 'insertText',
  'closeWindow', 'getActivePage', 'setActivePage', 'cdp',
  'handleJavaScriptDialog', 'frames', 'evaluateInFrame', 'nativeClick',
  'nativeType', 'nativeKeyPress',
] as const;

describe('ReactorPage', () => {
  const handle: BskSessionHandle = {
    sessionId: 'test-session',
    call: vi.fn(),
  };

  it('exposes the session id', () => {
    expect(new ReactorPage(handle).handle.sessionId).toBe('test-session');
  });

  it.each(NOT_IMPLEMENTED_METHODS)('throws NotImplementedError from %s', async (method) => {
    const page = new ReactorPage(handle) as unknown as Record<string, (...args: unknown[]) => unknown>;

    // Sync methods (getActivePage / setActivePage) throw before returning;
    // async methods reject. Normalise both into one captured error.
    let captured: unknown;
    try {
      const result = page[method]();
      if (result instanceof Promise) {
        await result.catch((err: unknown) => { captured = err; });
      }
    } catch (err) {
      captured = err;
    }

    expect(captured).toBeInstanceOf(NotImplementedError);
    const error = captured as NotImplementedError;
    expect(error.method).toBe(method);
    expect(error.message).toContain(`IPage.${method}`);
    expect(error.message).toMatch(/Suggested alternative: /);
  });

  it('rejects async methods rather than resolving to empty values', async () => {
    const page = new ReactorPage(handle);
    await expect(page.setFileInput(['/tmp/a.png'])).rejects.toBeInstanceOf(NotImplementedError);
    // Explicitly assert no silent empty-array / false return path.
    await expect(page.getInterceptedRequests()).rejects.not.toBeUndefined();
  });

  it('names the bsk alternative in the error message', async () => {
    const page = new ReactorPage(handle);
    await expect(page.installInterceptor('/api')).rejects.toThrow(/tool\.network/);
  });
});
